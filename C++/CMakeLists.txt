cmake_minimum_required(VERSION 3.0)
project(DistributedDB)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -pthread")

file(GLOB CXX_EXEC "src/*.cpp")

add_subdirectory(DMap)

add_executable(DistributedDB ${CXX_EXEC})
target_include_directories(DistributedDB PUBLIC inc/)
target_link_libraries(DistributedDB DMap)
