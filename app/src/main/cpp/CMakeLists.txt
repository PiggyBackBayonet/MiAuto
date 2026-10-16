cmake_minimum_required(VERSION 3.22.1)

project("headunit")

# Force 16 KB memory page size alignment for Android 15+ compatibility
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

# =========================================================================
# Target 1: usbhelper (Libusb Support)
# =========================================================================

# Define imported precompiled libusb1.0.so library (which is now 16 KB page-aligned)
add_library(usb1.0 SHARED IMPORTED)

set_target_properties(usb1.0 PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libusb1.0.so"
)

# Define our JNI wrapper library
add_library(usbhelper SHARED usbhelper.c)

# Link wrapper with libusb and Android logging framework
target_link_libraries(usbhelper
    usb1.0
    log
)

# =========================================================================
# Target 2: hur_soft_hevc (FFmpeg HEVC Software Decoder Support)
# =========================================================================

add_library(hur_soft_hevc SHARED ffmpeg_hevc_decoder.cpp)

target_compile_features(hur_soft_hevc PRIVATE cxx_std_17)
target_compile_options(hur_soft_hevc PRIVATE -Wall -Wextra -Werror)

find_library(log-lib log)
find_library(android-lib android)

set(FFMPEG_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/ffmpeg")
set(FFMPEG_INCLUDE_DIR "${FFMPEG_ROOT}/include")
set(FFMPEG_LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

set(FFMPEG_LIBS
    "${FFMPEG_LIB_DIR}/libavcodec.so"
    "${FFMPEG_LIB_DIR}/libavutil.so"
    "${FFMPEG_LIB_DIR}/libswscale.so"
)

set(HUR_FFMPEG_REQUIRED_FILES
    "${FFMPEG_INCLUDE_DIR}/libavcodec/avcodec.h"
    "${FFMPEG_INCLUDE_DIR}/libavutil/imgutils.h"
    "${FFMPEG_INCLUDE_DIR}/libswscale/swscale.h"
    ${FFMPEG_LIBS}
)

set(HUR_FFMPEG_READY TRUE)
foreach(required_file IN LISTS HUR_FFMPEG_REQUIRED_FILES)
    if(NOT EXISTS "${required_file}")
        set(HUR_FFMPEG_READY FALSE)
        break()
    endif()
endforeach()

message(STATUS "HUR FFmpeg for ${ANDROID_ABI}: ${HUR_FFMPEG_READY} (${FFMPEG_LIB_DIR})")

if(HUR_FFMPEG_READY)
    target_compile_definitions(hur_soft_hevc PRIVATE HUR_HAVE_FFMPEG=1)
    target_include_directories(hur_soft_hevc PRIVATE "${FFMPEG_INCLUDE_DIR}")
    target_link_libraries(hur_soft_hevc PRIVATE ${FFMPEG_LIBS})
else()
    # Not a failure: the bundled decoder is optional and the app falls back to the
    # device's MediaCodec. But on a 32-bit ABI the checked-in FFmpeg builds do not
    # exist, so this is the expected state there and it is worth saying out loud —
    # the APK then carries a JNI stub with no decoder behind it, and Settings reports
    # the bundled decoder as unavailable for that CPU.
    message(WARNING
        "HUR FFmpeg for ${ANDROID_ABI}: NOT AVAILABLE — no libavcodec/libavutil/"
        "libswscale in ${FFMPEG_LIB_DIR}, so hur_soft_hevc is built without the "
        "bundled software HEVC decoder and H.265 falls back to MediaCodec. See "
        "app/src/main/cpp/ffmpeg/README.md to add the libraries for this ABI.")
    target_compile_definitions(hur_soft_hevc PRIVATE HUR_HAVE_FFMPEG=0)
endif()

target_link_libraries(hur_soft_hevc PRIVATE ${log-lib} ${android-lib})

# Ordinary-UID, read-only legacy AP frequency query.
add_library(local_hotspot_radio SHARED local_hotspot_radio.c)
target_compile_options(local_hotspot_radio PRIVATE -Wall -Wextra -Werror)
