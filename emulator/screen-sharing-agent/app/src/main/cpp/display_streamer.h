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

#include "accessors/display_manager.h"
#include "accessors/window_manager.h"
#include "common.h"
#include "geom.h"
#include "jvm.h"
#include "video_packet_header.h"

namespace screensharing {

// Processes control socket commands.
class DisplayStreamer : public DisplayManager::DisplayListener {
public:
  // The display streamer takes ownership of the socket file descriptor and closes it when destroyed.
  DisplayStreamer(int display_id, std::string codec_name, Size max_video_resolution, int initial_video_orientation, int socket_fd);
  virtual ~DisplayStreamer() = default;

  void Run();
  // Sets orientation of the device display. A negative value tells the agent to update
  // the app-level orientation according to the previously set display orientation.
  void SetVideoOrientation(int32_t orientation);

  // Sets the maximum resolution of the display video stream.
  void SetMaxVideoResolution(Size max_video_resolution);

  // Returns the cached version of DisplayInfo.
  DisplayInfo GetDisplayInfo();

  virtual void OnDisplayAdded(int32_t display_id);

  virtual void OnDisplayRemoved(int32_t display_id);

  virtual void OnDisplayChanged(int32_t display_id);

  void Shutdown();

private:
  struct DisplayRotationWatcher : public WindowManager::RotationWatcher {
    DisplayRotationWatcher(DisplayStreamer* display_streamer);
    virtual ~DisplayRotationWatcher();

    void OnRotationChanged(int rotation) override;

    DisplayStreamer* display_streamer;
    std::atomic<int32_t> display_rotation;
  };

  bool ProcessFramesUntilStopped(AMediaCodec* codec, VideoPacketHeader* packet_header, const AMediaFormat* sync_frame_request);

  void StopCodec();
  void StopCodecUnlocked();  // REQUIRES(mutex_)
  bool IsCodecRunning();

  DisplayRotationWatcher display_rotation_watcher_;
  int display_id_;
  std::string codec_name_;
  int socket_fd_;
  int64_t presentation_timestamp_offset_;
  std::atomic<bool> stopped_;

  std::mutex mutex_;
  DisplayInfo display_info_;  // GUARDED_BY(mutex_)
  Size max_video_resolution_;  // GUARDED_BY(mutex_)
  int32_t video_orientation_;   // GUARDED_BY(mutex_)
  AMediaCodec* running_codec_;  // GUARDED_BY(mutex_)

  DISALLOW_COPY_AND_ASSIGN(DisplayStreamer);
};

}  // namespace screensharing
