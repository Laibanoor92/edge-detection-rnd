cmake_minimum_required(VERSION 3.10)
project(minimalnativeapp)

set(OpenCV_DIR "${CMAKE_SOURCE_DIR}/opencv/sdk/native/jni")
find_package(OpenCV REQUIRED core imgproc imgcodecs)

add_library(native-lib SHARED native-lib.cpp)

target_include_directories(native-lib
        PRIVATE
        ${OpenCV_INCLUDE_DIRS})

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(native-lib
        ${OpenCV_LIBS}
        ${log-lib}
        ${android-lib})
