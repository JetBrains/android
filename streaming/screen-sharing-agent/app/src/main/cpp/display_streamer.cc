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
#include <sys/system_properties.h>
#include <sys/uio.h>
#include <unistd.h>

#include <cassert>
#include <cerrno>
#include <chrono>
#include <cmath>

#include "accessors/surface_control.h"
#include "agent.h"
#include "jvm.h"
#include "log.h"

namespace screensharing {

using namespace std;
using namespace std::chrono;

struct CodecInfo {
  std::string mime_type;
  std::string name;
  Size max_resolution;
  Size size_alignment;

  CodecInfo(std::string mime_type, std::string name, Size max_resolution, Size size_alignment)
      : mime_type(std::move(mime_type)),
        name(std::move(name)),
        max_resolution(max_resolution),
        size_alignment(size_alignment) {
  }
};

namespace {

constexpr int MAX_SUBSEQUENT_ERRORS = 10;
constexpr int MIN_VIDEO_RESOLUTION = 128;
constexpr int COLOR_FormatSurface = 0x7F000789;  // See android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface.
constexpr int BIT_RATE = 8000000;
constexpr int BIT_RATE_REDUCED = 1000000;
constexpr int I_FRAME_INTERVAL_SECONDS = 10;
constexpr int REPEAT_FRAME_DELAY_MILLIS = 100;
constexpr int CHANNEL_HEADER_LENGTH = 20;
constexpr char const* AMEDIACODEC_KEY_REQUEST_SYNC_FRAME = "request-sync";  // Introduced in API 31.
constexpr char const* AMEDIAFORMAT_KEY_COLOR_STANDARD = "color-standard";  // Introduced in API 28.
constexpr int COLOR_STANDARD_BT601_NTSC = 4;  // See android.media.MediaFormat.COLOR_STANDARD_BT601_NTSC.

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
      Log::W("AMediaCodec_dequeueOutputBuffer returned %ld", static_cast<long>(index));
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

bool IsUnderpoweredCodec(Size codec_resolution, Size display_resolution) {
  int32_t resolution = min(codec_resolution.width, codec_resolution.height);
  return resolution < 1024 || (Agent::api_level() < 32 && resolution < max(display_resolution.width, display_resolution.height));
}

AMediaFormat* CreateMediaFormat(const string& mime_type) {
  AMediaFormat* media_format = AMediaFormat_new();
  AMediaFormat_setString(media_format, AMEDIAFORMAT_KEY_MIME, mime_type.c_str());
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_COLOR_FORMAT, COLOR_FormatSurface);
  // Does not affect the actual frame rate, but must be present.
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_FRAME_RATE, 60);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS);
  AMediaFormat_setInt64(media_format, AMEDIAFORMAT_KEY_REPEAT_PREVIOUS_FRAME_AFTER, REPEAT_FRAME_DELAY_MILLIS * 1000);
  if (mime_type == "video/x-vnd.on2.vp8") {
    // Workaround for b/247802881.
    AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_COLOR_STANDARD, COLOR_STANDARD_BT601_NTSC);
  }
  return media_format;
}

CodecInfo* SelectVideoEncoder(const string& mime_type) {
  Jni jni = Jvm::GetJni();
  JClass clazz = jni.GetClass("com/android/tools/screensharing/CodecInfo");
  jmethodID method = clazz.GetStaticMethod("selectVideoEncoderForType",
                                           "(Ljava/lang/String;)Lcom/android/tools/screensharing/CodecInfo;");
  JObject codec_info = clazz.CallStaticObjectMethod(method, JString(jni, mime_type).ref());
  if (codec_info.IsNull()) {
    Log::Fatal(VIDEO_ENCODER_NOT_FOUND, "No video encoder is available for %s", mime_type.c_str());
  }
  JString jname = JString(codec_info.GetObjectField(clazz.GetFieldId("name", "Ljava/lang/String;")));
  string codec_name = jname.IsNull() ? "<unnamed>" : jname.GetValue();
  int max_width = codec_info.GetIntField(clazz.GetFieldId("maxWidth", "I"));
  int max_height = codec_info.GetIntField(clazz.GetFieldId("maxHeight", "I"));
  int width_alignment = codec_info.GetIntField(clazz.GetFieldId("widthAlignment", "I"));
  int height_alignment = codec_info.GetIntField(clazz.GetFieldId("heightAlignment", "I"));
  return new CodecInfo(mime_type, codec_name, Size(max_width, max_height), Size(width_alignment, height_alignment));
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

[[noreturn]] void FatalVideoEncoderError(const char* error_message, const CodecInfo& codec_info, int32_t width, int32_t height) {
  if (codec_info.max_resolution.width <= 640 && codec_info.max_resolution.height <= 640) {
    Log::Fatal(WEAK_VIDEO_ENCODER, "%s:\n%s", error_message, GetVideoEncoderDetails(codec_info, width, height).c_str());
  }
  Log::Fatal(REPEATED_VIDEO_ENCODER_ERRORS, "%s:\n%s", error_message, GetVideoEncoderDetails(codec_info, width, height).c_str());
}

void WriteChannelHeader(const string& codec_name, int socket_fd) {
  string buf;
  int buf_size = 1 + CHANNEL_HEADER_LENGTH;
  buf.reserve(buf_size);  // Single-byte channel marker followed by header.
  buf.append("V");  // Video channel marker.
  buf.append(codec_name);
  // Pad with spaces to the fixed length.
  while (buf.length() < buf_size) {
    buf.insert(buf.end(), ' ');
  }
  if (write(socket_fd, buf.c_str(), buf_size) != buf_size) {
    if (errno != EBADF && errno != EPIPE) {
      Log::Fatal(SOCKET_IO_ERROR, "Error writing to video socket - %s", strerror(errno));
    }
    Agent::Shutdown();
  }
}

int32_t RoundUpToMultipleOf(int32_t value, int32_t power_of_two) {
  return (value + power_of_two - 1) & ~(power_of_two - 1);
}

Size ComputeVideoSize(Size rotated_display_size, const CodecInfo& codec_info, Size max_video_resolution) {
  int32_t max_width = min(max_video_resolution.width, codec_info.max_resolution.width);
  int32_t max_height = min(max_video_resolution.height, codec_info.max_resolution.height);
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

Size ConfigureCodec(AMediaCodec* codec, const CodecInfo& codec_info, Size max_video_resolution, AMediaFormat* media_format,
                    const DisplayInfo& display_info) {
  Size video_size = ComputeVideoSize(display_info.logical_size, codec_info, max_video_resolution);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_WIDTH, video_size.width);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_HEIGHT, video_size.height);
  int32_t bit_rate = 0;
  AMediaFormat_getInt32(media_format, AMEDIAFORMAT_KEY_BIT_RATE, &bit_rate);
  media_status_t status = AMediaCodec_configure(codec, media_format, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
  if (status != AMEDIA_OK) {
    Log::Fatal(VIDEO_ENCODER_CONFIGURATION_ERROR, "AMediaCodec_configure returned %d for video_size=%dx%d bit rate=%d",
               status, video_size.width, video_size.height, bit_rate);
  }
  Log::I("Configured %s video_size=%dx%d bit rate=%d", codec_info.name.c_str(), video_size.width, video_size.height, bit_rate);
  return video_size;
}

}  // namespace

DisplayStreamer::DisplayStreamer(int32_t display_id, string codec_name, Size max_video_resolution, int32_t initial_video_orientation,
                                 int32_t max_bit_rate, int socket_fd)
    : display_rotation_watcher_(this),
      display_id_(display_id),
      codec_name_(std::move(codec_name)),
      socket_fd_(socket_fd),
      max_bit_rate_(max_bit_rate),
      max_video_resolution_(max_video_resolution),
      video_orientation_(initial_video_orientation) {
  assert(socket_fd > 0);
  string mime_type = (codec_name_.compare(0, 2, "vp") == 0 ? "video/x-vnd.on2." : "video/") + codec_name_;
  codec_info_ = SelectVideoEncoder(mime_type);
  WriteChannelHeader(codec_name_, socket_fd_);
}

DisplayStreamer::~DisplayStreamer() {
  if (thread_.joinable()) {
    thread_.join();
  }
  delete codec_info_;
}

void DisplayStreamer::Start() {
  if (streamer_stopped_) {
    Log::D("Starting display stream");
    streamer_stopped_ = false;
    thread_ = thread([this]() {
      Jvm::AttachCurrentThread("DisplayStreamer");
      Run();
      Jvm::DetachCurrentThread();
    });
  }
}

void DisplayStreamer::Stop() {
  if (!streamer_stopped_) {
    Log::D("Stopping display stream");
    streamer_stopped_ = true;
    StopCodecAndWaitForThreadToTerminate();
  }
}

void DisplayStreamer::Shutdown() {
  if (socket_fd_ > 0) {
    close(socket_fd_);
    StopCodec();
    if (thread_.joinable()) {
      thread_.join();
    }
  }
}

void DisplayStreamer::StopCodecAndWaitForThreadToTerminate() {
  StopCodec();
  if (thread_.joinable()) {
    thread_.join();
  }
}

void DisplayStreamer::Run() {
  Jni jni = Jvm::GetJni();

  Log::D("Using %s video encoder with %dx%d max resolution",
         codec_info_->name.c_str(), codec_info_->max_resolution.width, codec_info_->max_resolution.height);
  AMediaFormat* media_format = CreateMediaFormat(codec_info_->mime_type);

  WindowManager::WatchRotation(jni, &display_rotation_watcher_);
  DisplayManager::RegisterDisplayListener(jni, this);
  VideoPacketHeader packet_header = {.frame_number = 1};

  bool end_of_stream = false;
  consequent_deque_error_count_ = 0;
  while (!streamer_stopped_ && !end_of_stream && !Agent::IsShuttingDown()) {
    AMediaCodec* codec = AMediaCodec_createCodecByName(codec_info_->name.c_str());
    if (codec == nullptr) {
      Log::Fatal(VIDEO_ENCODER_INITIALIZATION_ERROR, "Unable to create a %s video encoder", codec_info_->name.c_str());
    }
    DisplayInfo display_info = DisplayManager::GetDisplayInfo(jni, display_id_);
    Log::D("display_info: %s", display_info.ToDebugString().c_str());
    VirtualDisplay virtual_display;
    JObject display_token;
    if (DisplayManager::CanCreateVirtualDisplay(jni)) {
      virtual_display = DisplayManager::CreateVirtualDisplay(
          jni, "screen-sharing-agent", display_info.logical_size.width, display_info.logical_size.height, display_id_, nullptr);
    } else {
      bool secure = Agent::api_level() < 31;  // Creation of secure displays is not allowed on API 31+.
      display_token = SurfaceControl::CreateDisplay(jni, "screen-sharing-agent", secure);
      if (display_token.IsNull()) {
        Log::Fatal(VIRTUAL_DISPLAY_CREATION_ERROR, "Unable to create a virtual display");
      }
    }
    // Use heuristics for determining a bit rate value that doesn't cause SIGABRT in the encoder (b/251659422).
    int32_t bit_rate = IsUnderpoweredCodec(codec_info_->max_resolution, display_info.logical_size) ? BIT_RATE_REDUCED : BIT_RATE;
    if (max_bit_rate_ > 0 && bit_rate > max_bit_rate_) {
      bit_rate = max_bit_rate_;
    }
    AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_BIT_RATE, bit_rate);
    ANativeWindow* surface = nullptr;
    {
      scoped_lock lock(mutex_);
      display_info_ = display_info;
      int32_t rotation_correction = video_orientation_ >= 0 ? NormalizeRotation(video_orientation_ - display_info.rotation) : 0;
      if (display_info.rotation == 2 && rotation_correction == 0) {
        // Simulated rotation is not capable of distinguishing between regular and upside down
        // display orientation. Compensate for that using rotation_correction.
        display_info.rotation = 0;
        rotation_correction = 2;
      }
      Size video_size = ConfigureCodec(codec, *codec_info_, max_video_resolution_.Rotated(rotation_correction), media_format, display_info);
      Log::D("rotation=%d rotation_correction = %d video_size = %dx%d",
             display_info.rotation, rotation_correction, video_size.width, video_size.height);
      media_status_t status = AMediaCodec_createInputSurface(codec, &surface);  // Requires API 26.
      if (status != AMEDIA_OK) {
        Log::Fatal(INPUT_SURFACE_CREATION_ERROR, "AMediaCodec_createInputSurface returned %d", status);
      }
      if (virtual_display.HasDisplay()) {
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
      packet_header.display_round = (display_info.flags & DisplayInfo::FLAG_ROUND) ? 1 : 0;
    }
    AMediaFormat* sync_frame_request = AMediaFormat_new();
    AMediaFormat_setInt32(sync_frame_request, AMEDIACODEC_KEY_REQUEST_SYNC_FRAME, 0);
    end_of_stream = ProcessFramesUntilCodecStopped(codec, &packet_header, sync_frame_request);
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
  WindowManager::RemoveRotationWatcher(jni, &display_rotation_watcher_);
  DisplayManager::UnregisterDisplayListener(jni, this);

  if (end_of_stream) {
    Agent::Shutdown();
  }
}

bool DisplayStreamer::ProcessFramesUntilCodecStopped(AMediaCodec* codec, VideoPacketHeader* packet_header,
                                                     const AMediaFormat* sync_frame_request) {
  bool end_of_stream = false;
  bool first_frame_after_start = true;
  while (!end_of_stream && IsCodecRunning()) {
    CodecOutputBuffer codec_buffer(codec);
    if (!codec_buffer.Deque(-1)) {
      if (++consequent_deque_error_count_ >= MAX_SUBSEQUENT_ERRORS) {
        FatalVideoEncoderError("Too many video encoder errors", *codec_info_, packet_header->display_width, packet_header->display_height);
      }
      continue;
    }
    consequent_deque_error_count_ = 0;
    end_of_stream = codec_buffer.IsEndOfStream();
    if (!IsCodecRunning()) {
      return false;
    }
    // Skip an AV1-specific data packet that is not a part of AV1 bitstream.
    // See https://aomediacodec.github.io/av1-spec/#obu-header-semantics.
    if (codec_info_->mime_type == "video/av01" && (*codec_buffer.buffer & 0x80) != 0) {
      continue;
    }
    if (first_frame_after_start) {
      // Request another sync frame to prevent a green bar that sometimes appears at the bottom
      // of the first frame.
      media_status_t status = AMediaCodec_setParameters(codec, sync_frame_request);
      if (status != AMEDIA_OK) {
        Log::E("AMediaCodec_setParameters returned %d", status);
      }
      first_frame_after_start = false;
    }
    int64_t delta = duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count() - Agent::GetLastTouchEventTime();
    if (delta < 1000) {
      Log::D("Video packet of %d bytes at %" PRIi64 " ms since last touch event", codec_buffer.info.size, delta);
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
    if (Log::IsEnabled(Log::Level::VERBOSE)) {
      Log::V("DisplayStreamer::ProcessFramesUntilCodecStopped: writing video packet %s", packet_header->ToDebugString().c_str());
    }
    iovec buffers[] = { { packet_header, sizeof(*packet_header) }, { codec_buffer.buffer, static_cast<size_t>(codec_buffer.info.size) } };
    if (writev(socket_fd_, buffers, 2) != buffers[0].iov_len + buffers[1].iov_len) {
      if (errno != EBADF && errno != EPIPE) {
        Log::Fatal(SOCKET_IO_ERROR, "Error writing to video socket - %s", strerror(errno));
      }
      end_of_stream = true;
    }
    if (!codec_buffer.IsConfig()) {
      packet_header->frame_number++;
    }
  }
  return end_of_stream;
}

void DisplayStreamer::SetVideoOrientation(int32_t orientation) {
  Log::D("DisplayStreamer::SetVideoOrientation(%d)", orientation);
  if (orientation == CURRENT_DISPLAY_ORIENTATION) {
    scoped_lock lock(mutex_);
    if (video_orientation_ >= 0) {
      Agent::session_environment().RestoreAccelerometerRotation();
      video_orientation_ = -1;
      StopCodecUnlocked();
    }
    return;
  }

  Agent::session_environment().DisableAccelerometerRotation();

  Jni jni = Jvm::GetJni();
  bool rotation_was_frozen = WindowManager::IsRotationFrozen(jni);

  scoped_lock lock(mutex_);
  if (orientation == CURRENT_VIDEO_ORIENTATION) {
    orientation = video_orientation_;
  }
  if (orientation >= 0) {
    WindowManager::FreezeRotation(jni, orientation);
    // Restore the original state of auto-display_rotation.
    if (!rotation_was_frozen) {
      WindowManager::ThawRotation(jni);
    }

    if (video_orientation_ != orientation) {
      video_orientation_ = orientation;
      StopCodecUnlocked();
    }
  }
}

void DisplayStreamer::SetMaxVideoResolution(Size max_video_resolution) {
  scoped_lock lock(mutex_);
  if (max_video_resolution_ != max_video_resolution) {
    max_video_resolution_ = max_video_resolution;
    StopCodecUnlocked();
  }
}

DisplayInfo DisplayStreamer::GetDisplayInfo() {
  scoped_lock lock(mutex_);
  return display_info_;
}

void DisplayStreamer::OnDisplayAdded(int32_t display_id) {
}

void DisplayStreamer::OnDisplayRemoved(int32_t display_id) {
}

void DisplayStreamer::OnDisplayChanged(int32_t display_id) {
  Log::D("DisplayStreamer::OnDisplayChanged(%d)", display_id);
  if (display_id == DEFAULT_DISPLAY) {
    StopCodec();
  }
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
    consequent_deque_error_count_ = 0;
  }
}

bool DisplayStreamer::IsCodecRunning() {
  scoped_lock lock(mutex_);
  return running_codec_ != nullptr;
}

DisplayStreamer::DisplayRotationWatcher::DisplayRotationWatcher(DisplayStreamer* display_streamer)
    : display_streamer(display_streamer),
      display_rotation(-1) {
}

DisplayStreamer::DisplayRotationWatcher::~DisplayRotationWatcher() {
  WindowManager::RemoveRotationWatcher(Jvm::GetJni(), this);
}

void DisplayStreamer::DisplayRotationWatcher::OnRotationChanged(int32_t new_rotation) {
  auto old_rotation = display_rotation.exchange(new_rotation);
  Log::D("DisplayRotationWatcher::OnRotationChanged: new_rotation=%d old_rotation=%d", new_rotation, old_rotation);
  if (new_rotation != old_rotation) {
    display_streamer->StopCodec();
  }
}

}  // namespace screensharing
