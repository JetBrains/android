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

#include <sys/uio.h>
#include <unistd.h>

#include <cassert>
#include <cerrno>
#include <chrono>
#include <cmath>

#include "accessors/surface_control.h"
#include "agent.h"
#include "codec_output_buffer.h"
#include "jvm.h"
#include "log.h"
#include "string_printf.h"

namespace screensharing {

using namespace std;
using namespace std::chrono;

namespace {

constexpr int MAX_SUBSEQUENT_ERRORS = 5;
constexpr int MIN_VIDEO_RESOLUTION = 128;
constexpr int COLOR_FormatSurface = 0x7F000789;  // See android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface.
constexpr int MAX_FRAME_RATE = 60;
constexpr int REDUCED_FRAME_RATE = 30;  // Frame rate used for watches.
constexpr int DEFAULT_BIT_RATE = 10000000;
constexpr int MIN_BIT_RATE = 100000;
constexpr int I_FRAME_INTERVAL_SECONDS = 10;
constexpr int REPEAT_FRAME_DELAY_MILLIS = 100;
constexpr char const* AMEDIACODEC_KEY_REQUEST_SYNC_FRAME = "request-sync";  // Introduced in API 31.
constexpr char const* AMEDIAFORMAT_KEY_COLOR_STANDARD = "color-standard";  // Introduced in API 28.
constexpr int COLOR_STANDARD_BT601_NTSC = 4;  // See android.media.MediaFormat.COLOR_STANDARD_BT601_NTSC.
constexpr double SQRT_2 = 1.41421356237;
constexpr double SQRT_10 = 3.16227766017;

// Rounds the given number to the closest on logarithmic scale value of the for n * 10^k,
// where n is one of 1, 2 or 5 and k is integer number.
int32_t RoundToOneTwoFiveScale(double x) {
  double exp = floor(log10(x));
  double u = pow(10, exp);
  double f = x / u;
  int n =
      f < SQRT_2 ?
        1 :
      f < SQRT_10 ?
        2 :
      f < 5 * SQRT_2 ?
        5 :
        10;
  return n * round<int32_t>(u);
}

AMediaFormat* CreateMediaFormat(const string& mime_type) {
  AMediaFormat* media_format = AMediaFormat_new();
  AMediaFormat_setString(media_format, AMEDIAFORMAT_KEY_MIME, mime_type.c_str());
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_COLOR_FORMAT, COLOR_FormatSurface);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS);
  AMediaFormat_setInt64(media_format, AMEDIAFORMAT_KEY_REPEAT_PREVIOUS_FRAME_AFTER, REPEAT_FRAME_DELAY_MILLIS * 1000);
  if (mime_type == "video/x-vnd.on2.vp8") {
    // Workaround for b/247802881.
    AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_COLOR_STANDARD, COLOR_STANDARD_BT601_NTSC);
  }
  return media_format;
}

string GetVideoEncoderDetails(const CodecInfo& codec_info, int32_t width, int32_t height) {
  string codec_name = codec_info.name;
  string mime_type = codec_info.mime_type;
  Jni jni = Jvm::GetJni();
  JClass clazz = jni.GetClass("com/android/tools/screensharing/CodecInfo");
  jmethodID method = clazz.GetStaticMethod("getVideoEncoderDetails", "(Ljava/lang/String;Ljava/lang/String;II)Ljava/lang/String;");
  JObject details = clazz.CallStaticObjectMethod(method, JString(jni, codec_name).ref(), JString(jni, mime_type).ref(), width, height);
  if (details.IsNull()) {
    return "Failed to obtain parameters of " + codec_info.name;
  }
  return details.ToString();
}

int32_t RoundUpToMultipleOf(int32_t value, int32_t power_of_two) {
  return (value + power_of_two - 1) & ~(power_of_two - 1);
}

Size ComputeVideoSize(Size rotated_display_size, const CodecInfo& codec_info, Size max_video_resolution) {
  int32_t max_width = max_video_resolution.width;
  int32_t max_height = max_video_resolution.height;
  if (max_width < min(rotated_display_size.width, codec_info.max_resolution.width) / 2 ||
      max_height < min(rotated_display_size.height, codec_info.max_resolution.height) / 2) {
    // The size of the host display image is less than half of the display size.
    // Produce video in double resolution to have better quality after scaling down.
    max_width *= 2;
    max_height *= 2;
  }
  max_width = min(max_width, codec_info.max_resolution.width);
  max_height = min(max_height, codec_info.max_resolution.height);
  double display_width = rotated_display_size.width;
  double display_height = rotated_display_size.height;
  double scale = max(min(1.0, min(max_width / display_width, max_height / display_height)),
                     max(MIN_VIDEO_RESOLUTION / display_width, MIN_VIDEO_RESOLUTION / display_height));
  // The horizontal size alignment is multiple of 8 to accommodate FFmpeg video decoder.
  int32_t alignment_width = RoundUpToMultipleOf(codec_info.size_alignment.width, 8);
  int32_t alignment_height = codec_info.size_alignment.height;
  // Video width is computed first and height is computed based on the width to make sure that,
  // if the video has a sightly different aspect ratio than the display, it is taller rather than
  // wider.
  int32_t width = RoundUpToMultipleOf(lround(display_width * scale), alignment_width);
  int32_t height;
  while (width > codec_info.max_resolution.width ||
      (height = RoundUpToMultipleOf(lround(width * display_height / display_width), alignment_height)) > codec_info.max_resolution.height) {
    width -= alignment_width;  // Reduce video size to stay within maximum resolution of the codec.
  }
  return Size { width, height };
}

Size ConfigureCodec(AMediaCodec* codec, const CodecInfo& codec_info, Size max_video_resolution, int32_t bit_rate,
                    AMediaFormat* media_format, const DisplayInfo& display_info, int32_t display_id) {
  Size video_size = ComputeVideoSize(display_info.logical_size, codec_info, max_video_resolution);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_WIDTH, video_size.width);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_HEIGHT, video_size.height);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_FRAME_RATE,
                        min(codec_info.max_frame_rate, Agent::IsWatch() ? REDUCED_FRAME_RATE : MAX_FRAME_RATE));
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_BIT_RATE, bit_rate);
  media_status_t status = AMediaCodec_configure(codec, media_format, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
  if (status != AMEDIA_OK) {
    Log::Fatal(VIDEO_ENCODER_CONFIGURATION_ERROR, "Display %d: AMediaCodec_configure returned %d for video size %dx%d bit rate %d",
               display_id, status, video_size.width, video_size.height, bit_rate);
  }
  Log::I("Display %d: configured %s video size %dx%d bit_rate %d",
         display_id, codec_info.name.c_str(), video_size.width, video_size.height, bit_rate);
  return video_size;
}

}  // namespace

DisplayStreamer::DisplayStreamer(int32_t display_id, const CodecInfo* codec_info, Size max_video_resolution,
                                 int32_t initial_video_orientation, int32_t max_bit_rate, int socket_fd)
    : display_rotation_watcher_(this),
      display_id_(display_id),
      codec_info_(codec_info),
      socket_fd_(socket_fd),
      bit_rate_(max_bit_rate > 0 ? max_bit_rate : DEFAULT_BIT_RATE),
      max_video_resolution_(max_video_resolution),
      video_orientation_(initial_video_orientation) {
  assert(socket_fd > 0);
}

DisplayStreamer::~DisplayStreamer() {
  if (thread_.joinable()) {
    thread_.join();
  }
}

void DisplayStreamer::Start() {
  if (streamer_stopped_.exchange(false)) {
    Log::D("Display %d: starting video stream", display_id_);
    thread_ = thread([this]() {
      Jvm::AttachCurrentThread((string("DisplayStreamer ") + to_string(display_id_)).c_str());
      Run();
      Jvm::DetachCurrentThread();
      Log::D("Display %d: streaming terminated", display_id_);
    });
  }
}

void DisplayStreamer::Stop() {
  if (!streamer_stopped_.exchange(true)) {
    Log::D("Display %d: stopping video stream", display_id_);
    StopCodec();
    if (thread_.joinable()) {
      thread_.join();
    }
  }
}

void DisplayStreamer::OnDisplayAdded(int32_t display_id) {
}

void DisplayStreamer::OnDisplayRemoved(int32_t display_id) {
}

void DisplayStreamer::OnDisplayChanged(int32_t display_id) {
  if (display_id == display_id_) {
    Log::D("DisplayStreamer::OnDisplayChanged(%d)", display_id);
    StopCodec();
  }
}

void DisplayStreamer::Run() {
  Jni jni = Jvm::GetJni();
  WindowManager::WatchRotation(jni, display_id_, &display_rotation_watcher_);
  DisplayManager::AddDisplayListener(jni, this);

  AMediaFormat* media_format = CreateMediaFormat(codec_info_->mime_type);
  VideoPacketHeader packet_header = { .display_id = display_id_, .frame_number = 0};
  bool continue_streaming = true;
  consequent_deque_error_count_ = 0;

  while (continue_streaming && !streamer_stopped_ && !Agent::IsShuttingDown()) {
    DisplayInfo display_info = DisplayManager::GetDisplayInfo(jni, display_id_);
    if (!display_info.IsValid()) {
      break;
    }
    Log::D("Display %d: display_info: %s", display_id_, display_info.ToDebugString().c_str());
    AMediaCodec* codec = AMediaCodec_createCodecByName(codec_info_->name.c_str());
    if (codec == nullptr) {
      Log::Fatal(VIDEO_ENCODER_INITIALIZATION_ERROR, "Display %d: unable to create a %s video encoder",
                 display_id_, codec_info_->name.c_str());
    }
    VirtualDisplay virtual_display;
    JObject display_token;
    string display_name = StringPrintf("studio.screen.sharing:%d", display_id_);
    if (Agent::feature_level() >= 34) {
      virtual_display = DisplayManager::CreateVirtualDisplay(
          jni, display_name.c_str(), display_info.logical_size.width, display_info.logical_size.height, display_id_, nullptr);
    } else {
      bool secure = Agent::feature_level() < 31;  // Creation of secure displays is not allowed on API 31+.
      display_token = SurfaceControl::CreateDisplay(jni, display_name.c_str(), secure);
      if (display_token.IsNull()) {
        Log::Fatal(VIRTUAL_DISPLAY_CREATION_ERROR, "Display %d: unable to create a virtual display", display_id_);
      }
    }
    ANativeWindow* surface = nullptr;
    {
      unique_lock lock(mutex_);
      if (codec_stop_pending_) {
        codec_stop_pending_ = false;
        continue;  // Start another loop to refresh display information.
      }
      display_info_ = display_info;
      int32_t rotation_correction = video_orientation_ >= 0 ? NormalizeRotation(video_orientation_ - display_info.rotation) : 0;
      if (display_info.rotation == 2 && rotation_correction == 0) {
        // Simulated rotation is not capable of distinguishing between regular and upside down
        // display orientation. Compensate for that using rotation_correction.
        display_info.rotation = 0;
        rotation_correction = 2;
      }
      Size video_size = ConfigureCodec(
          codec, *codec_info_, max_video_resolution_.Rotated(rotation_correction), bit_rate_, media_format, display_info, display_id_);
      Log::D("Display %d: rotation=%d rotation_correction=%d video_size=%dx%d",
             display_id_, display_info.rotation, rotation_correction, video_size.width, video_size.height);
      media_status_t status = AMediaCodec_createInputSurface(codec, &surface);  // Requires API 26.
      if (status != AMEDIA_OK) {
        Log::Fatal(INPUT_SURFACE_CREATION_ERROR, "Display %d: AMediaCodec_createInputSurface returned %d", display_id_, status);
      }
      if (Agent::feature_level() >= 34) {
        virtual_display.Resize(video_size.width, video_size.height, display_info_.logical_density_dpi);
        virtual_display.SetSurface(surface);
      } else {
        int32_t height = lround(static_cast<double>(video_size.width) * display_info.logical_size.height / display_info.logical_size.width);
        int32_t y = (video_size.height - height) / 2;
        SurfaceControl::ConfigureProjection(jni, display_token, surface, display_info, { 0, y, video_size.width, height });
      }
      AMediaCodec_start(codec);
      running_codec_ = codec;
      Size display_size = display_info.NaturalSize();  // The display dimensions in the canonical orientation.
      packet_header.display_width = display_size.width;
      packet_header.display_height = display_size.height;
      packet_header.display_orientation = NormalizeRotation(display_info.rotation + rotation_correction);
      packet_header.display_orientation_correction = NormalizeRotation(rotation_correction);
      packet_header.flags =
          ((display_info.flags & DisplayInfo::FLAG_ROUND) ? VideoPacketHeader::FLAG_DISPLAY_ROUND : 0) |
          (bit_rate_reduced_ ? VideoPacketHeader::FLAG_BIT_RATE_REDUCED : 0);
      packet_header.bit_rate = bit_rate_;
    }
    AMediaFormat* sync_frame_request = AMediaFormat_new();
    AMediaFormat_setInt32(sync_frame_request, AMEDIACODEC_KEY_REQUEST_SYNC_FRAME, 0);
    continue_streaming = ProcessFramesUntilCodecStopped(codec, &packet_header, sync_frame_request);
    StopCodec();
    AMediaFormat_delete(sync_frame_request);
    if (virtual_display.HasDisplay()) {
      virtual_display.Release();
    } else {
      SurfaceControl::DestroyDisplay(jni, display_token);
    }
    AMediaCodec_delete(codec);
    ANativeWindow_release(surface);
  }

  AMediaFormat_delete(media_format);
  WindowManager::RemoveRotationWatcher(jni, display_id_, &display_rotation_watcher_);
  DisplayManager::RemoveDisplayListener(this);

  if (!continue_streaming) {
    Agent::Shutdown();
  }
}

bool DisplayStreamer::ProcessFramesUntilCodecStopped(AMediaCodec* codec, VideoPacketHeader* packet_header,
                                                     const AMediaFormat* sync_frame_request) {
  bool continue_streaming = true;
  bool first_frame_after_start = true;
  while (continue_streaming && IsCodecRunning()) {
    CodecOutputBuffer codec_buffer(codec, StringPrintf("Display %d: ", display_id_));
    if (!codec_buffer.Deque(-1)) {
      if (++consequent_deque_error_count_ >= MAX_SUBSEQUENT_ERRORS && !ReduceBitRate()) {
        ExitCode exitCode = bit_rate_ <= MIN_BIT_RATE ? WEAK_VIDEO_ENCODER : REPEATED_VIDEO_ENCODER_ERRORS;
        Log::Fatal(exitCode, "Display %d: too many video encoder errors:\n%s", display_id_,
                   GetVideoEncoderDetails(*codec_info_, packet_header->display_width, packet_header->display_height).c_str());
      }
      continue;
    }

    consequent_deque_error_count_ = 0;
    continue_streaming = !codec_buffer.IsEndOfStream();
    if (!IsCodecRunning()) {
      return true;
    }
    // Skip an AV1-specific data packet that is not a part of AV1 bitstream.
    // See https://aomediacodec.github.io/av1-spec/#obu-header-semantics.
    if (codec_info_->mime_type == "video/av01" && (*codec_buffer.buffer() & 0x80) != 0) {
      continue;
    }

    if (first_frame_after_start) {
      // Request another sync frame to prevent a green bar that sometimes appears at the bottom
      // of the first frame.
      media_status_t status = AMediaCodec_setParameters(codec, sync_frame_request);
      if (status != AMEDIA_OK) {
        Log::E("Display %d: AMediaCodec_setParameters returned %d", display_id_, status);
      }
      first_frame_after_start = false;
    }
    int64_t delta = duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count() - Agent::GetLastTouchEventTime();
    if (delta < 1000) {
      Log::D("Display %d: video packet of %d bytes at %" PRIi64 " ms since last touch event", display_id_, codec_buffer.size(), delta);
    }
    packet_header->origination_timestamp_us = duration_cast<microseconds>(system_clock::now().time_since_epoch()).count();
    if (codec_buffer.IsConfig()) {
      packet_header->presentation_timestamp_us = 0;
    } else {
      if (presentation_timestamp_offset_ == 0) {
        presentation_timestamp_offset_ = codec_buffer.presentation_time_us() - 1;
      }
      packet_header->presentation_timestamp_us = codec_buffer.presentation_time_us() - presentation_timestamp_offset_;
    }
    packet_header->packet_size = codec_buffer.size();
    if (Log::IsEnabled(Log::Level::VERBOSE)) {
      Log::V("Display %d: writing video packet: %s", display_id_, packet_header->ToDebugString().c_str());
    }
    iovec buffers[] = { { packet_header, VideoPacketHeader::SIZE }, { codec_buffer.buffer(), static_cast<size_t>(codec_buffer.size()) } };
    if (writev(socket_fd_, buffers, 2) != buffers[0].iov_len + buffers[1].iov_len) {
      if (errno != EBADF && errno != EPIPE) {
        Log::Fatal(SOCKET_IO_ERROR, "Error writing to video socket - %s", strerror(errno));
      }
      continue_streaming = false;
    }
    if (!codec_buffer.IsConfig()) {
      packet_header->frame_number++;
    }
    bit_rate_reduced_ = false;
  }
  return continue_streaming;
}

void DisplayStreamer::SetVideoOrientation(int32_t orientation) {
  Log::D("Display %d: setting video orientation %d", display_id_, orientation);
  if (orientation == CURRENT_DISPLAY_ORIENTATION) {
    unique_lock lock(mutex_);
    if (video_orientation_ >= 0) {
      Agent::session_environment().RestoreAccelerometerRotation();
      video_orientation_ = -1;
      StopCodecUnlocked();
    }
    return;
  }

  Agent::session_environment().DisableAccelerometerRotation();

  Jni jni = Jvm::GetJni();
  bool rotation_was_frozen = WindowManager::IsRotationFrozen(jni, display_id_);

  unique_lock lock(mutex_);
  if (orientation == CURRENT_VIDEO_ORIENTATION) {
    orientation = video_orientation_;
  }
  if (orientation >= 0) {
    WindowManager::FreezeRotation(jni, display_id_, orientation);
    // Restore the original state of auto-display_rotation.
    if (!rotation_was_frozen) {
      WindowManager::ThawRotation(jni, display_id_);
    }

    if (video_orientation_ != orientation) {
      video_orientation_ = orientation;
      StopCodecUnlocked();
    }
  }
}

void DisplayStreamer::SetMaxVideoResolution(Size max_video_resolution) {
  unique_lock lock(mutex_);
  if (max_video_resolution_ != max_video_resolution) {
    max_video_resolution_ = max_video_resolution;
    StopCodecUnlocked();
  }
}

DisplayInfo DisplayStreamer::GetDisplayInfo() {
  unique_lock lock(mutex_);
  return display_info_;
}

void DisplayStreamer::StopCodec() {
  unique_lock lock(mutex_);
  StopCodecUnlocked();
}

void DisplayStreamer::StopCodecUnlocked() {
  if (running_codec_ == nullptr) {
    codec_stop_pending_ = true;
  } else {
    Log::D("Display %d: stopping codec", display_id_);
    AMediaCodec_stop(running_codec_);
    running_codec_ = nullptr;
    consequent_deque_error_count_ = 0;
  }
}

bool DisplayStreamer::IsCodecRunning() {
  unique_lock lock(mutex_);
  return running_codec_ != nullptr;
}

bool DisplayStreamer::ReduceBitRate() {
  if (bit_rate_ <= MIN_BIT_RATE) {
    return false;
  }
  StopCodec();
  bit_rate_ = RoundToOneTwoFiveScale(bit_rate_ / 2);
  bit_rate_reduced_ = true;
  Log::I("Display %d: bit rate reduced to %d", display_id_, bit_rate_);
  return true;
}

DisplayStreamer::DisplayRotationWatcher::DisplayRotationWatcher(DisplayStreamer* display_streamer)
    : display_streamer(display_streamer),
      display_rotation(-1) {
}

void DisplayStreamer::DisplayRotationWatcher::OnRotationChanged(int32_t new_rotation) {
  auto old_rotation = display_rotation.exchange(new_rotation);
  Log::D("Display %d: DisplayRotationWatcher::OnRotationChanged: new_rotation=%d old_rotation=%d",
         display_streamer->display_id_, new_rotation, old_rotation);
  if (new_rotation != old_rotation) {
    display_streamer->StopCodec();
  }
}

}  // namespace screensharing
