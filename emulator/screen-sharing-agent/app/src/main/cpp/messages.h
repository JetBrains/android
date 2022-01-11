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

#include "common.h"
#include "base128_input_stream.h"

namespace screensharing {

constexpr int MAX_TOUCHES = 2;

// Common base class of all control messages.
class Message {
public:
  virtual ~Message() {}

  int32_t get_type() const {
    return type_;
  }

  static std::unique_ptr<Message> deserialize(Base128InputStream& stream);

protected:
  Message(int32_t type)
      : type_(type) {
  }

  int32_t type_;
};

// Represents a mouse button being pressed or released or a mouse being moved.
class MouseEventMessage : Message {
public:
  MouseEventMessage(int32_t x, int32_t y, uint32_t buttons, uint32_t display_id)
    : Message(TYPE),
      x_(x),
      y_(y),
      button_state_(buttons),
      display_id_(display_id) {
  }
  virtual ~MouseEventMessage() {};

  // The horizontal coordinate corresponding to the display in its original orientation.
  int32_t get_x() const { return x_; }

  // The vertical coordinate corresponding to the display in its original orientation.
  int32_t get_y() const { return y_; }

  // Indicates which buttons were pressed.
  // 0: No buttons were pressed
  // 1: Primary button (left)
  // 2: Secondary button (right)
  uint32_t get_button_state() const { return button_state_; }

  // The display device where the mouse event occurred. Zero indicates the main display.
  uint32_t get_display_id() const { return display_id_; }

  static constexpr int TYPE = 0;

private:
  friend class Message;

  static MouseEventMessage* deserialize(Base128InputStream& stream);

  int32_t x_;
  int32_t y_;
  uint32_t button_state_;
  uint32_t display_id_;

  DISALLOW_COPY_AND_ASSIGN(MouseEventMessage);
};

// TYPE = 1 is reserved for a multi-touch control message.

// Represents a key being pressed or released on a keyboard.
class KeyEventMessage : Message {
public:
  KeyEventMessage(int32_t action, int32_t keycode, uint32_t meta_state)
      : Message(TYPE),
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
  friend class Message;

  static KeyEventMessage* deserialize(Base128InputStream& stream);

  int32_t action_;
  int32_t keycode_;
  uint32_t meta_state_;

  DISALLOW_COPY_AND_ASSIGN(KeyEventMessage);
};

// Represents one or more characters typed on a keyboard.
class TextInputMessage : Message {
public:
  TextInputMessage(const std::u16string& text)
      : Message(TYPE),
        text_(text) {
  }
  virtual ~TextInputMessage() {};

  const std::u16string& get_text() const { return text_; }

  static constexpr int TYPE = 3;

private:
  friend class Message;

  static TextInputMessage* deserialize(Base128InputStream& stream);

  std::u16string text_;

  DISALLOW_COPY_AND_ASSIGN(TextInputMessage);
};

}  // namespace screensharing
