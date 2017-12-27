cmake_minimum_required(VERSION 3.4.1)

add_library(hello-jni SHARED
            hello-jni.c
            optimized.c)

set_source_files_properties(optimized.c PROPERTIES COMPILE_FLAGS -O1)

target_link_libraries(hello-jni
                      android
                      log)
