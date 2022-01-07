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

#include "accessors/window_manager.h"
#include "common.h"
#include "geom.h"
#include "jvm.h"
#include "video_packet_header.h"

namespace screensharing {

// Processes control socket commands.
class DisplayStreamer {
public:
  DisplayStreamer(int display_id, Size max_video_resolution, int socket_fd);

  void Run();
  void OnVideoOrientationChanged(int32_t orientation);
  void OnMaxVideoResolutionChanged(Size max_video_resolution);
  void Shutdown();

private:
  struct DisplayRotationWatcher : public WindowManager::RotationWatcher {
    DisplayRotationWatcher(DisplayStreamer* display_streamer);
    virtual ~DisplayRotationWatcher();

    void OnRotationChanged(int rotation) override;

    DisplayStreamer* display_streamer;
    std::atomic<int32_t> display_rotation;
  };

  bool ProcessFramesUntilStopped(AMediaCodec* codec, VideoPacketHeader* packet_header);

  void StopCodec();
  void StopCodecUnlocked();  // REQUIRES(mutex_)
  bool IsCodecRunning();

  int display_id_;
  Size max_video_resolution_;
  int socket_fd_;
  int64_t presentation_timestamp_offset_;
  DisplayRotationWatcher display_rotation_watcher_;
  std::atomic<bool> stopped_;

  std::mutex mutex_;
  int32_t video_orientation_;   // GUARDED_BY(mutex_)
  AMediaCodec* running_codec_;  // GUARDED_BY(mutex_)

  friend struct DisplayRotationWatcher;

  DISALLOW_COPY_AND_ASSIGN(DisplayStreamer);
};

}  // namespace screensharing
