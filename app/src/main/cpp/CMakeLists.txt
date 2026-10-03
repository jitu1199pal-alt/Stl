cmake_minimum_required(VERSION 3.22.1)
project("cadconverter")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -fvisibility=hidden -fdata-sections -ffunction-sections")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections")

set(LIBDXFRW_DIR ${CMAKE_CURRENT_SOURCE_DIR}/libdxfrw)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${LIBDXFRW_DIR}
    ${LIBDXFRW_DIR}/intern
)

# Glob any libdxfrw C++ source files if present
file(GLOB_RECURSE LIBDXFRW_SOURCES
    "${LIBDXFRW_DIR}/*.cpp"
)

add_library(cadconverter SHARED
    cad_bridge.cpp
    CadModelReader.cpp
    ${LIBDXFRW_SOURCES}
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(cadconverter
    ${log-lib}
    ${android-lib}
)
