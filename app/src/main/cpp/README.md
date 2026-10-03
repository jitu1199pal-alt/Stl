# Native C++ DWG & DXF Engine (`libdxfrw`)

This folder contains the complete native C++ and JNI implementation for reading binary AutoCAD DWG and text DXF files directly on Android using the NDK.

## Architecture
- `CMakeLists.txt`: CMake build script targeting `arm64-v8a`, `armeabi-v7a`, and `x86_64`.
- `CadModelReader.h` & `CadModelReader.cpp`: Geometry extraction engine implementing $INSUNITS scaling, origin-rebasing, and flat primitive streaming.
- `cad_bridge.cpp`: JNI interface streaming flat memory buffers directly into Android Native `SurfaceView`.

## To Build with NDK in Android Studio
1. Clone `libdxfrw` into `app/src/main/cpp/libdxfrw`:
   ```bash
   git clone https://github.com/LibreCAD/libdxfrw.git app/src/main/cpp/libdxfrw
   ```
2. Enable `externalNativeBuild` in `app/build.gradle.kts`:
   ```kotlin
   android {
       externalNativeBuild {
           cmake {
               path = file("src/main/cpp/CMakeLists.txt")
           }
       }
   }
   ```
