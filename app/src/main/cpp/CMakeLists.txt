cmake_minimum_required(VERSION 3.22.1)
project("cad_native_engine")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -fvisibility=hidden -fdata-sections -ffunction-sections")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections")

set(LIBDXFRW_DIR ${CMAKE_CURRENT_SOURCE_DIR}/libdxfrw)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${LIBDXFRW_DIR}
)

set(LIBDXFRW_SOURCES
    ${LIBDXFRW_DIR}/drw_entities.cpp
    ${LIBDXFRW_DIR}/drw_header.cpp
    ${LIBDXFRW_DIR}/drw_objects.cpp
    ${LIBDXFRW_DIR}/libdwgr.cpp
    ${LIBDXFRW_DIR}/libdxfrw.cpp
    ${LIBDXFRW_DIR}/intern/drw_dbg.cpp
    ${LIBDXFRW_DIR}/intern/drw_textcodec.cpp
    ${LIBDXFRW_DIR}/intern/dwgbuffer.cpp
    ${LIBDXFRW_DIR}/intern/dwgreader.cpp
    ${LIBDXFRW_DIR}/intern/dwgreader15.cpp
    ${LIBDXFRW_DIR}/intern/dwgreader18.cpp
    ${LIBDXFRW_DIR}/intern/dwgreader21.cpp
    ${LIBDXFRW_DIR}/intern/dwgreader24.cpp
    ${LIBDXFRW_DIR}/intern/dwgreader27.cpp
    ${LIBDXFRW_DIR}/intern/dxfreader.cpp
)

add_library(cad_native_engine SHARED
    cad_bridge.cpp
    CadModelReader.cpp
    ${LIBDXFRW_SOURCES}
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(cad_native_engine
    ${log-lib}
    ${android-lib}
)
