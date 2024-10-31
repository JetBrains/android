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

#pragma once

#include <cstddef>
#include <string>

#include "common.h"

namespace screensharing {

class SocketReader {
public:
  enum class Status { SUCCESS, TIMEOUT, IO_ERROR, DISCONNECTED };

  struct Result {
    explicit Result(Status status, size_t bytes_read = 0)
        : status(status),
          error_code(status == Status::SUCCESS ? 0 : errno),
          bytes_read(bytes_read) {
    }

    Status status;
    int error_code;  // Contains the value of errno if status != Status::SUCCESS.
    size_t bytes_read; // Guaranteed to be positive if status == Status::SUCCESS.
  };

  static constexpr int32_t INFINITE_TIMEOUT = -1;

  SocketReader(int socket_fd, int32_t timeout_millis = INFINITE_TIMEOUT);
  SocketReader(SocketReader&& other);

  Result Read(void* buf, size_t size);

  int socket_fd() const { return socket_fd_; }

  void set_timeout_millis(int32_t timeout_millis) { timeout_millis_ = timeout_millis; }
  int32_t timeout_millis() const { return timeout_millis_; }

private:
  int socket_fd_;
  int32_t timeout_millis_;

  DISALLOW_COPY_AND_ASSIGN(SocketReader);
};

}  // namespace screensharing
