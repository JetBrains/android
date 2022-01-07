# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

cmake_minimum_required(VERSION 3.18.1)

project("screensharing")

set(${CMAKE_C_FLAGS}, "${CMAKE_C_FLAGS}")

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -Wall")

add_library(
        screen-sharing-agent

        SHARED

        accessors/display_info.cc
        accessors/display_manager.cc
        accessors/service_manager.cc
        accessors/surface_control.cc
        accessors/window_manager.cc
        agent.cc
        display_streamer.cc
        jvm.cc
        log.cc
        main.cc
        video_packet_header.cc
)

target_include_directories(screen-sharing-agent PRIVATE .)

find_library(log log)
find_library(mediandk mediandk)

target_link_libraries(
        screen-sharing-agent
        -landroid

        ${log}
        ${mediandk}
)