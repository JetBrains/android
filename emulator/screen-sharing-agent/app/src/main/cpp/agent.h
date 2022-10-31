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
#include "session_environment.h"

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

  // Modifies system settings for the screen sharing session. May be called on any thread.
  static void InitializeSessionEnvironment();
  // Restores the original environment that existed before calling InitializeSessionEnvironment.
  // May be called on any thread. Safe to be called multiple times.
  static void RestoreEnvironment();

  static void Shutdown();

  // Returns the timestamp of the end of last simulated touch event in milliseconds according to the monotonic clock.
  static int64_t GetLastTouchEventTime();
  // Records the timestamp of the last simulated touch event in milliseconds according to the monotonic clock.
  static void RecordTouchEvent();

  static int32_t flags() {
    return flags_;
  }

private:
  static constexpr char SOCKET_NAME[] = "screen-sharing-agent";

  static int32_t display_id_;
  static Size max_video_resolution_;
  static int32_t max_bit_rate_;  // Zero means no limit.
  static int32_t initial_video_orientation_;  // In quadrants counterclockwise.
  static std::string codec_name_;
  static int32_t flags_;
  static DisplayStreamer* display_streamer_;
  static Controller* controller_;
  static std::mutex environment_mutex_;
  static SessionEnvironment* session_environment_;  // GUARDED_BY(environment_mutex_)
  static std::atomic_int64_t last_touch_time_millis_;
  static std::atomic_bool shutting_down_;

  DISALLOW_COPY_AND_ASSIGN(Agent);
};

}  // namespace screensharing
