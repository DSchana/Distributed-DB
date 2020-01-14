cmake_minimum_required(VERSION 3.0)
project(DistributedDB)

set(CMAKE_CXX_STANDARD 20)

file(GLOB CXX_EXEC "src/*.cpp")

add_executable(DistributedDB ${CXX_EXEC})
target_include_directories(DistributedDB PUBLIC inc/)