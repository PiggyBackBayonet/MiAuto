# Bundled HEVC decoder

This directory contains the FFmpeg headers used by the bundled HEVC decoder.
The checked-in `arm64-v8a` libraries were built from FFmpeg 7.1.1.

Expected layout:

```text
app/src/main/cpp/ffmpeg/include/libavcodec/avcodec.h
app/src/main/cpp/ffmpeg/include/libavutil/avutil.h
app/src/main/cpp/ffmpeg/include/libavutil/imgutils.h
app/src/main/cpp/ffmpeg/include/libswscale/swscale.h
app/src/main/jniLibs/arm64-v8a/libavcodec.so
app/src/main/jniLibs/arm64-v8a/libavutil.so
app/src/main/jniLibs/arm64-v8a/libswscale.so
```

Recommended FFmpeg build shape for Android head units:

```text
--disable-everything
--disable-programs
--disable-doc
--disable-avdevice
--disable-avfilter
--disable-avformat
--disable-encoders
--disable-muxers
--disable-demuxers
--enable-decoder=hevc
--enable-parser=hevc
--enable-protocol=file
--enable-swscale
--enable-pic
--enable-shared
--disable-static
--extra-ldflags=-Wl,-z,max-page-size=16384
```

Only package ABIs that are actually needed by the head unit. For SD 8155 this is
normally `arm64-v8a`. The JNI wrapper renders to the Android `Surface` through
`ANativeWindow`.

## ABIs without these libraries

Only `arm64-v8a` is checked in. `armeabi-v7a`, `x86` and `x86_64` therefore build
`hur_soft_hevc` without FFmpeg (`HUR_HAVE_FFMPEG=0`), `CMakeLists.txt` warns about it,
and `FfmpegHevcDecoder.isAvailable()` returns false — Settings then reports the bundled
decoder as unavailable and H.265 falls back to the device's MediaCodec. That is the
supported state for a 32-bit ARM head unit, not a defect: the alternative is a setting
that looks enabled and decodes nothing.

To add another ABI, build the same FFmpeg 7.1.1 shape for it and drop `libavcodec.so`,
`libavutil.so` and `libswscale.so` into `app/src/main/jniLibs/<abi>/`; the CMake check
above picks them up automatically and no Gradle change is needed. The headers in
`include/` are shared by every ABI — the only configure-dependent header is
`libavutil/avconfig.h`, which is identical for little-endian arm and arm64.

License: the bundled FFmpeg build reports `LGPL version 2.1 or later`. See
`FFMPEG_LICENSE.md` and `COPYING.LGPLv2.1`.
