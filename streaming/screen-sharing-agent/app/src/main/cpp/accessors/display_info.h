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

#include <cstdint>
#include <map>
#include <string>

#include "geom.h"

namespace screensharing {

// Native code analogue of the android.view.DisplayInfo class.
struct DisplayInfo {
  DisplayInfo() noexcept;
  DisplayInfo(
      int32_t logical_width, int32_t logical_height, int32_t logical_density_dpi, int32_t rotation, int32_t layer_stack, int32_t flags,
      int32_t type, int32_t state) noexcept;

  [[nodiscard]] bool IsValid() const {
    return logical_size.width != 0 && logical_size.height != 0;
  }

  // Returns the display dimensions in the canonical orientation.
  [[nodiscard]] Size NaturalSize() const {
    return logical_size.Rotated(-rotation);
  }

  [[nodiscard]] bool IsOn() const {
    return state == STATE_ON || state == STATE_VR;
  }

  bool operator==(const DisplayInfo& other) const {
    return logical_size == other.logical_size &&
           logical_density_dpi == other.logical_density_dpi &&
           rotation == other.rotation &&
           layer_stack == other.layer_stack &&
           flags == other.flags &&
           type == other.type &&
           state == other.state;
  }

  bool operator!=(const DisplayInfo& other) const {
    return !operator==(other);
  }

  [[nodiscard]] std::string ToDebugString() const;

  [[nodiscard]] static std::string ToDebugString(const std::map<int, DisplayInfo>& displays);

  Size logical_size { 0, 0 };
  int32_t logical_density_dpi = 0;
  int32_t rotation = 0;
  int32_t layer_stack = 0;
  int32_t flags = 0;
  int32_t type = 0;
  int32_t state = 0;

  // From frameworks/base/core/java/android/view/Display.java
  static constexpr int32_t FLAG_PRIVATE = 1 << 2;
  static constexpr int32_t FLAG_ROUND = 1 << 4;

  static constexpr int32_t TYPE_INTERNAL = 1;
  enum State { STATE_UNKNOWN = 0, STATE_OFF = 1, STATE_ON = 2, STATE_DOZE = 3, STATE_DOZE_SUSPEND = 4, STATE_VR = 5, STATE_ON_SUSPEND = 6 };
};

}  // namespace screensharing