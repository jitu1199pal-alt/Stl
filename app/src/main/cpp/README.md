# 100% Offline AutoCAD DWG-to-DXF Native C++ Engine (`libcadconverter`)

This module provides a 100% offline CAD conversion pipeline converting binary DWG files into text-based DXF files directly on the Android device without any cloud server or internet connection.

## Architecture

1. **`CMakeLists.txt`**:
   - Configures CMake to compile the native C++ sources and `libdxfrw` into `libcadconverter.so`.
   - Automatically globs any `libdxfrw` C++ source files when present.

2. **C++ JNI Wrapper (`cad-bridge.cpp` / `cad_bridge.cpp`)**:
   - Implements native function `Java_com_example_app_CadConverter_convertDwgToDxf(JNIEnv* env, jobject obj, jstring dwgPath, jstring dxfPath)`.
   - Bridges `dwg2dxf` translation using `libdxfrw`'s `DRW_Interface` (`dwgR` and `dxfRW`).
   - Includes robust `try-catch` exception handling preventing app crashes on corrupted or unsupported CAD drawings.

3. **Kotlin Singleton (`CadConverter.kt`)**:
   - Resides in `com.example.app` (with forwarder in `com.example.cad`).
   - Dynamically loads `System.loadLibrary("cadconverter")` with fallback support.
   - Provides `suspend fun convertDwgToDxf(dwgPath: String, context: Context?): String` running in a background coroutine thread (`Dispatchers.IO`).
   - Generates temporary output DXF files, writes the converted output, and returns the DXF content as a String.

## Android Studio NDK Setup & GitHub Sync

To compile the native C++ library with Android Studio:
1. Open Android Studio -> **Tools** -> **SDK Manager** -> **SDK Tools**.
2. Check **NDK (Side by side)** and **CMake**, then click **Apply**.
3. Clone `libdxfrw` into `app/src/main/cpp/libdxfrw`:
   ```bash
   git clone https://github.com/LibreCAD/libdxfrw.git app/src/main/cpp/libdxfrw
   ```
4. In `app/build.gradle.kts`, enable `externalNativeBuild`:
   ```kotlin
   android {
       externalNativeBuild {
           cmake {
               path = file("src/main/cpp/CMakeLists.txt")
           }
       }
   }
   ```
5. Build and run: Android Studio compiles `libcadconverter.so` and packages it into the APK.
