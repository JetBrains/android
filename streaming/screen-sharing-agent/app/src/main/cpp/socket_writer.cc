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

#include "socket_writer.h"

#include <poll.h>
#include <sys/uio.h>
#include <unistd.h>

#include <cassert>
#include <cerrno>
#include <chrono>

#include "log.h"

namespace screensharing {

using namespace std;
using namespace std::chrono;

SocketWriter::SocketWriter(int socket_fd, std::string&& socket_name)
    : socket_fd_(socket_fd),
      socket_name_(socket_name) {
  assert(socket_fd > 0);
}

SocketWriter::Result SocketWriter::Write(const void* buf1, size_t size1, const void* buf2, size_t size2, int timeout_micros) {
  bool was_blocked = false;
  while (true) {
    ssize_t written;
    if (size2 == 0) {
      written = TEMP_FAILURE_RETRY(write(socket_fd_, buf1, size1));
    } else {
      iovec buffers[] = {{const_cast<void*>(buf1), size1}, {const_cast<void*>(buf2), size2}};
      written = TEMP_FAILURE_RETRY(writev(socket_fd_, buffers, 2));
    }
    if (written < 0) {
      switch (errno) {
        case EBADF:
        case EPIPE:
          Log::I("Disconnected while writing to %s socket", socket_name_.c_str());
          return Result::DISCONNECTED;

        case EAGAIN: {
          Log::W("Writing to %s socket failed - %s", socket_name_.c_str(), strerror(errno));
          was_blocked = true;
          auto poll_start = steady_clock::now();
          struct pollfd fds = {socket_fd_, POLLOUT, 0};
          int ret = poll(&fds, 1, timeout_micros);
          if (ret < 0) {
            Log::Fatal(SOCKET_IO_ERROR, "Error waiting for %s socket to start accepting data - %s", socket_name_.c_str(), strerror(errno));
          }
          if (ret == 0) {
            Log::W("Writing to %s socket timed out", socket_name_.c_str());
            return Result::TIMEOUT;
          }
          timeout_micros -= duration_cast<microseconds>(steady_clock::now() - poll_start).count();
          if (timeout_micros <= 0) {
            Log::W("Writing to %s socket timed out", socket_name_.c_str());
            return Result::TIMEOUT;
          }
          Log::W("Retrying writing to %s socket", socket_name_.c_str());
          continue;
        }

        default:
          Log::Fatal(SOCKET_IO_ERROR, "Error writing to %s socket - %s", socket_name_.c_str(), strerror(errno));
      }
    }
    if (written == size1 + size2) {
      if (was_blocked) {
        Log::I("Writing to %s socket succeeded", socket_name_.c_str());
      }
      return was_blocked ? Result::SUCCESS_AFTER_BLOCKING : Result::SUCCESS;
    }
    if (written < size1) {
      if (written == 0) {
        Log::Fatal(SOCKET_IO_ERROR, "No progress writing to %s socket - %s", socket_name_.c_str(), strerror(errno));
      }
      size1 -= written;
      buf1 = static_cast<const char*>(buf1) + written;
    } else {
      written -= size1;
      size1 = size2 - written;
      buf1 = static_cast<const char*>(buf2) + written;
      size2 = 0;
    }
  }
}

}  // namespace screensharing
