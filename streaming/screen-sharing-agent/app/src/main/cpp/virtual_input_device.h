/*
 * Copyright 2023 The Android Open Source Project
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

#include <bitset>
#include <chrono>
#include <map>

#include "common.h"

// User input injection mechanism based on the uinput module (https://kernel.org/doc/html/v4.12/input/uinput.html).

namespace screensharing {

enum class UinputAction {
  RELEASE = 0,
  PRESS = 1,
  MOVE = 2,
  CANCEL = 3,
};

enum class DeviceType;

class VirtualInputDevice {
public:
  static constexpr size_t MAX_POINTERS = 20;
  static constexpr size_t MAX_PRESSURE = 255;

  [[nodiscard]] bool IsValid() const;

  [[nodiscard]] const std::string& phys() const {
    return phys_;
  }

protected:
  VirtualInputDevice(std::string phys);
  virtual ~VirtualInputDevice();

  bool WriteInputEvent(uint16_t type, uint16_t code, int32_t value, std::chrono::nanoseconds event_time);
  bool WriteEvKeyEvent(
      int32_t android_code, int32_t android_action,
      const std::map<int, int>& ev_key_code_mapping, const std::map<int, UinputAction>& action_mapping,
      std::chrono::nanoseconds event_time);

  int fd_;  // Owned.
  std::string phys_;

  DISALLOW_COPY_AND_ASSIGN(VirtualInputDevice);
};

class VirtualKeyboard : public VirtualInputDevice {
public:
  VirtualKeyboard();
  ~VirtualKeyboard() override;

  bool WriteKeyEvent(int32_t android_key_code, int32_t android_action, std::chrono::nanoseconds event_time);

  static const std::map<int, int> KEY_CODE_MAPPING;
  // Public to be shared with VirtualDpad.
  static const std::map<int, UinputAction> KEY_ACTION_MAPPING;
};

class VirtualDpad : public VirtualInputDevice {
public:
  VirtualDpad();
  ~VirtualDpad() override;

  bool WriteDpadKeyEvent(int32_t android_key_code, int32_t android_action, std::chrono::nanoseconds event_time);

  static const std::map<int, int> DPAD_KEY_CODE_MAPPING;
};

class VirtualMouse : public VirtualInputDevice {
public:
  VirtualMouse();
  ~VirtualMouse() override;

  bool WriteButtonEvent(int32_t android_button_code, int32_t android_action, std::chrono::nanoseconds event_time);
  bool WriteRelativeEvent(int32_t relative_x, int32_t relative_y, std::chrono::nanoseconds event_time);
  bool WriteScrollEvent(int32_t x_axis_movement, int32_t y_axis_movement, std::chrono::nanoseconds event_time);

private:
  friend class VirtualStylus;

  static const std::map<int, int> BUTTON_CODE_MAPPING;
  // Expose to share with VirtualStylus.
  static const std::map<int, UinputAction> BUTTON_ACTION_MAPPING;
};

class VirtualTouchscreen : public VirtualInputDevice {
public:
  VirtualTouchscreen(int32_t screen_width, int32_t screen_height);
  ~VirtualTouchscreen() override;

  bool WriteTouchEvent(int32_t pointer_id, int32_t tool_type, int32_t action, int32_t location_x, int32_t location_y,
                       int32_t pressure, int32_t major_axis_size, std::chrono::nanoseconds event_time);

  [[nodiscard]] int32_t screen_width() const { return screen_width_; }
  [[nodiscard]] int32_t screen_height() const { return screen_height_; }

private:
  bool IsValidPointerId(int32_t pointer_id, UinputAction uinput_action);
  bool HandleTouchDown(int32_t pointer_id, std::chrono::nanoseconds event_time);
  bool HandleTouchUp(int32_t pointer_id, std::chrono::nanoseconds event_time);

  static const std::map<int, int> TOOL_TYPE_MAPPING;

  int32_t screen_width_;
  int32_t screen_height_;

  // The set of active touch pointers on this device.
  // We only allow pointer id to go up to MAX_POINTERS because the maximum slots of virtual
  // touchscreen is set up with MAX_POINTERS. Note that in other cases Android allows pointer id
  // to go up to MAX_POINTERS_ID.
  std::bitset<MAX_POINTERS> active_pointers_{};
};

class VirtualStylus : public VirtualInputDevice {
public:
  VirtualStylus(int32_t screen_width, int32_t screen_height);
  ~VirtualStylus() override;

  bool WriteMotionEvent(int32_t tool_type, int32_t action, int32_t location_x, int32_t location_y,
                        int32_t pressure, int32_t tilt_x, int32_t tilt_y, std::chrono::nanoseconds event_time);
  bool WriteButtonEvent(int32_t android_button_code, int32_t android_action, std::chrono::nanoseconds event_time);

  [[nodiscard]] int32_t screen_width() const { return screen_width_; }
  [[nodiscard]] int32_t screen_height() const { return screen_height_; }

private:
  bool HandleStylusDown(uint16_t tool, std::chrono::nanoseconds event_time);
  bool HandleStylusUp(uint16_t tool, std::chrono::nanoseconds event_time);

  static const std::map<int, int> TOOL_TYPE_MAPPING;
  static const std::map<int, int> BUTTON_CODE_MAPPING;

  int32_t screen_width_;
  int32_t screen_height_;

  // True if the stylus is touching or hovering on the screen.
  bool is_stylus_down_ = false;
};

}  // namespace screensharing