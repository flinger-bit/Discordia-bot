cmake_minimum_required(VERSION 3.21)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_OSX_ARCHITECTURES "x86_64")
set(CMAKE_CXX_VISIBILITY_PRESET hidden)

project(XDBotRework VERSION 1.1.0)

add_library(${PROJECT_NAME} SHARED
    src/main.cpp
    src/Global.cpp
    src/Macro.cpp
    src/hacks/TpsBypass.cpp
    src/hacks/Speedhack.cpp
    src/hacks/Noclip.cpp
    src/hacks/FrameStepper.cpp
    src/hacks/Clickbot.cpp
    src/hacks/Other.cpp
    src/ui/BotLayer.cpp
    src/ui/MacroListLayer.cpp
)

if (NOT DEFINED ENV{GEODE_SDK})
    message(FATAL_ERROR
        "Unable to find Geode SDK! Please define GEODE_SDK.\n"
        "  Windows: set GEODE_SDK=C:/path/to/geode/sdk\n"
        "  Mac/Linux: export GEODE_SDK=/path/to/geode/sdk"
    )
else()
    message(STATUS "Found Geode SDK: $ENV{GEODE_SDK}")
endif()

add_subdirectory($ENV{GEODE_SDK} ${CMAKE_CURRENT_BINARY_DIR}/geode)

target_include_directories(${PROJECT_NAME} PRIVATE
    src/
)

setup_geode_mod(${PROJECT_NAME})
