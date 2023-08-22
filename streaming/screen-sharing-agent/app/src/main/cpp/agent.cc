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

#include "agent.h"

#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/un.h>
#include <unistd.h>

#include <cassert>
#include <chrono>
#include <cstdlib>

#include "accessors/service_manager.h"
#include "flags.h"
#include "log.h"
#include "session_environment.h"

namespace screensharing {

using namespace std;
using namespace std::chrono;

namespace {

int CreateAndConnectSocket(const string& socket_name) {
  int socket_fd = socket(AF_UNIX, SOCK_STREAM, 0);
  if (socket_fd < 0) {
    Log::Fatal(SOCKET_CONNECTIVITY_ERROR, "Failed to create a socket");
  }
  sockaddr_un address = { AF_UNIX, "" };
  // An abstract socket address is distinguished by a null byte in front of the socket name
  // and doesn't need a null terminator. See https://man7.org/linux/man-pages/man7/unix.7.html.
  if (socket_name.size() > sizeof(address.sun_path) - 2) {
    Log::Fatal(SOCKET_CONNECTIVITY_ERROR, "Socket name \"%s\" is too long", socket_name.c_str());
  }
  strncpy(address.sun_path + 1, socket_name.c_str(), sizeof(address.sun_path) - 2);
  int len = sizeof(sa_family_t) + 1 + socket_name.size();
  int ret = connect(socket_fd, (const struct sockaddr*) &address, len);
  if (ret < 0) {
    close(socket_fd);
    Log::Fatal(SOCKET_CONNECTIVITY_ERROR, "Failed to connect to socket \"%s\" - %s", socket_name.c_str(), strerror(errno));
  }
  return socket_fd;
}

[[noreturn]] void InvalidCommandLineArgument(const string& arg) {
  Log::Fatal(INVALID_COMMAND_LINE, "Invalid command line argument: \"%s\"", arg.c_str());
}

void sighup_handler(int signal_number) {
  Agent::Shutdown();
}

}  // namespace

void Agent::Initialize(const vector<string>& args) {
  for (int i = 1; i < args.size(); i++) {
    const string& arg = args[i];
    if (arg.rfind("--socket=", 0) == 0) {
      socket_name_ = arg.substr(sizeof("--socket=") - 1, arg.size());
    } else if (arg.rfind("--log=", 0) == 0) {
      auto value = arg.substr(sizeof("--log=") - 1, arg.size());
      if (value == "verbose") {
        Log::SetLevel(Log::Level::VERBOSE);
      } else if (value == "debug") {
        Log::SetLevel(Log::Level::DEBUG);
      } else if (value == "info") {
        Log::SetLevel(Log::Level::INFO);
      } else if (value == "warn") {
        Log::SetLevel(Log::Level::WARN);
      } else if (value == "error") {
        Log::SetLevel(Log::Level::ERROR);
      } else {
        InvalidCommandLineArgument(arg);
      }
    } else if (arg.rfind("--max_size=", 0) == 0) {
      char* ptr;
      auto w = strtoul(arg.c_str() + sizeof("--max_size=") - 1, &ptr, 10);
      auto h = *ptr == ',' ? strtoul(ptr + 1, &ptr, 10) : 0;
      if (*ptr != '\0' || w <= 0 || numeric_limits<int32_t>::max() < w || h <= 0 || numeric_limits<int32_t>::max() < h) {
        InvalidCommandLineArgument(arg);
      }
      max_video_resolution_.width = w;
      max_video_resolution_.height = h;
    } else if (arg.rfind("--orientation=", 0) == 0) {
      char* ptr;
      auto orientation = strtoul(arg.c_str() + sizeof("--orientation=") - 1, &ptr, 10);
      if (*ptr != '\0') {
        InvalidCommandLineArgument(arg);
      }
      initial_video_orientation_ = orientation & 0x03;
    } else if (arg.rfind("--flags=", 0) == 0) {
      char* ptr;
      flags_ = strtoul(arg.c_str() + sizeof("--flags=") - 1, &ptr, 10);
      if (*ptr != '\0') {
        InvalidCommandLineArgument(arg);
      }
    } else if (arg.rfind("--max_bit_rate=", 0) == 0) {
      char* ptr;
      max_bit_rate_ = strtoul(arg.c_str() + sizeof("--max_bit_rate=") - 1, &ptr, 10);
      if (*ptr != '\0') {
        InvalidCommandLineArgument(arg);
      }
    } else if (arg.rfind("--codec=", 0) == 0) {
      codec_name_ = arg.substr(sizeof("--codec=") - 1, arg.size());
    } else if (!arg.empty()) {  // For some unclear reason some command line arguments are empty strings.
      InvalidCommandLineArgument(arg);
    }
  }

  api_level_ = android_get_device_api_level();
}

void Agent::Run(const vector<string>& args) {
  Initialize(args);

  struct sigaction action = { .sa_handler = sighup_handler };
  int res = sigaction(SIGHUP, &action, nullptr);
  if (res < 0) {
    Log::E("Unable to set SIGHUP handler - sigaction returned %d", res);
  }

  assert(display_streamers_.empty());
  video_socket_fd_ = CreateAndConnectSocket(socket_name_);
  control_socket_fd_ = CreateAndConnectSocket(socket_name_);
  auto ret = display_streamers_.try_emplace(
      PRIMARY_DISPLAY_ID,
      PRIMARY_DISPLAY_ID, codec_name_, max_video_resolution_, initial_video_orientation_, max_bit_rate_, video_socket_fd_);
  primary_display_streamer_ = &ret.first->second;
  controller_ = new Controller(control_socket_fd_);
  Log::D("Created video and control sockets");
  if ((flags_ & START_VIDEO_STREAM) != 0) {
    primary_display_streamer_->Start();
  }
  controller_->Run();
  Shutdown();
}

void Agent::StartVideoStream(int32_t display_id, Size max_video_resolution) {
  DisplayStreamer* display_streamer;
  bool created;
  if (display_id == PRIMARY_DISPLAY_ID) {
    display_streamer = primary_display_streamer_;
    created = false;
  } else {
    auto ret = display_streamers_.try_emplace(
        PRIMARY_DISPLAY_ID,
        PRIMARY_DISPLAY_ID, codec_name_, max_video_resolution, DisplayStreamer::CURRENT_DISPLAY_ORIENTATION,
        primary_display_streamer_->bit_rate(), video_socket_fd_);
    display_streamer = &ret.first->second;
    created = ret.second;
  }
  if (!created) {
    display_streamer->SetMaxVideoResolution(max_video_resolution);
  }
  display_streamer->Start();
}

void Agent::StopVideoStream(int32_t display_id) {
  auto it = display_streamers_.find(display_id);
  if (it != display_streamers_.end()) {
    DisplayStreamer& display_streamer = it->second;
    display_streamer.Stop();
    if (display_id != PRIMARY_DISPLAY_ID) {
      display_streamers_.erase(it);
    }
  }
}

void Agent::SetVideoOrientation(int32_t display_id, int32_t orientation) {
  auto it = display_streamers_.find(display_id);
  if (it != display_streamers_.end()) {
    DisplayStreamer& display_streamer = it->second;
    display_streamer.SetVideoOrientation(orientation);
  }
}

void Agent::SetVideoOrientationOfInternalDisplays(int32_t orientation) {
  for (auto& it : display_streamers_) {
    DisplayInfo display_info = GetDisplayInfo(it.first);
    if (display_info.type == DisplayInfo::TYPE_INTERNAL) {
      DisplayStreamer& display_streamer = it.second;
      display_streamer.SetVideoOrientation(orientation);
    }
  }
}

void Agent::SetMaxVideoResolution(int32_t display_id, Size max_video_resolution) {
  auto it = display_streamers_.find(display_id);
  if (it != display_streamers_.end()) {
    DisplayStreamer& display_streamer = it->second;
    display_streamer.SetMaxVideoResolution(max_video_resolution);
  }
}

DisplayInfo Agent::GetDisplayInfo(int32_t display_id) {
  auto it = display_streamers_.find(display_id);
  if (it != display_streamers_.end()) {
    DisplayStreamer& display_streamer = it->second;
    return display_streamer.GetDisplayInfo();
  }
  return DisplayManager::GetDisplayInfo(Jvm::GetJni(), display_id);
}

void Agent::InitializeSessionEnvironment() {
  ServiceManager::GetService(Jvm::GetJni(), "settings");  // Wait for the "settings" service to initialize.
  scoped_lock lock(environment_mutex_);
  session_environment_ = new SessionEnvironment((flags_ & TURN_OFF_DISPLAY_WHILE_MIRRORING) != 0);
}

void Agent::RestoreEnvironment() {
  scoped_lock lock(environment_mutex_);
  delete session_environment_;
  session_environment_ = nullptr;
}

void Agent::Shutdown() {
  if (!shutting_down_.exchange(true)) {
    if (controller_ != nullptr) {
      controller_->Stop();
    }
    close(control_socket_fd_);
    for (auto& it : display_streamers_) {
      it.second.Stop();
    }
    close(video_socket_fd_);
    RestoreEnvironment();
  }
}

int64_t Agent::GetLastTouchEventTime() {
  return last_touch_time_millis_.load();
}

void Agent::RecordTouchEvent() {
  last_touch_time_millis_.store(duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count());
}

int32_t Agent::api_level_(0);
string Agent::socket_name_("screen-sharing-agent");
Size Agent::max_video_resolution_(numeric_limits<int32_t>::max(), numeric_limits<int32_t>::max());
int32_t Agent::initial_video_orientation_(-1);
int32_t Agent::max_bit_rate_(0);
string Agent::codec_name_("vp8");
int32_t Agent::flags_(0);
int Agent::video_socket_fd_(0);
int Agent::control_socket_fd_(0);
std::map<int32_t, DisplayStreamer> Agent::display_streamers_;
DisplayStreamer* Agent::primary_display_streamer_(nullptr);
Controller* Agent::controller_(nullptr);
mutex Agent::environment_mutex_;
SessionEnvironment* Agent::session_environment_(nullptr);  // GUARDED_BY(environment_mutex_)
atomic_int64_t Agent::last_touch_time_millis_(0);
atomic_bool Agent::shutting_down_(false);

}  // namespace screensharing
