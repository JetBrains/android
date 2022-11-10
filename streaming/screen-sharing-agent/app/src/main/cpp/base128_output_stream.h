/*
 * Copyright (C) 2022 The Android Open Source Project
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

namespace screensharing {

// An output stream that uses the unsigned little endian base 128 (<a xref="https://en.wikipedia.org/wiki/LEB128">LEB128</a>)
// variable-length encoding for integer values.
// See Base128InputStream.
class Base128OutputStream {
public:
  Base128OutputStream(int fd, size_t buffer_size);
  ~Base128OutputStream();

  // Shuts down the socket file descriptor for writing but doesn't close it.
  void Close();
  void Flush();
  void WriteByte(uint8_t byte);
  void WriteBytes(const std::string& bytes);
  void WriteUInt16(uint16_t value);
  void WriteInt16(int16_t value) { WriteUInt16(value); }
  void WriteUInt32(uint32_t value);
  void WriteInt32(int32_t value) { WriteUInt32(value); }
  void WriteUInt64(uint64_t value);
  void WriteInt64(int64_t value) { WriteUInt64(value); }
  void WriteBool(bool value);

private:
  int fd_;
  uint8_t* buffer_;
  size_t buffer_capacity_;
  size_t offset_;

  DISALLOW_COPY_AND_ASSIGN(Base128OutputStream);
};

}  // namespace screensharing
