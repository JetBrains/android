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

#include <thread>
#include <vector>
#include <accessors/input_manager.h>
#include <accessors/key_character_map.h>

#include "common.h"
#include "accessors/pointer_helper.h"
#include "base128_input_stream.h"
#include "messages.h"
#include "geom.h"
#include "jvm.h"

namespace screensharing {

// Processes control socket commands.
class Controller {
public:
  Controller(int socket_fd);
  ~Controller();

  void Start();
  void Shutdown();

private:
  struct PressedPointer {
    int32_t pointer_id;
    int64_t press_time_millis;
    Point last_location;
  };

  void Initialize();
  void Run();
  void ProcessMessage(const Message& message);
  void ProcessMouseEvent(const MouseEventMessage& message);
  void ProcessKeyboardEvent(const KeyEventMessage& message);
  void ProcessTextInput(const TextInputMessage& message);
  std::vector<PressedPointer>::iterator FindPressedPointer(int pointer_id);

  Jni jni_ = nullptr;
  Base128InputStream input_stream_;
  std::thread thread_;
  InputManager* input_manager_;
  PointerHelper* pointer_helper_;
  JObjectArray pointer_properties_;  // MotionEvent.PointerProperties[]
  JObjectArray pointer_coordinates_;  // MotionEvent.PointerCoords[]
  std::vector<PressedPointer> pressed_pointers_;
  KeyCharacterMap* key_character_map_;

  DISALLOW_COPY_AND_ASSIGN(Controller);
};

}  // namespace screensharing
