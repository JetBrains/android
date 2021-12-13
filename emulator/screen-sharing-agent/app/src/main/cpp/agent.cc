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

}  // namespace

Agent::Agent(const vector<string>& args) {
  assert(instance_ == nullptr);
  instance_ = this;
  if (args.size() > 1 && args[1] == "--log=debug") {
    Log::SetLevel(Log::Level::DEBUG);
  }
}

Agent::~Agent() {
  Log::I("Screen sharing agent is stopping");
  StopActivity();
}

void Agent::Run() {
  CreateAndConnectSocket(SOCKET_NAME);
  // TODO: Implement.
}

void Agent::Stop() {
  if (instance_ != nullptr) {
    instance_->StopActivity();
  }
  Log::I("Screen sharing agent is stopping");
  exit(EXIT_SUCCESS);
}

void Agent::StopActivity() {
  // TODO: Implement.
}

Agent* Agent::instance_ = nullptr;

}  // namespace screensharing
