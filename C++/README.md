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
Run `git submodule init` and `git submodule update` to make avaliable the RapidJson library for building.

Use `cmake` to generate make files from C++/CMakeLists.txt. Use `make all` from the same directory as the `cmake` call to generate `DistributedDB` executable file.

ex. \
From Distributed-DB/C++
```
mkdir build
cd build
cmake ..
make all
```
