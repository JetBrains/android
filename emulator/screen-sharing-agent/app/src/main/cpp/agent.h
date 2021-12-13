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
#include <thread>
#include <vector>

#include "common.h"

namespace screensharing {

// The main class of the screen sharing agent.
class Agent {
public:
  Agent(const std::vector<std::string>& args);
  ~Agent();

  void Run();

  static void OnVideoOrientationChanged(int32_t orientation);

  [[noreturn]] static void Stop();

private:
  void StopActivity();

  static constexpr char SOCKET_NAME[] = "screen-sharing-agent";

  static Agent* instance_;

  int32_t display_id_ = 0;
  int32_t max_video_resolution_ = std::numeric_limits<int32_t>::max();

  DISALLOW_COPY_AND_ASSIGN(Agent);
};

}  // namespace screensharing
