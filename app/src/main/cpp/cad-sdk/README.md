# CAD SDK Integration Guide for CAD View Pro

This directory (`app/src/main/cpp/cad-sdk/`) provides the integration interface for professional commercial and open-source CAD SDK backends.

## 1. Professional Commercial Backend: Open Design Alliance (ODA) Drawings SDK

For commercial distribution requiring complete DWG fidelity across all versions (R12, 2000, 2004, 2007, 2010, 2013, 2018):

1. Obtain a licensed ODA Drawings SDK for Android (arm64-v8a, armeabi-v7a, x86_64) from [https://www.opendesign.com/](https://www.opendesign.com/).
2. Place the ODA C++ headers into:
   ```
   app/src/main/cpp/cad-sdk/include/
       OdaCommon.h
       DbDatabase.h
       DbEntity.h
       ...
   ```
3. Place the precompiled `.so` libraries into:
   ```
   app/src/main/jniLibs/arm64-v8a/
       libTD_Db.so
       libTD_Ge.so
       libTD_Gi.so
       ...
   ```
4. In `app/src/main/cpp/CMakeLists.txt`, enable `ENABLE_ODA_SDK`:
   ```cmake
   set(ENABLE_ODA_SDK ON)
   ```

## 2. Open-Source Backend: LibreDWG / libdxfrw

If targeting an open-source distribution:
- **libdxfrw** (GPLv2+): For reading older DWG versions and complete DXF support:
  ```bash
  git clone https://github.com/LibreCAD/libdxfrw.git app/src/main/cpp/libdxfrw
  ```
- **LibreDWG** (GPLv3+): GNU project C library for DWG decoding.
*Important:* Always verify that GPL licensing aligns with your app's distribution model.

## 3. Zero-Dependency Standalone Fallback Engine (Built-In)

If no third-party CAD SDK binaries are installed:
- CAD View Pro automatically runs its high-performance built-in vector parser and WebGL Three.js renderer.
- Full support for ASCII DXF, binary DXF, DWG vector extraction, and interactive layers/measurement/search.
- App never crashes, ensuring 100% offline usability out-of-the-box.
