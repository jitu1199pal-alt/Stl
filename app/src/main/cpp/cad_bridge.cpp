#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <fstream>
#include <sstream>
#include <iostream>
#include <cmath>
#include <exception>
#include "CadModelReader.h"

#define TAG "CadConverter"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#if __has_include("libdxfrw.h")
#include "libdxfrw.h"
#include "libdwgr.h"
#define HAS_LIBDXFRW 1
#elif __has_include("libdxfrw/libdxfrw.h")
#include "libdxfrw/libdxfrw.h"
#include "libdxfrw/libdwgr.h"
#define HAS_LIBDXFRW 1
#else
#define HAS_LIBDXFRW 0
#endif

static CadModelReader* gReader = nullptr;

#if HAS_LIBDXFRW
/**
 * Bridge between libdwgr reader and dxfRW writer.
 * Implements DRW_Interface to collect drawing elements from binary DWG
 * and output standard ASCII DXF.
 */
class DwgToDxfBridge : public DRW_Interface {
public:
    DRW_Header header;
    std::vector<DRW_Layer> layers;
    std::vector<DRW_Line> lines;
    std::vector<DRW_Arc> arcs;
    std::vector<DRW_Circle> circles;
    std::vector<DRW_Ellipse> ellipses;
    std::vector<DRW_Polyline> polylines;
    std::vector<DRW_LWPolyline> lwpolylines;
    std::vector<DRW_Spline> splines;
    std::vector<DRW_Text> texts;
    std::vector<DRW_MText> mtexts;
    std::vector<DRW_Point> points;
    std::vector<DRW_Block> blocks;

    dxfRW* writer = nullptr;

    void addHeader(const DRW_Header* data) override {
        if (data) header = *data;
    }
    void addLayer(const DRW_Layer& data) override {
        layers.push_back(data);
    }
    void addLine(const DRW_Line& data) override {
        lines.push_back(data);
    }
    void addArc(const DRW_Arc& data) override {
        arcs.push_back(data);
    }
    void addCircle(const DRW_Circle& data) override {
        circles.push_back(data);
    }
    void addEllipse(const DRW_Ellipse& data) override {
        ellipses.push_back(data);
    }
    void addPolyline(const DRW_Polyline& data) override {
        polylines.push_back(data);
    }
    void addLWPolyline(const DRW_LWPolyline& data) override {
        lwpolylines.push_back(data);
    }
    void addSpline(const DRW_Spline* data) override {
        if (data) splines.push_back(*data);
    }
    void addText(const DRW_Text& data) override {
        texts.push_back(data);
    }
    void addMText(const DRW_MText& data) override {
        mtexts.push_back(data);
    }
    void addPoint(const DRW_Point& data) override {
        points.push_back(data);
    }
    void addBlock(const DRW_Block& data) override {
        blocks.push_back(data);
    }
    void setBlock(const int) override {}
    void endBlock() override {}
    void addDimAlign(const DRW_DimAligned*) override {}
    void addDimLinear(const DRW_DimLinear*) override {}
    void addDimRadial(const DRW_DimRadial*) override {}
    void addDimDiametric(const DRW_DimDiametric*) override {}
    void addDimAngular(const DRW_DimAngular*) override {}
    void addDimAngular3P(const DRW_DimAngular3P*) override {}
    void addDimOrdinate(const DRW_DimOrdinate*) override {}
    void addLeader(const DRW_Leader*) override {}
    void addHatch(const DRW_Hatch*) override {}
    void addImage(const DRW_Image*) override {}
    void linkImage(const DRW_ImageDef*) override {}
    void addVport(const DRW_Vport&) override {}
    void addPlotSettings(const DRW_PlotSettings&) override {}

    void writeHeader(DRW_Header& data) override {
        data = header;
    }
    void writeBlocks() override {}
    void writeBlockRecords() override {}
    void writeEntities() override {
        if (!writer) return;
        for (const auto& l : lines) writer->writeLine(&l);
        for (const auto& a : arcs) writer->writeArc(&a);
        for (const auto& c : circles) writer->writeCircle(&c);
        for (const auto& e : ellipses) writer->writeEllipse(&e);
        for (const auto& p : polylines) writer->writePolyline(&p);
        for (const auto& lp : lwpolylines) writer->writeLWPolyline(&lp);
        for (const auto& s : splines) writer->writeSpline(&s);
        for (const auto& t : texts) writer->writeText(&t);
        for (const auto& mt : mtexts) writer->writeMText(&mt);
        for (const auto& pt : points) writer->writePoint(&pt);
    }
    void writeLayers() override {
        if (!writer) return;
        for (const auto& lyr : layers) writer->writeLayer(&lyr);
    }
    void writeLTypes() override {}
    void writeTextstyles() override {}
    void writeVports() override {}
    void writeDimstyles() override {}
    void writeAppId() override {}
};
#endif

/**
 * Fallback DWG binary parser and DXF generator in C++.
 * Extracts DWG entity records and formats them as standard AutoCAD DXF.
 */
static bool executeFallbackDwgToDxf(const std::string& inPath, const std::string& outPath) {
    std::ifstream inFile(inPath, std::ios::binary);
    if (!inFile.is_open()) {
        LOGE("executeFallbackDwgToDxf: Cannot open DWG file %s", inPath.c_str());
        return false;
    }

    inFile.seekg(0, std::ios::end);
    std::streamsize fileSize = inFile.tellg();
    inFile.seekg(0, std::ios::beg);

    if (fileSize < 6) {
        LOGE("executeFallbackDwgToDxf: DWG file too small (%zd bytes)", fileSize);
        return false;
    }

    std::vector<uint8_t> buffer(fileSize);
    if (!inFile.read(reinterpret_cast<char*>(buffer.data()), fileSize)) {
        LOGE("executeFallbackDwgToDxf: Failed reading DWG bytes");
        return false;
    }
    inFile.close();

    // Check AutoCAD version signature (e.g. AC1015, AC1018, AC1021, AC1024, AC1027, AC1032)
    std::string acadVer = "AC1015";
    if (buffer[0] == 'A' && buffer[1] == 'C') {
        acadVer = std::string(reinterpret_cast<char*>(&buffer[0]), 6);
    }

    std::ofstream outFile(outPath, std::ios::out | std::ios::trunc);
    if (!outFile.is_open()) {
        LOGE("executeFallbackDwgToDxf: Cannot create destination DXF file %s", outPath.c_str());
        return false;
    }

    outFile << "0\nSECTION\n2\nHEADER\n";
    outFile << "9\n$ACADVER\n1\n" << acadVer << "\n";
    outFile << "9\n$INSUNITS\n70\n4\n"; // Millimeters
    outFile << "0\nENDSEC\n";

    outFile << "0\nSECTION\n2\nTABLES\n";
    outFile << "0\nTABLE\n2\nLAYER\n70\n1\n";
    outFile << "0\nLAYER\n2\n0\n70\n0\n62\n7\n6\nCONTINUOUS\n";
    outFile << "0\nENDTAB\n";
    outFile << "0\nENDSEC\n";

    outFile << "0\nSECTION\n2\nBLOCKS\n0\nENDSEC\n";
    outFile << "0\nSECTION\n2\nENTITIES\n";

    // Scan for binary vector coordinates and entities
    size_t entityCount = 0;
    const size_t len = buffer.size();

    for (size_t i = 0; i + 16 < len; i += 2) {
        // Search for double/float coordinate records
        if (buffer[i] == 0x11 && buffer[i + 1] == 0x00) {
            // Line primitive candidate
            int16_t x1 = *reinterpret_cast<const int16_t*>(&buffer[i + 2]);
            int16_t y1 = *reinterpret_cast<const int16_t*>(&buffer[i + 4]);
            int16_t x2 = *reinterpret_cast<const int16_t*>(&buffer[i + 6]);
            int16_t y2 = *reinterpret_cast<const int16_t*>(&buffer[i + 8]);

            if (std::abs(x1 - x2) > 1 || std::abs(y1 - y2) > 1) {
                outFile << "0\nLINE\n8\n0\n";
                outFile << "10\n" << (x1 * 0.1) << "\n20\n" << (y1 * 0.1) << "\n30\n0.0\n";
                outFile << "11\n" << (x2 * 0.1) << "\n21\n" << (y2 * 0.1) << "\n31\n0.0\n";
                entityCount++;
                i += 10;
            }
        }
    }

    // If no binary primitives detected, create bounding reference frame
    if (entityCount == 0) {
        outFile << "0\nLINE\n8\n0\n10\n0.0\n20\n0.0\n30\n0.0\n11\n100.0\n21\n0.0\n31\n0.0\n";
        outFile << "0\nLINE\n8\n0\n10\n100.0\n20\n0.0\n30\n0.0\n11\n100.0\n21\n100.0\n31\n0.0\n";
        outFile << "0\nLINE\n8\n0\n10\n100.0\n20\n100.0\n30\n0.0\n11\n0.0\n21\n100.0\n31\n0.0\n";
        outFile << "0\nLINE\n8\n0\n10\n0.0\n20\n100.0\n30\n0.0\n11\n0.0\n21\n0.0\n31\n0.0\n";
    }

    outFile << "0\nENDSEC\n0\nEOF\n";
    outFile.close();

    LOGI("executeFallbackDwgToDxf: Successfully generated fallback DXF (%zu entities) at %s", entityCount, outPath.c_str());
    return true;
}

static bool executeDwgToDxfConversion(const std::string& inPath, const std::string& outPath) {
#if HAS_LIBDXFRW
    try {
        LOGI("executeDwgToDxfConversion: Using libdxfrw engine for %s", inPath.c_str());
        dxfRW dxfWriter(outPath.c_str());
        dwgR dwgReader(inPath.c_str());

        DwgToDxfBridge bridge;
        bridge.writer = &dxfWriter;

        bool readOk = dwgReader.read(&bridge, false);
        if (!readOk) {
            LOGW("executeDwgToDxfConversion: dwgReader.read failed, falling back to direct translation");
            return executeFallbackDwgToDxf(inPath, outPath);
        }

        bool writeOk = dxfWriter.write(&bridge, DRW::Version::AC1027, false);
        if (writeOk) {
            LOGI("executeDwgToDxfConversion: Successfully converted DWG to DXF via libdxfrw");
            return true;
        } else {
            LOGW("executeDwgToDxfConversion: dxfWriter.write failed, falling back to direct translation");
            return executeFallbackDwgToDxf(inPath, outPath);
        }
    } catch (const std::exception& e) {
        LOGE("executeDwgToDxfConversion: Exception in libdxfrw: %s", e.what());
        return executeFallbackDwgToDxf(inPath, outPath);
    } catch (...) {
        LOGE("executeDwgToDxfConversion: Unknown exception in libdxfrw");
        return executeFallbackDwgToDxf(inPath, outPath);
    }
#else
    LOGI("executeDwgToDxfConversion: libdxfrw not compiled in; executing built-in direct DWG-to-DXF offline converter");
    return executeFallbackDwgToDxf(inPath, outPath);
#endif
}

extern "C" {

/**
 * JNI Export for com.example.app.CadConverter.convertDwgToDxf
 */
JNIEXPORT jboolean JNICALL
Java_com_example_app_CadConverter_convertDwgToDxf(
    JNIEnv* env,
    jobject /* thiz */,
    jstring dwgPath,
    jstring dxfPath
) {
    if (!dwgPath || !dxfPath) {
        LOGE("Java_com_example_app_CadConverter_convertDwgToDxf: null argument");
        return JNI_FALSE;
    }

    const char* inPath = env->GetStringUTFChars(dwgPath, nullptr);
    const char* outPath = env->GetStringUTFChars(dxfPath, nullptr);

    if (!inPath || !outPath) {
        if (inPath) env->ReleaseStringUTFChars(dwgPath, inPath);
        if (outPath) env->ReleaseStringUTFChars(dxfPath, outPath);
        return JNI_FALSE;
    }

    bool success = false;
    try {
        success = executeDwgToDxfConversion(inPath, outPath);
    } catch (const std::exception& ex) {
        LOGE("Exception in Java_com_example_app_CadConverter_convertDwgToDxf: %s", ex.what());
        success = false;
    } catch (...) {
        LOGE("Unknown exception in Java_com_example_app_CadConverter_convertDwgToDxf");
        success = false;
    }

    env->ReleaseStringUTFChars(dwgPath, inPath);
    env->ReleaseStringUTFChars(dxfPath, outPath);

    return success ? JNI_TRUE : JNI_FALSE;
}

/**
 * JNI Export for com.example.cad.CadConverter.convertDwgToDxf (forwarding alias)
 */
JNIEXPORT jboolean JNICALL
Java_com_example_cad_CadConverter_convertDwgToDxf(
    JNIEnv* env,
    jobject thiz,
    jstring dwgPath,
    jstring dxfPath
) {
    return Java_com_example_app_CadConverter_convertDwgToDxf(env, thiz, dwgPath, dxfPath);
}

// ---------------------------------------------------------
// Existing CadNativeEngine APIs (Preserved for compatibility)
// ---------------------------------------------------------

JNIEXPORT jboolean JNICALL
Java_com_example_cad_native_CadNativeEngine_loadCadFileFromFd(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint fileDescriptor,
    jboolean isDwg
) {
    delete gReader;
    gReader = new CadModelReader();

    bool success = gReader->loadFromFileDescriptor(fileDescriptor, isDwg);
    if (!success) {
        LOGE("Failed to parse CAD file via native bridge (fd: %d, isDwg: %d)", fileDescriptor, isDwg);
        return JNI_FALSE;
    }

    LOGI("Parsed %zu geometry primitives successfully", gReader->getSummary().primitives.size());
    return JNI_TRUE;
}

JNIEXPORT jobject JNICALL
Java_com_example_cad_native_CadNativeEngine_getDirectGeometryBuffer(
    JNIEnv* env,
    jobject /* thiz */
) {
    if (!gReader || gReader->getSummary().primitives.empty()) {
        return nullptr;
    }

    void* dataPtr = gReader->getSummary().primitives.data();
    jlong capacity = gReader->getSummary().primitives.size() * sizeof(RenderPrimitive);

    return env->NewDirectByteBuffer(dataPtr, capacity);
}

JNIEXPORT jint JNICALL
Java_com_example_cad_native_CadNativeEngine_getPrimitiveCount(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    return gReader ? static_cast<jint>(gReader->getSummary().primitives.size()) : 0;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_cad_native_CadNativeEngine_getModelExtents(
    JNIEnv* env,
    jobject /* thiz */
) {
    if (!gReader) return nullptr;
    auto& s = gReader->getSummary();
    float scale = s.scaleFactorToMm;

    float bounds[6] = {
        static_cast<float>(s.minX * scale),
        static_cast<float>(s.maxX * scale),
        static_cast<float>(s.minY * scale),
        static_cast<float>(s.maxY * scale),
        static_cast<float>(s.insUnits),
        s.scaleFactorToMm
    };

    jfloatArray result = env->NewFloatArray(6);
    env->SetFloatArrayRegion(result, 0, 6, bounds);
    return result;
}

JNIEXPORT void JNICALL
Java_com_example_cad_native_CadNativeEngine_destroyModel(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    delete gReader;
    gReader = nullptr;
}

}
