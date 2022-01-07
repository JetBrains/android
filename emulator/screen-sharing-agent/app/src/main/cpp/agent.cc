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

#include <cstdlib>

#include "log.h"

using namespace std;

namespace screensharing {

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

}  // namespace

Agent::Agent(const vector<string>& args)
    : max_video_resolution_(Size(numeric_limits<int32_t>::max(), numeric_limits<int32_t>::max())) {
  assert(instance_ == nullptr);
  instance_ = this;

  for (int i = 1; i < args.size(); i++) {
    auto arg = args[i];
    if (arg.rfind("--log=", 0) == 0) {
      auto value = arg.substr(sizeof("--log=") - 1, arg.size());
      if (value == "debug") {
        Log::SetLevel(Log::Level::DEBUG);
      } else if (value == "info") {
        Log::SetLevel(Log::Level::INFO);
      } else {
        InvalidCommandLineArgument(arg);
      }
    } else if (arg.rfind("--max_size=", 0) == 0) {
      char* ptr;
      auto w = strtoul(arg.c_str() + sizeof("--max_size=") - 1, &ptr, 10);
      auto h = *ptr == ',' ? strtoul(ptr + 1, &ptr, 10) : 0;
      if (*ptr == '\0' && 0 < w && w <= numeric_limits<int32_t>::max() && 0 < h && h <= numeric_limits<int32_t>::max()) {
        max_video_resolution_.width = w;
        max_video_resolution_.height = h;
      } else {
        InvalidCommandLineArgument(arg);
      }
    } else {
      InvalidCommandLineArgument(arg);
    }
  }
}

Agent::~Agent() {
  Log::I("Screen sharing agent is stopping");
  delete controller_;
  delete display_streamer_;
}

void Agent::Run() {
  display_streamer_ = new DisplayStreamer(display_id_, max_video_resolution_, CreateAndConnectSocket(SOCKET_NAME));
  controller_ = new Controller(CreateAndConnectSocket(SOCKET_NAME));
  Log::D("Created video and control sockets");
  controller_->Start();
  display_streamer_->Run();
}

void Agent::OnVideoOrientationChanged(int32_t orientation) {
  if (instance_ != nullptr) {
    instance_->display_streamer_->OnVideoOrientationChanged(orientation);
  }
}

void Agent::OnMaxVideoResolutionChanged(Size max_video_resolution) {
  if (instance_ != nullptr) {
    instance_->max_video_resolution_ = max_video_resolution;
    instance_->display_streamer_->OnMaxVideoResolutionChanged(max_video_resolution);
  }
}

void Agent::Shutdown() {
  if (instance_ != nullptr) {
    instance_->ShutdownInternal();
  }
}

void Agent::ShutdownInternal() {
  if (controller_ != nullptr) {
    controller_->Shutdown();
  }
  if (display_streamer_ != nullptr) {
    display_streamer_->Shutdown();
  }
}

Agent* Agent::instance_ = nullptr;

}  // namespace screensharing
