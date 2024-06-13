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

#pragma once

#include <android/native_window.h>
#include <media/NdkMediaCodec.h>

#include <atomic>
#include <mutex>
#include <thread>

#include "accessors/display_manager.h"
#include "accessors/window_manager.h"
#include "common.h"
#include "geom.h"
#include "jvm.h"
#include "socket_writer.h"
#include "video_packet_header.h"

namespace screensharing {

struct CodecInfo {
  std::string mime_type;
  std::string name;
  Size max_resolution;
  Size size_alignment;
  int32_t max_frame_rate;

  CodecInfo(std::string mime_type, std::string name, Size max_resolution, Size size_alignment, int32_t max_frame_rate)
      : mime_type(std::move(mime_type)),
        name(std::move(name)),
        max_resolution(max_resolution),
        size_alignment(size_alignment),
        max_frame_rate(max_frame_rate) {
  }
};

// Processes control socket commands.
class DisplayStreamer : private DisplayManager::DisplayListener {
public:
  enum OrientationReset {
    CURRENT_VIDEO_ORIENTATION = -1, CURRENT_DISPLAY_ORIENTATION = -2
  };

  DisplayStreamer(
      int display_id, const CodecInfo* codec_name, Size max_video_resolution, int initial_video_orientation, int max_bitrate,
      int socket_fd);
  virtual ~DisplayStreamer();

  // Starts the streamer's thread.
  void Start();
  // Stops the streamer. Waits for the streamer's thread to terminate.
  void Stop();

  // Sets orientation of the device display. The orientation parameter may have a negative value
  // equal to one of the OrientationReset values.
  void SetVideoOrientation(int32_t orientation);
  // Sets the maximum resolution of the display video stream.
  void SetMaxVideoResolution(Size max_video_resolution);
  // Returns the cached version of DisplayInfo.
  [[nodiscard]] DisplayInfo GetDisplayInfo();

  [[nodiscard]] const CodecInfo* codec_info() const { return codec_info_; }
  [[nodiscard]] int32_t bit_rate() const { return bit_rate_; }

private:
  struct DisplayRotationWatcher : public WindowManager::RotationWatcher {
    explicit DisplayRotationWatcher(DisplayStreamer* display_streamer);

    void OnRotationChanged(int rotation) override;

    DisplayStreamer* display_streamer;
    std::atomic_int32_t display_rotation;

    DISALLOW_COPY_AND_ASSIGN(DisplayRotationWatcher);
  };

  void Run();
  // Returns true if the streaming should continue, otherwise false.
  bool ProcessFramesUntilCodecStopped(VideoPacketHeader* packet_header, const AMediaFormat* sync_frame_request);
  void CreateCodec();
  // Deletes the codec if it was created. The codec should not be running when this method is called. Safe to call multiple times.
  void DeleteCodec();
  void StartCodecUnlocked();  // REQUIRES(mutex_)
  // Stops the codec before deleting if it is running. Safe to call multiple times.
  void StopCodec();
  void StopCodecUnlocked();  // REQUIRES(mutex_)
  bool IsCodecRunning();
  // Returns true if the bit rate was deduced, false if it already reached allowed minimum.
  bool ReduceBitRate();
  // Deletes the underlying OS display if the virtual_display_ or display_token_ refer to it.
  // Safe to call multiple times.
  void ReleaseVirtualDisplay(Jni jni);

  void OnDisplayAdded(int32_t display_id) override;
  void OnDisplayRemoved(int32_t display_id) override;
  void OnDisplayChanged(int32_t display_id) override;

  std::thread thread_;
  DisplayRotationWatcher display_rotation_watcher_;
  int display_id_;
  const CodecInfo* codec_info_ = nullptr;  // Not owned.
  SocketWriter writer_;
  int64_t presentation_timestamp_offset_ = 0;
  int32_t bit_rate_;
  bool bit_rate_reduced_ = false;
  int32_t consequent_deque_error_count_ = 0;
  std::atomic_bool streamer_stopped_ = true;
  VirtualDisplay virtual_display_;
  JObject display_token_;

  AMediaCodec* codec_ = nullptr;
  std::recursive_mutex mutex_;
  DisplayInfo display_info_;  // GUARDED_BY(mutex_)
  Size max_video_resolution_;  // GUARDED_BY(mutex_)
  int32_t video_orientation_;  // GUARDED_BY(mutex_)
  bool codec_running_ = false;  // GUARDED_BY(mutex_)
  bool codec_stop_pending_ = false;  // GUARDED_BY(mutex_)

  DISALLOW_COPY_AND_ASSIGN(DisplayStreamer);
};

}  // namespace screensharing
