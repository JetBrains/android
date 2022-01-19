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

#include <inttypes.h>

#include <memory>
#include <vector>
#include <android/input.h>

#include "base128_input_stream.h"
#include "common.h"

namespace screensharing {

// Common base class of all control messages.
class ControlMessage {
public:
  virtual ~ControlMessage() {}

  int32_t get_type() const {
    return type_;
  }

  static std::unique_ptr<ControlMessage> deserialize(Base128InputStream& stream);

protected:
  ControlMessage(int32_t type)
      : type_(type) {
  }

  int32_t type_;
};

// Represents an Android MotionEvent.
class MotionEventMessage : ControlMessage {
public:
  struct Pointer {
    Pointer(int32_t x, int32_t y, int32_t pointer_id)
        : x(x),
          y(y),
          pointer_id(pointer_id) {
    }
    Pointer() = default;

    // The horizontal coordinate of a touch corresponding to the display in its original orientation.
    int32_t x;
    // The vertical coordinate of a touch corresponding to the display in its original orientation.
    int32_t y;
    // The ID of the touch that stays the same when the touch point moves.
    int32_t pointer_id;
  };

  // Pointers are expected to be ordered according to their ids.
  // The action translates directly to android.view.MotionEvent.action.
  MotionEventMessage(std::vector<Pointer>&& pointers, int32_t action, int32_t display_id)
      : ControlMessage(TYPE),
        pointers_(pointers),
        action_(action),
        display_id_(display_id) {
  }
  virtual ~MotionEventMessage() {};

  // The touches, one for each finger. The pointers are ordered according to their ids.
  const std::vector<Pointer>& get_pointers() const { return pointers_; }

  // The action. See android.view.MotionEvent.action.
  int32_t get_action() const { return action_; }

  // The display device where the mouse event occurred. Zero indicates the main display.
  int32_t get_display_id() const { return display_id_; }

  static constexpr int TYPE = 1;

  static constexpr int MAX_POINTERS = 2;

private:
  friend class ControlMessage;

  static MotionEventMessage* deserialize(Base128InputStream& stream);

  const std::vector<Pointer> pointers_;
  const int32_t action_;
  const int32_t display_id_;

  DISALLOW_COPY_AND_ASSIGN(MotionEventMessage);
};

// Represents a key being pressed or released on a keyboard.
class KeyEventMessage : ControlMessage {
public:
  KeyEventMessage(int32_t action, int32_t keycode, uint32_t meta_state)
      : ControlMessage(TYPE),
        action_(action),
        keycode_(keycode),
        meta_state_(meta_state) {
  }
  virtual ~KeyEventMessage() {};

  // AKEY_EVENT_ACTION_DOWN, AKEY_EVENT_ACTION_UP or ACTION_DOWN_AND_UP.
  int32_t get_action() const { return action_; }

  // The code of the pressed or released key. */
  int32_t get_keycode() const { return keycode_; }

  int32_t get_meta_state() const { return meta_state_; }

  static constexpr int TYPE = 2;

  static constexpr int ACTION_DOWN_AND_UP = 8;

private:
  friend class ControlMessage;

  static KeyEventMessage* deserialize(Base128InputStream& stream);

  int32_t action_;
  int32_t keycode_;
  uint32_t meta_state_;

  DISALLOW_COPY_AND_ASSIGN(KeyEventMessage);
};

// Represents one or more characters typed on a keyboard.
class TextInputMessage : ControlMessage {
public:
  TextInputMessage(const std::u16string& text)
      : ControlMessage(TYPE),
        text_(text) {
  }
  virtual ~TextInputMessage() {};

  const std::u16string& get_text() const { return text_; }

  static constexpr int TYPE = 3;

private:
  friend class ControlMessage;

  static TextInputMessage* deserialize(Base128InputStream& stream);

  std::u16string text_;

  DISALLOW_COPY_AND_ASSIGN(TextInputMessage);
};

// Represents one or more characters typed on a keyboard.
class SetDeviceOrientationMessage : ControlMessage {
public:
  SetDeviceOrientationMessage(uint32_t orientation)
      : ControlMessage(TYPE),
        orientation_(orientation) {
  }
  virtual ~SetDeviceOrientationMessage() {};

  uint32_t get_orientation() const { return orientation_; }

  static constexpr int TYPE = 4;

private:
  friend class ControlMessage;

  static SetDeviceOrientationMessage* deserialize(Base128InputStream& stream);

  uint32_t orientation_;

  DISALLOW_COPY_AND_ASSIGN(SetDeviceOrientationMessage);
};

// Sets maximum display streaming resolution.
class SetMaxVideoResolutionMessage : ControlMessage {
public:
  SetMaxVideoResolutionMessage(uint32_t width, uint32_t height)
      : ControlMessage(TYPE),
        width_(width),
        height_(height) {
  }
  virtual ~SetMaxVideoResolutionMessage() {};

  uint32_t get_width() const { return width_; }
  uint32_t get_height() const { return height_; }

  static constexpr int TYPE = 5;

private:
  friend class ControlMessage;

  static SetMaxVideoResolutionMessage* deserialize(Base128InputStream& stream);

  uint32_t width_;
  uint32_t height_;

  DISALLOW_COPY_AND_ASSIGN(SetMaxVideoResolutionMessage);
};

}  // namespace screensharing
