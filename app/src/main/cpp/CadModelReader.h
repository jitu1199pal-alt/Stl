#pragma once

#include <vector>
#include <string>
#include <cstdint>

enum EntityType : uint8_t {
    TYPE_LINE = 1,
    TYPE_ARC = 2,
    TYPE_CIRCLE = 3,
    TYPE_SPLINE_SEGMENT = 4,
    TYPE_TEXT = 5,
    TYPE_DIMENSION_LINE = 6
};

#pragma pack(push, 1)
struct RenderPrimitive {
    uint8_t type;         // EntityType
    uint8_t layerId;      // Mapped layer index
    uint16_t colorRgb565; // ACI converted to RGB565
    float x1, y1;         // Start / Center / Text Origin
    float x2, y2;         // End point or Bounding parameters
    float radius;         // For Circle / Arc / Text Height
    float startAngle;     // For Arc
    float sweepAngle;     // For Arc
    float rotation;       // For Text / Block orientation
};
#pragma pack(pop)

struct CadLayerInfo {
    std::string name;
    uint32_t colorRgb;
    bool isVisible;
    bool isFrozen;
};

struct CadModelSummary {
    double minX, maxX;
    double minY, maxY;
    int32_t insUnits;
    float scaleFactorToMm;
    std::vector<CadLayerInfo> layers;
    std::vector<RenderPrimitive> primitives;
};

class CadModelReader {
public:
    CadModelReader();
    virtual ~CadModelReader() = default;

    bool loadFromFileDescriptor(int fd, bool isDwg);

    CadModelSummary& getSummary() { return summary; }

private:
    CadModelSummary summary;
    double originX = 0.0;
    double originY = 0.0;

    uint8_t getOrCreateLayerId(const std::string& layerName);
    uint16_t aciToRgb565(int aci);
    float resolveInsUnitsScale(int insUnits);
    void updateBounds(double x, double y);
};
