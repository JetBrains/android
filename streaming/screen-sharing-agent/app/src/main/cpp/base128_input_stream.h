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

#include <cerrno>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <string>

#include "common.h"
#include "io_exception.h"

namespace screensharing {

// An input stream that uses the unsigned little endian base 128 (<a xref="https://en.wikipedia.org/wiki/LEB128">LEB128</a>)
// variable-length encoding for integer values.
// See Base128OutputStream.
class Base128InputStream {
public:
  Base128InputStream(int socket_fd, size_t buffer_size);
  ~Base128InputStream();

  // Shuts down the socket file descriptor for reading but doesn't close it.
  void Close();
  uint8_t ReadByte();
  std::string ReadBytes();
  int16_t ReadInt16();
  uint16_t ReadUInt16() { return static_cast<uint16_t>(ReadInt16()); }
  int32_t ReadInt32();
  uint32_t ReadUInt32() { return static_cast<uint32_t>(ReadInt32()); }
  int64_t ReadInt64();
  uint64_t ReadUInt64() { return static_cast<uint64_t>(ReadInt64()); }
  bool ReadBool();
  std::unique_ptr<std::u16string> ReadString16();

  class StreamFormatException : public IoException {
  public:
    StreamFormatException(const char* message)
      : IoException(message) {
    }

    static StreamFormatException InvalidFormat();
  };

private:
  int fd_;
  uint8_t* buffer_;
  size_t buffer_capacity_;
  size_t offset_;
  size_t data_end_;

  DISALLOW_COPY_AND_ASSIGN(Base128InputStream);
};

}  // namespace screensharing
