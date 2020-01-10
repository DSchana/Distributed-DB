cmake_minimum_required(VERSION 3.0)
project(DistributedDB)

set(CMAKE_CXX_STANDARD 20)

include_directories(inc/)
file(GLOB CXX_EXEC "src/*.cpp")

add_executable(DistributedDB ${CXX_EXEC})