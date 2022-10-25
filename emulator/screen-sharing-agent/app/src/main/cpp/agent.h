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

#include <string>
#include <vector>

#include "common.h"
#include "controller.h"
#include "display_streamer.h"

namespace screensharing {

// The main class of the screen sharing agent.
class Agent {
public:
  Agent(const std::vector<std::string>& args);
  ~Agent();

  void Run();

  // Sets orientation of the device display. A negative value tells the agent to update
  // the app-level orientation according to the previously set display orientation.
  static void SetVideoOrientation(int32_t orientation);
  static void SetMaxVideoResolution(Size max_video_resolution);
  static DisplayInfo GetDisplayInfo();

  static void Shutdown();

  // Returns the timestamp of the end of last simulated touch event in milliseconds according to the monotonic clock.
  static int64_t GetLastTouchEventTime();
  // Records the timestamp of the last simulated touch event in milliseconds according to the monotonic clock.
  static void RecordTouchEvent();

  static int32_t flags() {
    return instance_->flags_;
  }

private:
  void ShutdownInternal();

  static constexpr char SOCKET_NAME[] = "screen-sharing-agent";

  static Agent* instance_;

  int32_t display_id_ = 0;
  Size max_video_resolution_;
  int32_t initial_video_orientation_;  // In quadrants counterclockwise.
  std::string codec_name_;
  int32_t flags_ = 0;
  DisplayStreamer* display_streamer_ = nullptr;
  Controller* controller_ = nullptr;
  std::atomic_int64_t last_touch_time_millis_ = 0;

  DISALLOW_COPY_AND_ASSIGN(Agent);
};

}  // namespace screensharing
