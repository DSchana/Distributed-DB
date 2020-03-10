# C++ Implementation

## Requirements
 * cmake >= 3.0
 * c++2a
    * GCC >= 9.0
    * Clang >= 9.0
 * pkg-config >= 1.6.3
 * Google Test
    * libgtest-dev (for debian based kernel)

## build
Run `configure.sh` to fetch git submodules and configure cmake config files. It may not complete the make all the way, that's fine, thie is just needed to generate `RapidJSONConfig.cmake`.

Run `build.sh` to compile DDB into executable.
