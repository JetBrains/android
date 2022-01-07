/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "display_streamer.h"

#include <linux/uio.h>
#include <sys/uio.h>
#include <unistd.h>

#include <cerrno>
#include <chrono>
#include <cmath>

#include "accessors/surface_control.h"
#include "accessors/display_manager.h"
#include "agent.h"
#include "jvm.h"
#include "log.h"

namespace screensharing {

using namespace std;
using namespace std::chrono;

namespace {

struct CodecOutputBuffer {
  explicit CodecOutputBuffer(AMediaCodec* codec)
      : index(-1),
        codec(codec),
        info(),
        buffer(),
        size() {
  }

  ~CodecOutputBuffer() {
    if (index >= 0) {
      AMediaCodec_releaseOutputBuffer(codec, static_cast<size_t>(index), false);
    }
  }

  [[nodiscard]] bool Deque(int64_t timeout_us) {
    index = AMediaCodec_dequeueOutputBuffer(codec, &info, timeout_us);
    if (index < 0) {
      Log::D("AMediaCodec_dequeueOutputBuffer returned %ld", static_cast<long>(index));
      return false;
    }
    if (Log::IsEnabled(Log::Level::VERBOSE)) {
      Log::V("CodecOutputBuffer::Deque: index:%ld offset:%d size:%d flags:0x%x, presentationTimeUs:%" PRId64,
             static_cast<long>(index), info.offset, info.size, info.flags, info.presentationTimeUs);
    }
    buffer = AMediaCodec_getOutputBuffer(codec, static_cast<size_t>(index), &size);
    if (buffer == nullptr) {
      Log::W("CodecOutputBuffer::Deque: AMediaCodec_getOutputBuffer(codec, %ld, &size) returned null", static_cast<long>(index));
      return false;
    }
    return true;
  }

  [[nodiscard]] bool IsEndOfStream() const {
    return (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) != 0;
  }

  [[nodiscard]] bool IsConfig() const {
    return (info.flags & AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG) != 0;
  }

  ssize_t index;
  AMediaCodec* codec;
  AMediaCodecBufferInfo info;
  uint8_t* buffer;
  size_t size;
};

constexpr const char* MIMETYPE_VIDEO_AVC = "video/avc";
constexpr int COLOR_FormatSurface = 0x7F000789;  // See android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
constexpr int BIT_RATE = 8000000;
constexpr int I_FRAME_INTERVAL_SECONDS = 10;
constexpr int REPEAT_FRAME_DELAY_MILLIS = 100;

AMediaFormat* createMediaFormat() {
  AMediaFormat* media_format = AMediaFormat_new();
  AMediaFormat_setString(media_format, AMEDIAFORMAT_KEY_MIME, MIMETYPE_VIDEO_AVC);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_COLOR_FORMAT, COLOR_FormatSurface);
  // Does not affect the actual frame rate, but must be present.
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_FRAME_RATE, 60);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_BIT_RATE, BIT_RATE);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS);
  AMediaFormat_setInt64(media_format, AMEDIAFORMAT_KEY_REPEAT_PREVIOUS_FRAME_AFTER, REPEAT_FRAME_DELAY_MILLIS * 1000);
  return media_format;
}

int32_t RoundUpToMultipleOf8(int32_t value) {
  return (value + 7) & ~7;
}

Size ComputeVideoSize(Size rotated_display_size, Size max_resolution) {
  auto width = rotated_display_size.width;
  auto height = rotated_display_size.height;
  double scale = min(1.0, min(static_cast<double>(max_resolution.width) / width, static_cast<double>(max_resolution.height) / height));
  if (scale == 0) {
    scale = 1.;
  }
  return Size { RoundUpToMultipleOf8(lround(width * scale)), RoundUpToMultipleOf8(lround(height * scale)) };
}

// The display area defined by display_info.logical_size is mapped to projected size and then
// rotated counterclockwise by the number of quadrants determined by the rotation parameter.
void ConfigureDisplay(const SurfaceControl& surface_control, jobject display_token, ANativeWindow* surface, int32_t rotation,
                      const DisplayInfo& display_info, Size projected_size) {
  SurfaceControl::Transaction transaction(surface_control);
  surface_control.SetDisplaySurface(display_token, surface);
  surface_control.SetDisplayProjection(
      display_token, NormalizeRotation(-rotation), display_info.logical_size.toRect(), projected_size.toRect());
  surface_control.SetDisplayLayerStack(display_token, display_info.layer_stack);
}

}  // namespace

DisplayStreamer::DisplayStreamer(int display_id, Size max_video_resolution, int socket_fd)
    : display_id_(display_id),
      max_video_resolution_(max_video_resolution),
      socket_fd_(socket_fd),
      presentation_timestamp_offset_(0),
      display_rotation_watcher_(this),
      stopped_(),
      video_orientation_(-1),
      running_codec_() {
  assert(socket_fd > 0);
}

void DisplayStreamer::Run() {
  Jni jni = Jvm::GetJni();
  WindowManager::WatchRotation(jni, &display_rotation_watcher_);
  VideoPacketHeader packet_header = { .frame_number = 1 };
  AMediaFormat* media_format = createMediaFormat();
  SurfaceControl surface_control(jni);

  while (!stopped_) {
    AMediaCodec* codec = AMediaCodec_createEncoderByType(MIMETYPE_VIDEO_AVC);
    if (codec == nullptr) {
      Log::Fatal("Unable to create AMediaCodec");
    }
    JObject display = surface_control.CreateDisplay("screen-sharing-agent", false); // secure = true doesn't work with API 31+.
    if (display.IsNull()) {
      Log::Fatal("Unable to create a virtual display");
    }
    DisplayInfo display_info = DisplayManager::GetDisplayInfo(jni, display_id_);
    Log::D("display_info: %s", display_info.ToDebugString().c_str());
    ANativeWindow* surface = nullptr;
    {
      scoped_lock lock(mutex_);
      int32_t extra_rotation = video_orientation_ >= 0 ? NormalizeRotation(video_orientation_ - display_info.rotation) : 0;
      Size video_size = ComputeVideoSize(display_info.logical_size.Rotated(extra_rotation), max_video_resolution_);
      Log::D("DisplayStreamer::Run: video_size=%dx%d", video_size.width, video_size.height);
      AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_WIDTH, video_size.width);
      AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_HEIGHT, video_size.height);
      AMediaCodec_configure(codec, media_format, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
      AMediaCodec_createInputSurface(codec, &surface);  // Requires API 26.
      if (surface == nullptr) {
        Log::Fatal("Unable to create input surface");
      }
      ConfigureDisplay(surface_control, display, surface, extra_rotation, display_info, video_size.Rotated(-extra_rotation));
      AMediaCodec_start(codec);
      running_codec_ = codec;
      Size display_size = display_info.NaturalSize();  // The display dimensions in the canonical orientation.
      packet_header.display_width = display_size.width;
      packet_header.display_height = display_size.height;
      packet_header.display_orientation = NormalizeRotation(display_info.rotation + extra_rotation);
    }
    bool end_of_stream = ProcessFramesUntilStopped(codec, &packet_header);
    StopCodec();
    surface_control.DestroyDisplay(display);
    AMediaCodec_delete(codec);
    ANativeWindow_release(surface);
    if (end_of_stream) {
      break;
    }
  }
  AMediaFormat_delete(media_format);
  Agent::Shutdown();
}

void DisplayStreamer::OnVideoOrientationChanged(int32_t orientation) {
  scoped_lock lock(mutex_);
  if (video_orientation_ != orientation) {
    video_orientation_ = orientation;
    StopCodecUnlocked();
  }
}

void DisplayStreamer::OnMaxVideoResolutionChanged(Size max_video_resolution) {
  scoped_lock lock(mutex_);
  if (max_video_resolution_ != max_video_resolution) {
    max_video_resolution_ = max_video_resolution;
    StopCodecUnlocked();
  }
}

void DisplayStreamer::Shutdown() {
  if (socket_fd_ > 0) {
    close(socket_fd_);
    StopCodec();
  }
}

bool DisplayStreamer::ProcessFramesUntilStopped(AMediaCodec* codec, VideoPacketHeader* packet_header) {
  bool end_of_stream = false;
  while (!end_of_stream && IsCodecRunning()) {
    CodecOutputBuffer codec_buffer(codec);
    if (!codec_buffer.Deque(-1)) {
      continue;
    }
    end_of_stream = codec_buffer.IsEndOfStream();
    if (!IsCodecRunning()) {
      return false;
    }
    packet_header->origination_timestamp_us = duration_cast<microseconds>(system_clock::now().time_since_epoch()).count();
    if (codec_buffer.IsConfig()) {
      packet_header->presentation_timestamp_us = 0;
    } else {
      if (presentation_timestamp_offset_ == 0) {
        presentation_timestamp_offset_ = codec_buffer.info.presentationTimeUs - 1;
      }
      packet_header->presentation_timestamp_us = codec_buffer.info.presentationTimeUs - presentation_timestamp_offset_;
    }
    packet_header->packet_size = codec_buffer.info.size;
    Log::V("DisplayStreamer::ProcessFramesUntilStopped: writing video packet %s", packet_header->ToDebugString().c_str());
    iovec buffers[] = { { packet_header, sizeof(*packet_header) }, { codec_buffer.buffer, static_cast<size_t>(codec_buffer.info.size) } };
    if (writev(socket_fd_, buffers, 2) != buffers[0].iov_len + buffers[1].iov_len) {
      if (errno != EBADF && errno != EPIPE) {
        Log::Fatal("Error writing to video socket - %s", strerror(errno));
      }
      end_of_stream = true;
    }
    if (codec_buffer.IsConfig()) {
      packet_header->frame_number++;
    }
  }
  return end_of_stream;
}

void DisplayStreamer::StopCodec() {
  scoped_lock lock(mutex_);
  StopCodecUnlocked();
}

void DisplayStreamer::StopCodecUnlocked() {
  if (running_codec_ != nullptr) {
    Log::D("DisplayStreamer::StopCodecUnlocked: stopping codec");
    AMediaCodec_stop(running_codec_);
    running_codec_ = nullptr;
  }
}

bool DisplayStreamer::IsCodecRunning() {
  scoped_lock lock(mutex_);
  return running_codec_ != nullptr;
}

DisplayStreamer::DisplayRotationWatcher::DisplayRotationWatcher(DisplayStreamer* display_streamer)
    : display_streamer(display_streamer),
      display_rotation() {
}

DisplayStreamer::DisplayRotationWatcher::~DisplayRotationWatcher() {
  WindowManager::RemoveRotationWatcher(Jvm::GetJni(), this);
}

void DisplayStreamer::DisplayRotationWatcher::OnRotationChanged(int32_t new_rotation) {
  auto old_rotation = display_rotation.exchange(new_rotation);
  if (new_rotation != old_rotation) {
    display_streamer->StopCodec();
  }
}

}  // namespace screensharing
