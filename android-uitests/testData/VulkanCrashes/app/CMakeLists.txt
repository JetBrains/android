cmake_minimum_required(VERSION 3.4.1)

# build native_app_glue as a static lib
set(APP_GLUE_DIR ${ANDROID_NDK}/sources/android/native_app_glue)
include_directories(${APP_GLUE_DIR})
add_library( app-glue STATIC ${APP_GLUE_DIR}/android_native_app_glue.c)

# build vulkan app
set(SRC_DIR src/main/jni)
set(WRAPPER_DIR ../../common/vulkan_wrapper)

add_library(vktuts SHARED
            ${SRC_DIR}/VulkanMain.cpp
            ${SRC_DIR}/AndroidMain.cpp
            ${WRAPPER_DIR}/vulkan_wrapper.cpp)

include_directories(${WRAPPER_DIR})

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Werror \
                     -Wno-unused-variable \
                     -DVK_USE_PLATFORM_ANDROID_KHR")

target_link_libraries(vktuts app-glue log android)
