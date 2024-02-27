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

#include "base128_output_stream.h"

#include <unistd.h>
#include <sys/socket.h>

#include <cassert>
#include <memory>

#include "io_exception.h"
#include "log.h"

namespace screensharing {

using namespace std;

Base128OutputStream::Base128OutputStream(SocketWriter&& writer, size_t buffer_size)
    : writer_(writer),
      buffer_(new uint8_t[buffer_size]),
      buffer_capacity_(buffer_size),
      offset_(0) {
}

Base128OutputStream::~Base128OutputStream() {
  Close();
  delete[] buffer_;
}

void Base128OutputStream::Close() {
  try {
    Flush();
  } catch (const IoException& e) {
    Log::E("Unable to flush Base128OutputStream");
  }
  shutdown(writer_.socket_fd(), SHUT_WR);
}

void Base128OutputStream::Flush() {
  if (offset_ > 0) {
    auto res = writer_.Write(buffer_, offset_, /*timeout_micros=*/10000000);
    if (res == SocketWriter::Result::DISCONNECTED) {
      throw EndOfFile();
    }
    if (res == SocketWriter::Result::TIMEOUT) {
      throw IoException();
    }
    offset_ = 0;
  }
}

void Base128OutputStream::WriteByte(uint8_t byte) {
  if (offset_ == buffer_capacity_) {
    Flush();
  }
  buffer_[offset_++] = byte;
}

void Base128OutputStream::WriteBytes(const string& bytes) {
  WriteInt32(bytes.size());
  for (auto b : bytes) {
    WriteByte(b);
  }
}

void Base128OutputStream::WriteUInt16(uint16_t value) {
  do {
    uint8_t b = value & 0x7F;
    value >>= 7;
    if (value != 0) {
      b |= 0x80;
    }
    WriteByte(b);
  } while (value != 0);
}

void Base128OutputStream::WriteUInt32(uint32_t value) {
  do {
    uint8_t b = value & 0x7F;
    value >>= 7;
    if (value != 0) {
      b |= 0x80;
    }
    WriteByte(b);
  } while (value != 0);
}

void Base128OutputStream::WriteUInt64(uint64_t value) {
  do {
    uint8_t b = value & 0x7F;
    value >>= 7;
    if (value != 0) {
      b |= 0x80;
    }
    WriteByte(b);
  } while (value != 0);
}

void Base128OutputStream::WriteBool(bool value) {
  WriteByte(value ? 1 : 0);
}

}  // namespace screensharing
