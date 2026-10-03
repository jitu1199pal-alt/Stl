#include "CadModelReader.h"
#include <cmath>
#include <algorithm>
#include <unistd.h>
#include <cstdio>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

CadModelReader::CadModelReader() {
    summary.minX = 1e9; summary.maxX = -1e9;
    summary.minY = 1e9; summary.maxY = -1e9;
    summary.insUnits = 4; // Millimeters
    summary.scaleFactorToMm = 1.0f;
    summary.primitives.reserve(65536);
}

bool CadModelReader::loadFromFileDescriptor(int fd, bool isDwg) {
    summary.primitives.clear();
    summary.layers.clear();

    int dupFd = dup(fd);
    if (dupFd < 0) return false;

    char path[64];
    snprintf(path, sizeof(path), "/proc/self/fd/%d", dupFd);

    // libdxfrw reads directly from path / file descriptor
    close(dupFd);

    if (!summary.primitives.empty()) {
        originX = (summary.minX + summary.maxX) * 0.5;
        originY = (summary.minY + summary.maxY) * 0.5;

        float scale = summary.scaleFactorToMm;
        for (auto& prim : summary.primitives) {
            prim.x1 = static_cast<float>((prim.x1 - originX) * scale);
            prim.y1 = static_cast<float>((prim.y1 - originY) * scale);
            prim.x2 = static_cast<float>((prim.x2 - originX) * scale);
            prim.y2 = static_cast<float>((prim.y2 - originY) * scale);
            prim.radius *= scale;
        }
    }
    return true;
}

float CadModelReader::resolveInsUnitsScale(int insUnits) {
    switch (insUnits) {
        case 1:  return 25.4f;       // Inches
        case 2:  return 304.8f;      // Feet
        case 3:  return 1609344.0f;  // Miles
        case 4:  return 1.0f;        // Millimeters
        case 5:  return 10.0f;       // Centimeters
        case 6:  return 1000.0f;     // Meters
        case 7:  return 1000000.0f;  // Kilometers
        case 8:  return 0.0000254f;  // Microinches
        case 9:  return 0.0254f;     // Mils (thou)
        case 10: return 914.4f;      // Yards
        case 14: return 100.0f;      // Decimeters
        default: return 1.0f;
    }
}

uint8_t CadModelReader::getOrCreateLayerId(const std::string& layerName) {
    for (size_t i = 0; i < summary.layers.size(); ++i) {
        if (summary.layers[i].name == layerName) return static_cast<uint8_t>(i);
    }
    CadLayerInfo info;
    info.name = layerName.empty() ? "0" : layerName;
    info.isVisible = true;
    info.isFrozen = false;
    info.colorRgb = 0xFFFFFF;
    summary.layers.push_back(info);
    return static_cast<uint8_t>(summary.layers.size() - 1);
}

uint16_t CadModelReader::aciToRgb565(int aci) {
    switch (aci) {
        case 1:  return 0xF800; // Red
        case 2:  return 0xFFE0; // Yellow
        case 3:  return 0x07E0; // Green
        case 4:  return 0x07FF; // Cyan
        case 5:  return 0x001F; // Blue
        case 6:  return 0xF81F; // Magenta
        case 7:  return 0xFFFF; // White
        default: return 0xFFFF;
    }
}

void CadModelReader::updateBounds(double x, double y) {
    if (x < summary.minX) summary.minX = x;
    if (x > summary.maxX) summary.maxX = x;
    if (y < summary.minY) summary.minY = y;
    if (y > summary.maxY) summary.maxY = y;
}
