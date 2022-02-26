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
#include "jvm.h"
#include "log.h"
#include "num_to_string.h"
#include "settings.h"

namespace screensharing {

using namespace std;
using namespace std::chrono;

namespace {

// Constants from android.os.BatteryManager.
constexpr int BATTERY_PLUGGED_AC = 1;
constexpr int BATTERY_PLUGGED_USB = 2;
constexpr int BATTERY_PLUGGED_WIRELESS = 4;

constexpr int BUFFER_SIZE = 4096;
constexpr int UTF8_MAX_BYTES_PER_CHARACTER = 4;

int64_t UptimeMillis() {
  timespec t = { 0, 0 };
  clock_gettime(CLOCK_MONOTONIC, &t);
  return static_cast<int64_t>(t.tv_sec) * 1000LL + t.tv_nsec / 1000000;
}

// Returns the number of Unicode code points contained in the given UTF-8 string.
int Utf8CharacterCount(const string& str) {
  int count = 0;
  for (auto c : str) {
    if ((c & 0xC0) != 0x80) {
      ++count;
    }
  }
  return count;
}

}  // namespace

Controller::Controller(int socket_fd)
    : input_stream_(socket_fd, BUFFER_SIZE),
      output_stream_(socket_fd, BUFFER_SIZE),
      thread_(),
      input_manager_(),
      pointer_helper_(),
      motion_event_start_time_(0),
      key_character_map_(),
      stay_on_(Settings::Table::GLOBAL, "stay_on_while_plugged_in"),
      accelerometer_rotation_(Settings::Table::SYSTEM, "accelerometer_rotation"),
      clipboard_listener_(this),
      clipboard_manager_(),  // Assigned on first use.
      max_synced_clipboard_length_(0) {
  assert(socket_fd > 0);
}

Controller::~Controller() {
  StopClipboardSync();
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
  StopClipboardSync();
  output_stream_.Close();
}

void Controller::Initialize() {
  input_manager_ = new InputManager(jni_);
  pointer_helper_ = new PointerHelper(jni_);
  pointer_properties_ = pointer_helper_->NewPointerPropertiesArray(MotionEventMessage::MAX_POINTERS);
  pointer_coordinates_ = pointer_helper_->NewPointerCoordsArray(MotionEventMessage::MAX_POINTERS);

  for (int i = 0; i < MotionEventMessage::MAX_POINTERS; ++i) {
    JObject properties = pointer_helper_->NewPointerProperties();
    pointer_properties_.SetElement(i, properties);
    JObject coords = pointer_helper_->NewPointerCoords();
    pointer_coordinates_.SetElement(i, coords);
  }

  key_character_map_ = new KeyCharacterMap(jni_);

  pointer_properties_.MakeGlobal();
  pointer_coordinates_.MakeGlobal();

  // Keep the screen on as long as the device has power.
  stay_on_.Set(num_to_string<BATTERY_PLUGGED_AC | BATTERY_PLUGGED_USB | BATTERY_PLUGGED_WIRELESS>::value);
  // Turn off "Auto-rotate screen".
  accelerometer_rotation_.Set("0");
}

void Controller::Run() {
  Log::D("Controller::Run");
  try {
    for (;;) {
      unique_ptr<ControlMessage> message = ControlMessage::Deserialize(input_stream_);
      ProcessMessage(*message);
    }
  } catch (EndOfFile& e) {
    Log::D("Controller::Run: End of command stream");
    // Returning from the Run method.
  } catch (IoException& e) {
    Log::Fatal("%s", e.GetMessage().c_str());
  }
}

void Controller::ProcessMessage(const ControlMessage& message) {
  switch (message.get_type()) {
    case MotionEventMessage::TYPE:
      ProcessMotionEvent((const MotionEventMessage&) message);
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

    case StartClipboardSyncMessage::TYPE:
      StartClipboardSync((const StartClipboardSyncMessage&) message);
      break;

    case StopClipboardSyncMessage::TYPE:
      StopClipboardSync();
      break;

    default:
      Log::E("Unexpected message type %d", message.get_type());
      break;
  }
}

void Controller::ProcessMotionEvent(const MotionEventMessage& message) {
  int64_t now = UptimeMillis();
  MotionEvent event(jni_);
  event.display_id = message.get_display_id();
  int32_t action = message.get_action();
  event.action = action;
  event.event_time_millis = now;
  if (action == AMOTION_EVENT_ACTION_DOWN) {
    motion_event_start_time_ = now;
  }
  if (motion_event_start_time_ == 0) {
    Log::W("Motion event started with action %d instead of expected %d", action, AMOTION_EVENT_ACTION_DOWN);
    motion_event_start_time_ = now;
  }
  event.down_time_millis = motion_event_start_time_;
  if (action == AMOTION_EVENT_ACTION_UP) {
    motion_event_start_time_ = 0;
  }

  for (auto& pointer : message.get_pointers()) {
    JObject properties = pointer_properties_.GetElement(jni_, event.pointer_count);
    pointer_helper_->SetPointerId(properties, pointer.pointer_id);
    JObject coordinates = pointer_coordinates_.GetElement(jni_, event.pointer_count);
    pointer_helper_->SetPointerCoords(coordinates, pointer.x, pointer.y);
    float pressure =
        (action == AMOTION_EVENT_ACTION_POINTER_UP && event.pointer_count == action >> AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT) ? 0 : 1;
    pointer_helper_->SetPointerPressure(coordinates, pressure);
    event.pointer_count++;
  }

  event.pointer_properties = pointer_properties_;
  event.pointer_coordinates = pointer_coordinates_;
  // InputManager doesn't allow ACTION_DOWN and ACTION_UP events with multiple pointers.
  // They have to be converted to a sequence of pointer-specific events.
  if (action == AMOTION_EVENT_ACTION_DOWN) {
    for (int i = 1; event.pointer_count = i, i < message.get_pointers().size(); i++) {
      input_manager_->InjectInputEvent(event.ToJava(), InputEventInjectionSync::NONE);
      event.action = AMOTION_EVENT_ACTION_POINTER_DOWN | (i << AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT);
    }
  }
  else if (action == AMOTION_EVENT_ACTION_UP) {
    for (int i = event.pointer_count; --i > 1;) {
      event.action = AMOTION_EVENT_ACTION_POINTER_UP | (i << AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT);
      pointer_helper_->SetPointerPressure(pointer_coordinates_.GetElement(jni_, i), 0);
      input_manager_->InjectInputEvent(event.ToJava(), InputEventInjectionSync::NONE);
      event.pointer_count = i;
    }
    event.action = AMOTION_EVENT_ACTION_UP;
  }
  input_manager_->InjectInputEvent(event.ToJava(), InputEventInjectionSync::NONE);
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

void Controller::StartClipboardSync(const StartClipboardSyncMessage& message) {
  int old_synced_clipboard_length = max_synced_clipboard_length_.exchange(message.get_max_synced_length());
  clipboard_manager_ = ClipboardManager::GetInstance(jni_);
  clipboard_manager_->SetText(message.get_text());
  if (old_synced_clipboard_length == 0) {
    clipboard_manager_->AddClipboardListener(&clipboard_listener_);
  }
}

void Controller::StopClipboardSync() {
  clipboard_manager_->RemoveClipboardListener(&clipboard_listener_);
  max_synced_clipboard_length_ = 0;
}

void Controller::OnPrimaryClipChanged() {
  auto text = clipboard_manager_->GetText();
  int max_length = max_synced_clipboard_length_;
  if (!text.empty() && text.size() <= max_length * UTF8_MAX_BYTES_PER_CHARACTER && Utf8CharacterCount(text) <= max_length) {
    ClipboardChangedMessage message(text);
    try {
      message.Serialize(output_stream_);
      output_stream_.Flush();
    } catch (EndOfFile& e) {
      // The socket has been closed - ignore.
    }
  }
}

Controller::ClipboardListener::~ClipboardListener() = default;

void Controller::ClipboardListener::OnPrimaryClipChanged() {
  controller_->OnPrimaryClipChanged();
}

}  // namespace screensharing
