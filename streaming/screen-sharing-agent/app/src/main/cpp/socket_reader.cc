/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include "socket_reader.h"

#include <poll.h>
#include <sys/uio.h>
#include <unistd.h>

#include <cassert>
#include <cerrno>
#include <chrono>

namespace screensharing {

using namespace std;
using namespace std::chrono;

SocketReader::SocketReader(int socket_fd, int32_t timeout_millis)
    : socket_fd_(socket_fd),
      timeout_millis_(timeout_millis) {
  assert(socket_fd > 0);
}

SocketReader::SocketReader(SocketReader&& other)
    : socket_fd_(other.socket_fd_),
      timeout_millis_(other.timeout_millis_) {
  other.socket_fd_ = -1;
}

SocketReader::Result SocketReader::Read(void* buf, size_t size) {
  auto remaining_time_millis = timeout_millis_;
  while (true) {
    ssize_t bytes_read = TEMP_FAILURE_RETRY(read(socket_fd_, buf, size));
    if (bytes_read <= 0) {
      switch (errno) {
        case 0:
        case EBADF:
        case EPIPE:
        case ENOENT:
          return Result(Status::DISCONNECTED);

        case EAGAIN: {
          auto poll_start = steady_clock::now();
          struct pollfd fds = {socket_fd_, POLLIN, 0};
          int ret = poll(&fds, 1, remaining_time_millis);
          if (ret == 0) {
            return Result(Status::TIMEOUT);
          }
          if (ret < 0 && errno != EINTR) {
            return Result(Status::DISCONNECTED);
          }
          remaining_time_millis -= duration_cast<milliseconds>(steady_clock::now() - poll_start).count();
          if (remaining_time_millis <= 0) {
            return Result(Status::TIMEOUT);
          }
          continue;
        }

        default:
          return Result(Status::IO_ERROR);
      }
    }
    return Result(Status::SUCCESS, static_cast<size_t>(bytes_read));
  }
}

}  // namespace screensharing
