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

#include <unistd.h>

#include <cstdio>

#include "accessors/input_manager.h"
#include "accessors/key_event.h"
#include "accessors/motion_event.h"
#include "accessors/service_manager.h"
#include "accessors/window_manager.h"
#include "agent.h"
#include "jvm.h"
#include "log.h"
#include "num_to_string.h"
#include "settings.h"

namespace screensharing {

using namespace std;
using namespace std::chrono;

// Names an location of the screen sharing agent's files.
#define SCREEN_SHARING_AGENT_JAR_NAME "screen-sharing-agent.jar"
#define SCREEN_SHARING_AGENT_SO_NAME "libscreen-sharing-agent.so"
#define DEVICE_PATH_BASE "/data/local/tmp/.studio"

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

Point AdjustedDisplayCoordinates(int32_t x, int32_t y, const DisplayInfo& display_info) {
  auto size = display_info.NaturalSize();
  switch (display_info.rotation) {
    case 1:
      return { y, size.width - x };

    case 2:
      return { size.width - x, size.height - y };

    case 3:
      return { size.height - y, x };

    default:
      return { x, y };
  }
}

// Removes files of the screen sharing agent from the persistent storage.
void RemoveAgentFiles() {
  remove(DEVICE_PATH_BASE "/" SCREEN_SHARING_AGENT_JAR_NAME);
  remove(DEVICE_PATH_BASE "/" SCREEN_SHARING_AGENT_SO_NAME);
}

}  // namespace

Controller::Controller(int socket_fd)
    : socket_fd_(socket_fd),
      input_stream_(socket_fd, BUFFER_SIZE),
      output_stream_(socket_fd, BUFFER_SIZE),
      thread_(),
      pointer_helper_(),
      motion_event_start_time_(0),
      key_character_map_(),
      stay_on_(Settings::Table::GLOBAL, "stay_on_while_plugged_in"),
      accelerometer_rotation_(Settings::Table::SYSTEM, "accelerometer_rotation"),
      clipboard_listener_(this),
      clipboard_manager_(),  // Assigned on first use.
      max_synced_clipboard_length_(0),
      setting_clipboard_(false) {
  assert(socket_fd > 0);
}

Controller::~Controller() {
  input_stream_.Close();
  StopClipboardSync();
  if (thread_.joinable()) {
    thread_.join();
  }
  delete pointer_helper_;
  delete key_character_map_;
  close(socket_fd_);
}

void Controller::Start() {
  Log::D("Controller::Start");
  thread_ = thread([this]() {
    jni_ = Jvm::AttachCurrentThread("Controller");
    Initialize();
    Run();
    Agent::Shutdown();
    Jvm::DetachCurrentThread();
  });
}

void Controller::Shutdown() {
  input_stream_.Close();
  StopClipboardSync();
  output_stream_.Close();
}

void Controller::Initialize() {
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

  ServiceManager::GetService(jni_, "settings");  // Wait for the "settings" service to initialize.
  // Keep the screen on as long as the device has power.
  stay_on_.Set(num_to_string<BATTERY_PLUGGED_AC | BATTERY_PLUGGED_USB | BATTERY_PLUGGED_WIRELESS>::value);
  // Turn off "Auto-rotate screen".
  accelerometer_rotation_.Set("0");

  RemoveAgentFiles();
}

void Controller::Run() {
  Log::D("Controller::Run");
  try {
    for (;;) {
      unique_ptr<ControlMessage> message = ControlMessage::Deserialize(input_stream_);
      ProcessMessage(*message);
    }
  } catch (StreamClosedException& e) {
    Log::D("Controller::Run: Command stream closed");
  } catch (EndOfFile& e) {
    Log::D("Controller::Run: End of command stream");
  } catch (IoException& e) {
    Log::Fatal("%s", e.GetMessage().c_str());
  }
}

void Controller::ProcessMessage(const ControlMessage& message) {
  switch (message.type()) {
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
      Log::E("Unexpected message type %d", message.type());
      break;
  }
}

void Controller::ProcessMotionEvent(const MotionEventMessage& message) {
  int64_t now = UptimeMillis();
  MotionEvent event(jni_);
  event.display_id = message.display_id();
  int32_t action = message.action();
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

  DisplayInfo display_info = Agent::GetDisplayInfo();

  for (auto& pointer : message.pointers()) {
    JObject properties = pointer_properties_.GetElement(jni_, event.pointer_count);
    pointer_helper_->SetPointerId(properties, pointer.pointer_id);
    JObject coordinates = pointer_coordinates_.GetElement(jni_, event.pointer_count);
    Point point = AdjustedDisplayCoordinates(pointer.x, pointer.y, display_info);
    pointer_helper_->SetPointerCoords(coordinates, point.x, point.y);
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
    for (int i = 1; event.pointer_count = i, i < message.pointers().size(); i++) {
      InputManager::InjectInputEvent(jni_, event.ToJava(), InputEventInjectionSync::NONE);
      event.action = AMOTION_EVENT_ACTION_POINTER_DOWN | (i << AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT);
    }
  }
  else if (action == AMOTION_EVENT_ACTION_UP) {
    for (int i = event.pointer_count; --i > 1;) {
      event.action = AMOTION_EVENT_ACTION_POINTER_UP | (i << AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT);
      pointer_helper_->SetPointerPressure(pointer_coordinates_.GetElement(jni_, i), 0);
      InputManager::InjectInputEvent(jni_, event.ToJava(), InputEventInjectionSync::NONE);
      event.pointer_count = i;
    }
    event.action = AMOTION_EVENT_ACTION_UP;
  }
  Agent::RecordTouchEvent();
  InputManager::InjectInputEvent(jni_, event.ToJava(), InputEventInjectionSync::NONE);

  if (event.action == AMOTION_EVENT_ACTION_UP) {
    // This event may have started an app. Update the app-level display orientation.
    Agent::SetVideoOrientation(-1);
  }
}

void Controller::ProcessKeyboardEvent(const KeyEventMessage& message) {
  int64_t now = duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
  KeyEvent event(jni_);
  event.down_time_millis = now;
  event.event_time_millis = now;
  int32_t action = message.action();
  event.action = action == KeyEventMessage::ACTION_DOWN_AND_UP ? AKEY_EVENT_ACTION_DOWN : action;
  event.code = message.keycode();
  event.meta_state = message.meta_state();
  event.source = KeyCharacterMap::VIRTUAL_KEYBOARD;
  JObject key_event = event.ToJava();
  InputManager::InjectInputEvent(jni_, key_event, InputEventInjectionSync::NONE);
  if (action == KeyEventMessage::ACTION_DOWN_AND_UP) {
    event.action = AKEY_EVENT_ACTION_UP;
    key_event = event.ToJava();
    InputManager::InjectInputEvent(jni_, key_event, InputEventInjectionSync::NONE);
  }
}

void Controller::ProcessTextInput(const TextInputMessage& message) {
  const u16string& text = message.text();
  for (uint16_t c: text) {
    JObjectArray event_array = key_character_map_->GetEvents(&c, 1);
    if (event_array.IsNull()) {
      Log::E("Unable to map character '\\u%04X' to key events", c);
      continue;
    }
    auto len = event_array.GetLength();
    for (int i = 0; i < len; i++) {
      JObject key_event = event_array.GetElement(i);
      InputManager::InjectInputEvent(jni_, key_event, InputEventInjectionSync::NONE);
    }
  }
}

void Controller::ProcessSetDeviceOrientation(const SetDeviceOrientationMessage& message) {
  int orientation = message.orientation();
  if (orientation < 0 || orientation >= 4) {
    Log::E("An attempt to set an invalid device orientation: %d", orientation);
    return;
  }
  Agent::SetVideoOrientation(orientation);
}

void Controller::ProcessSetMaxVideoResolution(const SetMaxVideoResolutionMessage& message) {
  if (message.width() <= 0 || message.height() <= 0) {
    Log::E("An attempt to set an invalid video resolution: %dx%d", message.width(), message.height());
    return;
  }
  Agent::SetMaxVideoResolution(Size(message.width(), message.height()));
}

void Controller::StartClipboardSync(const StartClipboardSyncMessage& message) {
  {
    scoped_lock lock(clipboard_mutex_);
    if (message.text() != last_clipboard_text_) {
      last_clipboard_text_ = message.text();
      setting_clipboard_ = true;
    }
    clipboard_manager_ = ClipboardManager::GetInstance(jni_);
    if (setting_clipboard_) {
      clipboard_manager_->SetText(jni_, message.text());
    }
    setting_clipboard_ = false;
    bool was_stopped = max_synced_clipboard_length_ == 0;
    max_synced_clipboard_length_ = message.max_synced_length();
    if (was_stopped) {
      clipboard_manager_->AddClipboardListener(&clipboard_listener_);
    }
  }
}

void Controller::StopClipboardSync() {
  scoped_lock lock(clipboard_mutex_);
  if (max_synced_clipboard_length_ != 0) {
    clipboard_manager_ = ClipboardManager::GetInstance(jni_);
    clipboard_manager_->RemoveClipboardListener(&clipboard_listener_);
    max_synced_clipboard_length_ = 0;
    last_clipboard_text_.resize(0);
  }
}

void Controller::OnPrimaryClipChanged() {
  string text;
  {
    scoped_lock lock(clipboard_mutex_);
    if (setting_clipboard_) {
      return;
    }
    // Cannot use jni_ because this method may be called on an arbitrary thread.
    text = clipboard_manager_->GetText(Jvm::GetJni());
    if (text.empty() || text == last_clipboard_text_) {
      return;
    }
    int max_length = max_synced_clipboard_length_;
    if (text.size() > max_length * UTF8_MAX_BYTES_PER_CHARACTER || Utf8CharacterCount(text) > max_length) {
      return;
    }
    last_clipboard_text_ = text;
  }

  ClipboardChangedNotification message(move(text));
  try {
    message.Serialize(output_stream_);
    output_stream_.Flush();
  } catch (StreamClosedException& e) {
    // The stream has been closed - ignore.
  } catch (EndOfFile& e) {
    // The socket has been closed - ignore.
  }
}

Controller::ClipboardListener::~ClipboardListener() = default;

void Controller::ClipboardListener::OnPrimaryClipChanged() {
  controller_->OnPrimaryClipChanged();
}

}  // namespace screensharing
