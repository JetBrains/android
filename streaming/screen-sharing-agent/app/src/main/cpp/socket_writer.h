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
#include <mutex>
#include <string>

#include "common.h"

namespace screensharing {

class SocketWriter {
public:
  enum class Result { SUCCESS, SUCCESS_AFTER_BLOCKING, TIMEOUT, DISCONNECTED };

  static constexpr int32_t INFINITE_TIMEOUT = -1;

  SocketWriter(int socket_fd, std::string&& socket_name, int32_t timeout_millis = INFINITE_TIMEOUT);
  SocketWriter(SocketWriter&&);

  Result Write(const void* buf, size_t size) {
    return Write(buf, size, nullptr, 0);
  }

  Result Write(const void* buf1, size_t size1, const void* buf2, size_t size2);

  int socket_fd() const { return socket_fd_; }

  void set_timeout_millis(int32_t timeout_millis) { timeout_millis_ = timeout_millis; }
  int32_t timeout_millis() const { return timeout_millis_; }

private:
  int socket_fd_ = 0;
  std::string socket_name_;
  int32_t timeout_millis_ = INFINITE_TIMEOUT;
  std::mutex mutex_;

  DISALLOW_COPY_AND_ASSIGN(SocketWriter);
};

}  // namespace screensharing
