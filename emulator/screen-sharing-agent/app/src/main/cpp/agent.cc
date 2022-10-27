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

int CreateAndConnectSocket(const char* socket_name) {
  int socket_fd = socket(AF_UNIX, SOCK_STREAM, 0);
  if (socket_fd < 0) {
    Log::Fatal("Failed to create a socket");
  }
  sockaddr_un address = { AF_UNIX, "" };
  // An abstract socket address is distinguished by a null byte in front of the socket name
  // and doesn't need a null terminator. See https://man7.org/linux/man-pages/man7/unix.7.html.
  strncpy(address.sun_path + 1, socket_name, sizeof(address.sun_path) - 2);
  int len = sizeof(sa_family_t) + 1 + strlen(socket_name);
  int ret = connect(socket_fd, (const struct sockaddr*) &address, len);
  if (ret < 0) {
    close(socket_fd);
    Log::Fatal("Failed to connect to socket \"%s\" - %s", socket_name, strerror(errno));
  }
  return socket_fd;
}

[[noreturn]] void InvalidCommandLineArgument(const string& arg) {
  Log::Fatal("Invalid command line argument: \"%s\"", arg.c_str());
}

void sighup_handler(int signal_number) {
  Agent::Shutdown();
}

}  // namespace

Agent::Agent(const vector<string>& args) {
  for (int i = 1; i < args.size(); i++) {
    const string& arg = args[i];
    if (arg.rfind("--log=", 0) == 0) {
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
    } else if (arg.rfind("--codec=", 0) == 0) {
      codec_name_ = arg.substr(sizeof("--codec=") - 1, arg.size());
    } else if (!arg.empty()) {  // For some unclear reason some command line arguments are empty strings.
      InvalidCommandLineArgument(arg);
    }
  }
}

Agent::~Agent() = default;

void Agent::Run() {
  struct sigaction action;
  memset(&action, 0, sizeof(action));
  action.sa_handler = sighup_handler;
  int res = sigaction(SIGHUP, &action, nullptr);
  if (res < 0) {
    Log::D("Unable to set SIGHUP handler - sigaction returned %d", res);
  }

  display_streamer_ = new DisplayStreamer(
      display_id_, codec_name_, max_video_resolution_, initial_video_orientation_, CreateAndConnectSocket(SOCKET_NAME));
  controller_ = new Controller(CreateAndConnectSocket(SOCKET_NAME));
  Log::D("Created video and control sockets");
  controller_->Start();
  display_streamer_->Run();
}

void Agent::SetVideoOrientation(int32_t orientation) {
  display_streamer_->SetVideoOrientation(orientation);
}

void Agent::SetMaxVideoResolution(Size max_video_resolution) {
  max_video_resolution_ = max_video_resolution;
  display_streamer_->SetMaxVideoResolution(max_video_resolution);
}

DisplayInfo Agent::GetDisplayInfo() {
  if (display_streamer_ == nullptr) {
    Log::Fatal("Display information has not been obtained yet");
  }
  return display_streamer_->GetDisplayInfo();
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
      controller_->Shutdown();
    }
    if (display_streamer_ != nullptr) {
      display_streamer_->Shutdown();
    }
    RestoreEnvironment();
  }
}

int64_t Agent::GetLastTouchEventTime() {
  return last_touch_time_millis_.load();
}

void Agent::RecordTouchEvent() {
  last_touch_time_millis_.store(duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count());
}

int32_t Agent::display_id_(0);
Size Agent::max_video_resolution_(numeric_limits<int32_t>::max(), numeric_limits<int32_t>::max());
int32_t Agent::initial_video_orientation_(-1);
string Agent::codec_name_("vp8");
int32_t Agent::flags_(0);
DisplayStreamer* Agent::display_streamer_(nullptr);
Controller* Agent::controller_(nullptr);
mutex Agent::environment_mutex_;
SessionEnvironment* Agent::session_environment_(nullptr);  // GUARDED_BY(environment_mutex_)
atomic_int64_t Agent::last_touch_time_millis_(0);
atomic_bool Agent::shutting_down_(false);

}  // namespace screensharing
