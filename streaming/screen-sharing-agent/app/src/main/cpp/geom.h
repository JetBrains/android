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

#include <android/rect.h>

#include <cstdint>

namespace screensharing {

struct Size {
  Size(int32_t width, int32_t height) noexcept : width(width), height(height) {}

  bool operator==(Size other) const {
    return width == other.width && height == other.height;
  }

  bool operator!=(Size other) const {
    return width != other.width || height != other.height;
  }

  Size Rotated(int32_t rotation) const {
    return rotation % 2 == 0 ? *this : Size(height, width);
  }

  static Size ofRect(const ARect& rect) {
    return Size(rect.right - rect.left, rect.bottom - rect.top);
  }

  ARect toRect() const {
    return ARect { 0, 0, width, height };
  }

  int32_t width;
  int32_t height;
};

struct Point {
  Point(int32_t x, int32_t y) : x(x), y(y) {}

  int32_t x;
  int32_t y;
};

// Converts the rotation value to the canonical [0, 3] range.
inline uint8_t NormalizeRotation(int32_t rotation) {
  return rotation & 0x03;
}

}  // namespace screensharing
