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

#include "controller.h"

#include <thread>

#include "accessors/motion_event.h"
#include "accessors/key_event.h"
#include "accessors/window_manager.h"
#include "agent.h"
#include "log.h"
#include "jvm.h"

namespace screensharing {

using namespace std;
using namespace std::chrono;

namespace {

constexpr int BUFFER_SIZE = 4096;

int64_t UptimeMillis() {
  timespec t = { 0, 0 };
  clock_gettime(CLOCK_MONOTONIC, &t);
  return static_cast<int64_t>(t.tv_sec) * 1000LL + t.tv_nsec / 1000000;
}

}  // namespace

Controller::Controller(int socket_fd)
    : input_stream_(socket_fd, BUFFER_SIZE),
      thread_(),
      input_manager_(),
      pointer_helper_(),
      key_character_map_() {
  assert(socket_fd > 0);
}

Controller::~Controller() {
  input_stream_.Close();
  if (thread_.joinable()) {
    thread_.join();
  }
  delete input_manager_;
  delete pointer_helper_;
  delete key_character_map_;
}

void Controller::Start() {
  Log::D("Controller::Start");
  thread_ = thread([this]() {
    jni_ = Jvm::AttachCurrentThread("Controller");
    Initialize();
    Run();
    Jvm::DetachCurrentThread();
    Agent::Shutdown();
  });
}

void Controller::Shutdown() {
  input_stream_.Close();
}

void Controller::Initialize() {
  input_manager_ = new InputManager(jni_);
  pointer_helper_ = new PointerHelper(jni_);
  pointer_properties_ = pointer_helper_->NewPointerPropertiesArray(MAX_TOUCHES);
  pointer_coordinates_ = pointer_helper_->NewPointerCoordsArray(MAX_TOUCHES);

  for (int i = 0; i < MAX_TOUCHES; ++i) {
    JObject properties = pointer_helper_->NewPointerProperties();
    pointer_properties_.SetElement(i, properties);
    JObject coords = pointer_helper_->NewPointerCoords();
    pointer_coordinates_.SetElement(i, coords);
  }

  key_character_map_ = new KeyCharacterMap(jni_);

  pointer_properties_.MakeGlobal();
  pointer_coordinates_.MakeGlobal();
}

void Controller::Run() {
  Log::D("Controller::Run");
  try {
    for (;;) {
      unique_ptr<Message> message = Message::deserialize(input_stream_);
      ProcessMessage(*message);
    }
  } catch (EndOfFile& e) {
    Log::D("Controller::Run: End of command stream");
    // Returning from the Run method.
  } catch (IoException& e) {
    Log::Fatal("%s", e.GetMessage().c_str());
  }
}

void Controller::ProcessMessage(const Message& message) {
  switch (message.get_type()) {
    case MouseEventMessage::TYPE:
      ProcessMouseEvent((const MouseEventMessage&) message);
      break;

    case KeyEventMessage::TYPE:
      ProcessKeyboardEvent((const KeyEventMessage&) message);
      break;

    case TextInputMessage::TYPE:
      ProcessTextInput((const TextInputMessage&) message);
      break;

    case SetDeviceOrientationMessage::TYPE:
      ProcessSetDeviceOrientation((const SetDeviceOrientationMessage&) message);
      break;

    case SetMaxVideoResolutionMessage::TYPE:
      ProcessSetMaxVideoResolution((const SetMaxVideoResolutionMessage&) message);
      break;

    default:
      Log::E("Unexpected message type %d", message.get_type());
      break;
  }
}

void Controller::ProcessMouseEvent(const MouseEventMessage& message) {
  int64_t now = UptimeMillis();
  MotionEvent event(jni_);
  int pointer_id = 0;
  event.device_id = message.get_display_id();
  event.button_state = message.get_button_state();
  int32_t pressure = event.button_state & 0x1;
  event.action = pressure == 0 ? AMOTION_EVENT_ACTION_UP : AMOTION_EVENT_ACTION_DOWN;
  auto pressed_pointer = FindPressedPointer(pointer_id);
  if (pressed_pointer == pressed_pointers_.end()) {
    if (pressure == 0) {
      return;
    }
    event.down_time_millis = 0;
    event.action = AMOTION_EVENT_ACTION_DOWN;
    pressed_pointers_.push_back(PressedPointer { pointer_id, now, Point(message.get_x(), message.get_y()) });
  } else {
    event.down_time_millis = now - pressed_pointer->press_time_millis;
    if (pressure != 0) {
      event.action = AMOTION_EVENT_ACTION_MOVE;
    } else {
      event.action = AMOTION_EVENT_ACTION_UP;
      pressed_pointers_.erase(pressed_pointer);
    }
  }
  event.event_time_millis = now;
  event.pointer_count = 1;
  JObject properties = pointer_properties_.GetElement(jni_, 0);
  pointer_helper_->SetPointerId(properties, pointer_id);
  JObject coordinates = pointer_coordinates_.GetElement(jni_, 0);
  pointer_helper_->SetPointerCoords(coordinates, message.get_x(), message.get_y());
  pointer_helper_->SetPointerPressure(coordinates, pressure);
  event.pointer_properties = pointer_properties_;
  event.pointer_coordinates = pointer_coordinates_;
  JObject motion_event = event.ToJava();
  input_manager_->InjectInputEvent(motion_event, InputEventInjectionSync::NONE);
}

void Controller::ProcessKeyboardEvent(const KeyEventMessage& message) {
  int64_t now = UptimeMillis();
  KeyEvent event(jni_);
  event.down_time_millis = now;
  event.event_time_millis = now;
  int32_t action = message.get_action();
  event.action = action == KeyEventMessage::ACTION_DOWN_AND_UP ? AKEY_EVENT_ACTION_DOWN : action;
  event.code = message.get_keycode();
  event.meta_state = message.get_meta_state();
  event.source = KeyCharacterMap::VIRTUAL_KEYBOARD;
  JObject key_event = event.ToJava();
  input_manager_->InjectInputEvent(key_event, InputEventInjectionSync::NONE);
  if (action == KeyEventMessage::ACTION_DOWN_AND_UP) {
    event.action = AKEY_EVENT_ACTION_UP;
    key_event = event.ToJava();
    input_manager_->InjectInputEvent(key_event, InputEventInjectionSync::NONE);
  }
}

void Controller::ProcessTextInput(const TextInputMessage& message) {
  const u16string& text = message.get_text();
  for (uint16_t c: text) {
    JObjectArray event_array = key_character_map_->GetEvents(&c, 1);
    if (event_array.IsNull()) {
      Log::E("Unable to map character '\\u%04X' to key events", c);
      continue;
    }
    auto len = event_array.GetLength();
    for (int i = 0; i < len; i++) {
      JObject key_event = event_array.GetElement(i);
      input_manager_->InjectInputEvent(key_event, InputEventInjectionSync::NONE);
    }
  }
}

void Controller::ProcessSetDeviceOrientation(const SetDeviceOrientationMessage& message) {
  int orientation = message.get_orientation();
  if (orientation < 0 || orientation >= 4) {
    Log::E("An attempt to set an invalid device orientation: %d", orientation);
    return;
  }
  bool rotation_was_frozen = WindowManager::IsRotationFrozen(jni_);

  WindowManager::FreezeRotation(jni_, orientation);
  // Restore the original state of auto-display_rotation.
  if (!rotation_was_frozen) {
    WindowManager::ThawRotation(jni_);
  }

  Agent::OnVideoOrientationChanged(orientation);
}

void Controller::ProcessSetMaxVideoResolution(const SetMaxVideoResolutionMessage& message) {
  if (message.get_width() <= 0 || message.get_height() <= 0) {
    Log::E("An attempt to set an invalid video resolution: %dx%d", message.get_width(), message.get_height());
    return;
  }
  Size max_size(message.get_width(), message.get_height());
  Agent::OnMaxVideoResolutionChanged(max_size);
}

vector<Controller::PressedPointer>::iterator Controller::FindPressedPointer(int pointer_id) {
  return find_if(pressed_pointers_.begin(), pressed_pointers_.end(),
                 [pointer_id](PressedPointer& p) { return p.pointer_id == pointer_id; });
}

}  // namespace screensharing
