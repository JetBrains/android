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

#include <map>
#include <string>
#include <vector>

#include "audio_streamer.h"
#include "controller.h"
#include "display_streamer.h"
#include "session_environment.h"

namespace screensharing {

extern const char ATTRIBUTION_TAG[];  // The tag used in system logs.
constexpr int32_t PRIMARY_DISPLAY_ID = 0;

// The main class of the screen sharing agent.
class Agent {
public:
  static void Run(const std::vector<std::string>& args);

  static void StartVideoStream(int32_t display_id, Size max_video_resolution);
  static void StopVideoStream(int32_t display_id);
  static void StartAudioStream();
  static void StopAudioStream();

  static void Shutdown();

  // Calls DisplayStreamer::SetVideoOrientation.
  static void SetVideoOrientation(int32_t display_id, int32_t orientation);
  // Calls DisplayStreamer::SetMaxVideoResolution.
  static void SetMaxVideoResolution(int32_t display_id, Size max_video_resolution);
  // Calls DisplayStreamer::GetDisplayInfo.
  [[nodiscard]] static DisplayInfo GetDisplayInfo(int32_t display_id);

  // Modifies system settings for the screen sharing session. May be called on any thread.
  static void InitializeSessionEnvironment();
  // Restores the original environment that existed before calling InitializeSessionEnvironment.
  // May be called on any thread. Safe to be called multiple times.
  static void RestoreEnvironment();

  // Returns the timestamp of the end of last simulated touch event in milliseconds according to the monotonic clock.
  [[nodiscard]] static int64_t GetLastTouchEventTime();
  // Records the timestamp of the last simulated touch event in milliseconds according to the monotonic clock.
  static void RecordTouchEvent();

  [[nodiscard]] static bool IsShuttingDown() { return shutting_down_; }

  [[nodiscard]] static bool is_watch() { return is_watch_; };

  [[nodiscard]] static const std::string& device_manufacturer();

  [[nodiscard]] static int32_t flags() { return flags_; }

  [[nodiscard]] inline static int32_t feature_level() { return feature_level_; }

  [[nodiscard]] static SessionEnvironment& session_environment() { return *session_environment_; }

  Agent() = delete;

private:
  static void Initialize(const std::vector<std::string>& args);

  static int32_t feature_level_;
  static bool is_watch_;
  static std::string device_manufacturer_;
  static std::string socket_name_;
  static Size max_video_resolution_;
  static int32_t max_bit_rate_;  // Zero means no limit.
  static int32_t initial_video_orientation_;  // In quadrants counterclockwise.
  static std::string codec_name_;
  static CodecInfo* codec_info_;
  static int32_t flags_;
  static int video_socket_fd_;
  static int audio_socket_fd_;
  static int control_socket_fd_;
  static std::map<int32_t, DisplayStreamer> display_streamers_;
  static DisplayStreamer* primary_display_streamer_;
  static AudioStreamer* audio_streamer_;
  static Controller* controller_;
  static std::mutex environment_mutex_;
  static SessionEnvironment* session_environment_;  // GUARDED_BY(environment_mutex_)
  static std::atomic_int64_t last_touch_time_millis_;
  static std::atomic_bool shutting_down_;
};

}  // namespace screensharing
