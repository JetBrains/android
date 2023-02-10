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
constexpr double MIN_VIDEO_RESOLUTION = 128;
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

bool IsCodecResolutionLessThanDisplayResolution(Size codec_resolution, Size display_resolution) {
  return max(codec_resolution.width, codec_resolution.height) < max(display_resolution.width, display_resolution.height);
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
  jmethodID method = clazz.GetStaticMethodId("selectVideoEncoderForType",
                                             "(Ljava/lang/String;)Lcom/android/tools/screensharing/CodecInfo;");
  JObject codec_info = clazz.CallStaticObjectMethod(method, JString(jni, mime_type).ref());
  if (codec_info.IsNull()) {
    Log::Fatal("No video encoder is available for %s", mime_type.c_str());
  }
  JString jname = JString(codec_info.GetObjectField(clazz.GetFieldId("name", "Ljava/lang/String;")));
  string codec_name = jname.IsNull() ? "<unnamed>" : jname.GetValue();
  int max_width = codec_info.GetIntField(clazz.GetFieldId("maxWidth", "I"));
  int max_height = codec_info.GetIntField(clazz.GetFieldId("maxHeight", "I"));
  int width_alignment = codec_info.GetIntField(clazz.GetFieldId("widthAlignment", "I"));
  int height_alignment = codec_info.GetIntField(clazz.GetFieldId("heightAlignment", "I"));
  return new CodecInfo(mime_type, codec_name, Size(max_width, max_height), Size(width_alignment, height_alignment));
}

string GetVideoEncoderDetails(const string& codec_name, const string& mime_type, int32_t width, int32_t height) {
  Jni jni = Jvm::GetJni();
  JClass clazz = jni.GetClass("com/android/tools/screensharing/CodecInfo");
  jmethodID method = clazz.GetStaticMethodId("getVideoEncoderDetails", "(Ljava/lang/String;Ljava/lang/String;II)Ljava/lang/String;");
  Log::V("%s:%d", __FILE__, __LINE__);
  return clazz.CallStaticObjectMethod(method, JString(jni, codec_name).ref(), JString(jni, mime_type).ref(), width, height).ToString();
}

[[noreturn]] void FatalVideoEncoderError(const char* error_message, const string& codec_name, const string& mime_type,
                                         int32_t width, int32_t height) {
  Log::Fatal("%s:\n%s", error_message, GetVideoEncoderDetails(codec_name, mime_type, width, height).c_str());
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
      Log::Fatal("Error writing to video socket - %s", strerror(errno));
    }
    Agent::Shutdown();
  }
}

int32_t RoundUpToMultipleOf(int32_t value, int32_t power_of_two) {
  return (value + power_of_two - 1) & ~(power_of_two - 1);
}

Size ComputeVideoSize(Size rotated_display_size, Size max_resolution, Size size_alignment) {
  auto width = rotated_display_size.width;
  auto height = rotated_display_size.height;
  double scale = max(min(1.0, min(static_cast<double>(max_resolution.width) / width, static_cast<double>(max_resolution.height) / height)),
                     max(MIN_VIDEO_RESOLUTION / width, MIN_VIDEO_RESOLUTION / height));
  return Size { RoundUpToMultipleOf(lround(width * scale), max(size_alignment.width, 8)),
                RoundUpToMultipleOf(lround(height * scale), max(size_alignment.height, 8)) };
}

Size ConfigureCodec(AMediaCodec* codec, const CodecInfo& codec_info, Size max_video_resolution, AMediaFormat* media_format,
                    const DisplayInfo& display_info) {
  Size max_resolution = Size(min(max_video_resolution.width, codec_info.max_resolution.width),
                             min(max_video_resolution.height, codec_info.max_resolution.height));
  Size video_size = ComputeVideoSize(display_info.logical_size, max_resolution, codec_info.size_alignment);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_WIDTH, video_size.width);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_HEIGHT, video_size.height);
  media_status_t status = AMediaCodec_configure(codec, media_format, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
  if (status != AMEDIA_OK) {
    Log::Fatal("AMediaCodec_configure returned %d for video_size=%dx%d", status, video_size.width, video_size.height);
  }
  return video_size;
}

// The display area defined by display_info.logical_size is mapped to projected size.
void ConfigureDisplay(const SurfaceControl& surface_control, jobject display_token, ANativeWindow* surface, const DisplayInfo& display_info,
                      Size projected_size) {
  SurfaceControl::Transaction transaction(surface_control);
  surface_control.SetDisplaySurface(display_token, surface);
  surface_control.SetDisplayProjection(display_token, 0, display_info.logical_size.toRect(), projected_size.toRect());
  surface_control.SetDisplayLayerStack(display_token, display_info.layer_stack);
}

}  // namespace

DisplayStreamer::DisplayStreamer(int32_t display_id, string codec_name, Size max_video_resolution, int32_t initial_video_orientation,
                                 int32_t max_bit_rate, int socket_fd)
    : display_rotation_watcher_(this),
      display_id_(display_id),
      codec_name_(std::move(codec_name)),
      socket_fd_(socket_fd),
      presentation_timestamp_offset_(0),
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
  SurfaceControl surface_control(jni);
  VideoPacketHeader packet_header = {.frame_number = 1};

  bool end_of_stream = false;
  consequent_deque_error_count_ = 0;
  while (!streamer_stopped_ && !end_of_stream && !Agent::IsShuttingDown()) {
    AMediaCodec* codec = AMediaCodec_createCodecByName(codec_info_->name.c_str());
    if (codec == nullptr) {
      Log::Fatal("Unable to create a %s video encoder", codec_info_->name.c_str());
    }
    int api_level = android_get_device_api_level();
    bool secure = android_get_device_api_level() < 31;  // Creation of secure displays is not allowed on API 31+.
    JObject display = surface_control.CreateDisplay("screen-sharing-agent", secure);
    if (display.IsNull()) {
      Log::Fatal("Unable to create a virtual display");
    }
    DisplayInfo display_info = DisplayManager::GetDisplayInfo(jni, display_id_);
    Log::D("display_info: %s", display_info.ToDebugString().c_str());
    // Use heuristics for determining a bit rate value that doesn't cause SIGABRT in the encoder (b/251659422).
    int32_t bit_rate =
        api_level < 32 && IsCodecResolutionLessThanDisplayResolution(codec_info_->max_resolution, display_info.logical_size) ?
        BIT_RATE_REDUCED : BIT_RATE;
    if (max_bit_rate_ > 0 && bit_rate > max_bit_rate_) {
      bit_rate = max_bit_rate_;
    }
    AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_BIT_RATE, bit_rate);
    ANativeWindow* surface = nullptr;
    {
      scoped_lock lock(mutex_);
      display_info_ = display_info;
      int32_t rotation_correction = video_orientation_ >= 0 ? NormalizeRotation(video_orientation_ - display_info.rotation) : 0;
      Size video_size = ConfigureCodec(codec, *codec_info_, max_video_resolution_, media_format, display_info);
      Log::D("rotation_correction = %d video_size = %dx%d", rotation_correction, video_size.width, video_size.height);
      media_status_t status = AMediaCodec_createInputSurface(codec, &surface);  // Requires API 26.
      if (status != AMEDIA_OK) {
        Log::Fatal("AMediaCodec_createInputSurface returned %d", status);
      }
      ConfigureDisplay(surface_control, display, surface, display_info, video_size);
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
    surface_control.DestroyDisplay(display);
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
        FatalVideoEncoderError("Too many video encoder errors", codec_info_->name, codec_info_->mime_type,
                               packet_header->display_width, packet_header->display_height);
      }
      continue;
    }
    consequent_deque_error_count_ = 0;
    end_of_stream = codec_buffer.IsEndOfStream();
    if (!IsCodecRunning()) {
      return false;
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
    Log::V("DisplayStreamer::ProcessFramesUntilCodecStopped: writing video packet %s", packet_header->ToDebugString().c_str());
    iovec buffers[] = { { packet_header, sizeof(*packet_header) }, { codec_buffer.buffer, static_cast<size_t>(codec_buffer.info.size) } };
    if (writev(socket_fd_, buffers, 2) != buffers[0].iov_len + buffers[1].iov_len) {
      if (errno != EBADF && errno != EPIPE) {
        Log::Fatal("Error writing to video socket - %s", strerror(errno));
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
  Jni jni = Jvm::GetJni();
  bool rotation_was_frozen = WindowManager::IsRotationFrozen(jni);

  scoped_lock lock(mutex_);
  if (orientation < 0) {
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
