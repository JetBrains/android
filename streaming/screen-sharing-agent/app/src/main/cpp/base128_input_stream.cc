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

#include "base128_input_stream.h"

#include <unistd.h>
#include <sys/socket.h>

#include <memory>

#include "log.h"

namespace screensharing {

using namespace std;

Base128InputStream::Base128InputStream(SocketReader&& reader, size_t buffer_size)
    : reader_(std::move(reader)),
      buffer_(new uint8_t[buffer_size]),
      buffer_capacity_(buffer_size),
      offset_(0),
      data_end_(0) {
}

Base128InputStream::~Base128InputStream() {
  Close();
  delete[] buffer_;
}

void Base128InputStream::Close() {
  shutdown(reader_.socket_fd(), SHUT_RD);
}

uint8_t Base128InputStream::ReadByte() {
  if (offset_ == data_end_) {
    auto result = reader_.Read(buffer_, buffer_capacity_);
    if (result.status == SocketReader::Status::DISCONNECTED) {
      throw EndOfFile();
    }
    if (result.status == SocketReader::Status::TIMEOUT) {
      throw IoTimeout();
    }
    if (result.status == SocketReader::Status::IO_ERROR) {
      throw IoException(result.error_code);
    }
    offset_ = 0;
    data_end_ = result.bytes_read;
  }
  return buffer_[offset_++];
}

string Base128InputStream::ReadBytes() {
  int len = ReadInt32();
  if (len < 0) {
    throw StreamFormatException::InvalidFormat();
  }
  string bytes;
  if (len > 0) {
    bytes.reserve(len);
    for (int i = 0; i < len; ++i) {
      bytes.push_back(static_cast<int8_t>(ReadByte()));
    }
  }
  return bytes;
}

int16_t Base128InputStream::ReadInt16() {
  int b = ReadByte();
  int value = b & 0x7F;
  for (int shift = 7; (b & 0x80) != 0; shift += 7) {
    b = ReadByte();
    if (shift == 21 && (b & 0xFC) != 0) {
      throw StreamFormatException::InvalidFormat();
    }
    value |= (b & 0x7F) << shift;
  }
  return static_cast<int16_t>(value);
}

int32_t Base128InputStream::ReadInt32() {
  int b = ReadByte();
  int value = b & 0x7F;
  for (int shift = 7; (b & 0x80) != 0; shift += 7) {
    b = ReadByte();
    if (shift == 28 && (b & 0xF0) != 0) {
      throw StreamFormatException::InvalidFormat();
    }
    value |= (b & 0x7F) << shift;
  }
  return value;
}

int64_t Base128InputStream::ReadInt64() {
  int b = ReadByte();
  long value = b & 0x7F;
  for (int shift = 7; (b & 0x80) != 0; shift += 7) {
    b = ReadByte();
    if (shift == 63 && (b & 0x7E) != 0) {
      throw StreamFormatException::InvalidFormat();
    }
    value |= ((long) (b & 0x7F)) << shift;
  }

  return value;
}

bool Base128InputStream::ReadBool() {
  int c = ReadByte();
  if ((c & ~0x1) != 0) {
    throw StreamFormatException::InvalidFormat();
  }
  return c != 0;
}

unique_ptr<u16string> Base128InputStream::ReadString16() {
  int len = ReadInt32();
  if (len < 0) {
    throw StreamFormatException::InvalidFormat();
  }
  if (len == 0) {
    return nullptr;
  }
  --len;
  if (len == 0) {
    return make_unique<u16string>();
  }
  auto result = make_unique<u16string>(static_cast<unsigned int>(len), '\0');
  for (int i = 0; i < len; i++) {
    (*result)[i] = ReadUInt16();
  }
  return result;
}

float Base128InputStream::ReadFloat() {
  // Float is written a 32-bit integer in little endian byte order as per IEEE 754 standard.
  float value;
  int32_t value_as_int32 = ReadFixed32();
  memcpy(&value, &value_as_int32, sizeof value);
  return value;
}

int32_t Base128InputStream::ReadFixed32() {
  return ReadByte() | ReadByte() << 8 | ReadByte() << 16 | ReadByte() << 24;
}

Base128InputStream::StreamFormatException Base128InputStream::StreamFormatException::InvalidFormat() {
  return StreamFormatException("Invalid file format");
}

}  // namespace screensharing
