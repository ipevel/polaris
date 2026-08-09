cmake_minimum_required(VERSION 3.22.1)

project(clash-bridge C)

set(CMAKE_POSITION_INDEPENDENT_CODE on)
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O3")

# mihomo 头文件（go build -buildmode=c-shared 生成）与 Go native 目录
include_directories(${CMAKE_CURRENT_SOURCE_DIR})
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../golang/native)

# 预编译的 libclash.so（Go 手动编译，见 kernel-core/src/main/jniLibs）
link_directories(${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

add_library(bridge SHARED main.c jni_helper.c bridge_helper.c)
target_link_libraries(bridge log clash)
