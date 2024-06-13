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

class SocketWriter {
public:
  enum class Result { SUCCESS, SUCCESS_AFTER_BLOCKING, TIMEOUT, DISCONNECTED };

  SocketWriter(int socket_fd, std::string&& socket_name);

  Result Write(const void* buf, size_t size, int timeout_micros) {
    return Write(buf, size, nullptr, 0, timeout_micros);
  }

  Result Write(const void* buf1, size_t size1, const void* buf2, size_t size2, int timeout_micros);

  int socket_fd() const { return socket_fd_; }

private:
  int socket_fd_;
  std::string socket_name_;
};

}  // namespace screensharing
