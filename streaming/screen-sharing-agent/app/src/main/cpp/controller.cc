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

#include <android/keycodes.h>
#include <sys/socket.h>

#include "accessors/device_state_manager.h"
#include "accessors/input_manager.h"
#include "accessors/key_event.h"
#include "accessors/motion_event.h"
#include "agent.h"
#include "flags.h"
#include "jvm.h"
#include "log.h"

namespace screensharing {

using namespace std;
using namespace std::chrono;

namespace {

constexpr int BUFFER_SIZE = 4096;
constexpr int UTF8_MAX_BYTES_PER_CHARACTER = 4;

constexpr int SOCKET_RECEIVE_TIMEOUT_MILLIS = 250;

constexpr int FINGER_TOUCH_SIZE = 1;

nanoseconds UptimeNanos() {
  timespec t = { 0, 0 };
  clock_gettime(CLOCK_MONOTONIC, &t);
  return duration_cast<nanoseconds>(seconds(t.tv_sec)) + nanoseconds(t.tv_nsec);
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

// Sets the receive timeout for the given socket. Zero timeout value means that reading
// from the socket will never time out.
void SetReceiveTimeoutMillis(int timeout_millis, int socket_fd) {
  struct timeval tv = { .tv_sec = timeout_millis / 1000, .tv_usec = (timeout_millis % 1000) * 1000 };
  setsockopt(socket_fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
}

bool CheckVideoSize(Size video_resolution) {
  if (video_resolution.width > 0 && video_resolution.height > 0) {
    return true;
  }
  Log::E("An attempt to set an invalid video resolution: %dx%d", video_resolution.width, video_resolution.height);
  return false;
}

void InjectMotionEvent(Jni jni, const MotionEvent& event, InputEventInjectionSync mode) {
  JObject motion_event = event.ToJava();
  if (motion_event.IsNull()) {
    return;  // The error has already been logged.
  }
  if (event.action == AMOTION_EVENT_ACTION_HOVER_MOVE || Log::IsEnabled(Log::Level::VERBOSE)) {
    Log::V("motion_event: %s", motion_event.ToString().c_str());
  } else if (Log::IsEnabled(Log::Level::DEBUG)) {
    Log::D("motion_event: %s", motion_event.ToString().c_str());
  }
  InputManager::InjectInputEvent(jni, motion_event, mode);
}

void InjectKeyEvent(Jni jni, const KeyEvent& event, InputEventInjectionSync mode) {
  JObject key_event = event.ToJava();
  if (Log::IsEnabled(Log::Level::DEBUG)) {
    Log::D("key_event: %s", key_event.ToString().c_str());
  }
  InputManager::InjectInputEvent(jni, key_event, mode);
}

}  // namespace

Controller::Controller(int socket_fd)
    : socket_fd_(socket_fd),
      input_stream_(socket_fd, BUFFER_SIZE),
      output_stream_(SocketWriter(socket_fd, "control"), BUFFER_SIZE),
      clipboard_listener_(this),
      device_state_listener_(this) {
  assert(socket_fd > 0);
  try {
    output_stream_.WriteByte('C');
    output_stream_.Flush();
  } catch (EndOfFile& e) {
    Log::D("Disconnected while writing command channel marker");
  } catch (IoException& e) {
    Log::Fatal(SOCKET_IO_ERROR, "%s", "Timed out while writing command channel marker");
  }
}

Controller::~Controller() {
  Stop();
  input_stream_.Close();
  output_stream_.Close();
  delete pointer_helper_;
  delete key_character_map_;
  delete virtual_keyboard_;
  delete virtual_mouse_;
}

void Controller::Stop() {
  if (device_supports_multiple_states_) {
    DeviceStateManager::RemoveDeviceStateListener(&device_state_listener_);
  }
  ui_settings_.Reset(nullptr);
  stopped = true;
}

void Controller::Initialize() {
  jni_ = Jvm::GetJni();
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
  if ((Agent::flags() & START_VIDEO_STREAM) != 0) {
    WakeUpDevice();
  }

  const vector<DeviceState>& device_states = DeviceStateManager::GetSupportedDeviceStates(jni_);
  if (!device_states.empty()) {
    device_supports_multiple_states_ = true;
    DeviceStateManager::AddDeviceStateListener(&device_state_listener_);
    int32_t device_state_identifier = DeviceStateManager::GetDeviceStateIdentifier(jni_);
    Log::D("Controller::Initialize: device_state_identifier=%d", device_state_identifier);
    SupportedDeviceStatesNotification supported_device_states_notification(device_states, device_state_identifier);
    try {
      supported_device_states_notification.Serialize(output_stream_);
      output_stream_.Flush();
    } catch (EndOfFile& e) {
      // The socket has been closed - ignore.
    }
    device_state_identifier_ = device_state_identifier;
  }

  DisplayManager::AddDisplayListener(jni_, this);

  Agent::InitializeSessionEnvironment();
}

void Controller::InitializeVirtualKeyboard() {
  if (virtual_keyboard_ == nullptr) {
    virtual_keyboard_ = new VirtualKeyboard();
    if (!virtual_keyboard_->IsValid()) {
      Log::E("Failed to create a virtual keyboard");
    }
  }
}

VirtualMouse& Controller::GetVirtualMouse(int32_t display_id) {
  if (virtual_mouse_ == nullptr) {
    virtual_mouse_ = new VirtualMouse();
    if (!virtual_mouse_->IsValid()) {
      Log::E("Failed to create a virtual mouse");
    }
  }
  if (virtual_mouse_display_id_ != display_id) {
    InputManager::AddPortAssociation(jni_, virtual_mouse_->phys(), display_id);
    virtual_mouse_display_id_ = display_id;
  }
  return *virtual_mouse_;
}

VirtualTouchscreen& Controller::GetVirtualTouchscreen(int32_t display_id, int32_t width, int32_t height) {
  auto iter = virtual_touchscreens_.find(display_id);
  if (iter == virtual_touchscreens_.end() || iter->second->screen_width() != width || iter->second->screen_height() != height) {
    if (iter != virtual_touchscreens_.end()) {
      InputManager::RemovePortAssociation(jni_, iter->second->phys());
    }
    iter = virtual_touchscreens_.insert_or_assign(display_id, make_unique<VirtualTouchscreen>(width, height)).first;
    InputManager::AddPortAssociation(jni_, iter->second->phys(), display_id);
  }
  return *iter->second;
}

void Controller::Run() {
  Log::D("Controller::Run");
  Initialize();

  try {
    for (;;) {
      auto socket_timeout = SOCKET_RECEIVE_TIMEOUT_MILLIS;
      if (!stopped) {
        if (max_synced_clipboard_length_ != 0) {
          SendClipboardChangedNotification();
        }

        if (device_supports_multiple_states_) {
          SendDeviceStateNotification();
        }

        if (poll_displays_until_ != steady_clock::time_point()) {
          PollDisplays();
          socket_timeout /= 5;  // Reduce socket timeout to increase polling frequency.
        }

        SendPendingDisplayEvents();
      }

      SetReceiveTimeoutMillis(socket_timeout, socket_fd_);  // Set a receive timeout to avoid blocking for a long time.
      int32_t message_type;
      try {
        message_type = input_stream_.ReadInt32();
      } catch (IoTimeout& e) {
        continue;
      }
      SetReceiveTimeoutMillis(0, socket_fd_);  // Remove receive timeout for reading the rest of the message.
      unique_ptr<ControlMessage> message = ControlMessage::Deserialize(message_type, input_stream_);
      if (!stopped) {
        ProcessMessage(*message);
      }
    }
  } catch (EndOfFile& e) {
    Log::D("Controller::Run: End of command stream");
  } catch (IoException& e) {
    Log::Fatal(SOCKET_IO_ERROR, "Error reading from command socket channel - %s", e.GetMessage().c_str());
  }
}

void Controller::ProcessMessage(const ControlMessage& message) {
  if (message.type() != MotionEventMessage::TYPE) { // Exclude
    Log::I("Controller::ProcessMessage %d", message.type());
  }
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

    case StartVideoStreamMessage::TYPE:
      StartVideoStream((const StartVideoStreamMessage&) message);
      break;

    case StopVideoStreamMessage::TYPE:
      StopVideoStream((const StopVideoStreamMessage&) message);
      break;

    case StartAudioStreamMessage::TYPE:
      StartAudioStream((const StartAudioStreamMessage&) message);
      break;

    case StopAudioStreamMessage::TYPE:
      StopAudioStream((const StopAudioStreamMessage&) message);
      break;

    case StartClipboardSyncMessage::TYPE:
      StartClipboardSync((const StartClipboardSyncMessage&) message);
      break;

    case StopClipboardSyncMessage::TYPE:
      StopClipboardSync();
      break;

    case RequestDeviceStateMessage::TYPE:
      RequestDeviceState((const RequestDeviceStateMessage&) message);
      break;

    case DisplayConfigurationRequest::TYPE:
      SendDisplayConfigurations((const DisplayConfigurationRequest&) message);
      break;

    case UiSettingsRequest::TYPE:
      SendUiSettings((const UiSettingsRequest&) message);
      break;

    case SetDarkModeRequest::TYPE:
      SetDarkMode((const SetDarkModeRequest&) message);
      break;

    case SetFontScaleRequest::TYPE:
      SetFontScale((const SetFontScaleRequest&) message);
      break;

    case SetScreenDensityRequest::TYPE:
      SetScreenDensity((const SetScreenDensityRequest&) message);
      break;

    case SetTalkBackRequest::TYPE:
      SetTalkBack((const SetTalkBackRequest&) message);
      break;

    case SetSelectToSpeakRequest::TYPE:
      SetSelectToSpeak((const SetSelectToSpeakRequest&) message);
      break;

    case SetAppLanguageRequest::TYPE:
      SetAppLanguage((const SetAppLanguageRequest&) message);
      break;

    case SetGestureNavigationRequest::TYPE:
      SetGestureNavigation((const SetGestureNavigationRequest&) message);
      break;

    case ResetUiSettingsRequest::TYPE:
      ResetUiSettings((const ResetUiSettingsRequest&) message);
      break;

    default:
      Log::Fatal(INVALID_CONTROL_MESSAGE, "Unexpected message type %d", message.type());
  }
}

void Controller::ProcessMotionEvent(const MotionEventMessage& message) {
  nanoseconds event_time = UptimeNanos();
  int32_t action = message.action();
  Log::V("Controller::ProcessMotionEvent action:%d", action);
  int32_t display_id = message.display_id();
  DisplayInfo display_info = Agent::GetDisplayInfo(display_id);
  if (!display_info.IsValid()) {
    return;
  }

  if (Agent::feature_level() >= 29 && Agent::flags() & USE_UINPUT &&
      // TODO: Handle hover and scroll motion events using uinput.
      action != AMOTION_EVENT_ACTION_HOVER_MOVE && action != AMOTION_EVENT_ACTION_HOVER_EXIT && action != AMOTION_EVENT_ACTION_SCROLL &&
      message.action_button() == 0 && message.button_state() == 0) {
    auto& touchscreen = GetVirtualTouchscreen(display_id, display_info.logical_size.width, display_info.logical_size.height);
    if (action == AMOTION_EVENT_ACTION_DOWN || action == AMOTION_EVENT_ACTION_UP || action == AMOTION_EVENT_ACTION_MOVE) {
      int32_t pressure = action == AMOTION_EVENT_ACTION_UP ? 0 : VirtualTouchscreen::MAX_PRESSURE;
      int32_t major_axis_size = pressure == 0 ? 0 : FINGER_TOUCH_SIZE;
      for (auto& pointer : message.pointers()) {
        bool success = touchscreen.WriteTouchEvent(pointer.pointer_id, AMOTION_EVENT_TOOL_TYPE_FINGER, action, pointer.x, pointer.y,
                                                   pressure, major_axis_size, event_time);
        if (!success) {
          Log::E("Error writing touch event");
        }
      }
    } else {
      auto action_code = action & AMOTION_EVENT_ACTION_MASK;
      if (action_code == AMOTION_EVENT_ACTION_POINTER_DOWN || action_code == AMOTION_EVENT_ACTION_POINTER_UP) {
        auto pointer_id = action >> AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT;
        action = action_code == AMOTION_EVENT_ACTION_POINTER_DOWN ? AMOTION_EVENT_ACTION_DOWN : AMOTION_EVENT_ACTION_UP;
        int32_t pressure = action == AMOTION_EVENT_ACTION_UP ? 0 : VirtualTouchscreen::MAX_PRESSURE;
        int32_t major_axis_size = pressure == 0 ? 0 : FINGER_TOUCH_SIZE;
        for (auto& pointer : message.pointers()) {
          if (pointer.pointer_id == pointer_id) {
            bool success = touchscreen.WriteTouchEvent(pointer_id, AMOTION_EVENT_TOOL_TYPE_FINGER, action, pointer.x, pointer.y,
                                                       pressure, major_axis_size, event_time);
            if (!success) {
              Log::E("Error writing touch event");
            }
            break;
          }
        }
      }
    }
  } else {
    MotionEvent event(jni_);
    event.display_id = display_id;
    event.action = action;
    event.button_state = message.button_state();
    event.event_time_millis = duration_cast<milliseconds>(event_time).count();;
    if (action != AMOTION_EVENT_ACTION_HOVER_MOVE && action != AMOTION_EVENT_ACTION_SCROLL) {
      if (action == AMOTION_EVENT_ACTION_DOWN) {
        motion_event_start_time_ = event.event_time_millis;
      }
      if (motion_event_start_time_ == 0) {
        Log::E("Motion event started with action %d instead of expected %d", action, AMOTION_EVENT_ACTION_DOWN);
        motion_event_start_time_ = event.event_time_millis;
      }
      event.down_time_millis = motion_event_start_time_;
      if (action == AMOTION_EVENT_ACTION_UP) {
        motion_event_start_time_ = 0;
      }
      Agent::RecordTouchEvent();
    }
    if (action == AMOTION_EVENT_ACTION_HOVER_MOVE || message.action_button() != 0 || message.button_state() != 0) {
      // AINPUT_SOURCE_MOUSE
      // - when action_button() is non-zero, as the Android framework has special handling for mouse in performButtonActionOnTouchDown(),
      //   which opens the context menu on right click.
      // - when message.button_state() is non-zero, otherwise drag operations initiated by touch down with AINPUT_SOURCE_MOUSE will not
      //   receive mouse move events.
      event.source = AINPUT_SOURCE_MOUSE;
    } else {
      event.source = AINPUT_SOURCE_STYLUS | AINPUT_SOURCE_TOUCHSCREEN;
    }

    for (auto& pointer: message.pointers()) {
      JObject properties = pointer_properties_.GetElement(jni_, event.pointer_count);
      pointer_helper_->SetPointerId(properties, pointer.pointer_id);
      JObject coordinates = pointer_coordinates_.GetElement(jni_, event.pointer_count);
      // We must clear first so that axis information from previous runs is not reused.
      pointer_helper_->ClearPointerCoords(coordinates);
      Point point = AdjustedDisplayCoordinates(pointer.x, pointer.y, display_info);
      pointer_helper_->SetPointerCoords(coordinates, point.x, point.y);
      float pressure = ((action & AMOTION_EVENT_ACTION_MASK) == AMOTION_EVENT_ACTION_POINTER_UP &&
          event.pointer_count == action >> AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT) ? 0 : 1;
      pointer_helper_->SetPointerPressure(coordinates, pressure);
      for (auto const& [axis, value]: pointer.axis_values) {
        pointer_helper_->SetAxisValue(coordinates, axis, value);
      }
      event.pointer_count++;
    }

    event.pointer_properties = pointer_properties_;
    event.pointer_coordinates = pointer_coordinates_;
    // InputManager doesn't allow ACTION_DOWN and ACTION_UP events with multiple pointers.
    // They have to be converted to a sequence of pointer-specific events.
    if (action == AMOTION_EVENT_ACTION_DOWN) {
      if (message.action_button()) {
        InjectMotionEvent(jni_, event, InputEventInjectionSync::NONE);
        event.action = AMOTION_EVENT_ACTION_BUTTON_PRESS;
        event.action_button = message.action_button();
      } else {
        for (int i = 1; event.pointer_count = i, i < message.pointers().size(); i++) {
          InjectMotionEvent(jni_, event, InputEventInjectionSync::NONE);
          event.action = AMOTION_EVENT_ACTION_POINTER_DOWN | (i << AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT);
        }
      }
    } else if (action == AMOTION_EVENT_ACTION_UP) {
      if (message.action_button()) {
        event.action = AMOTION_EVENT_ACTION_BUTTON_RELEASE;
        event.action_button = message.action_button();
        InjectMotionEvent(jni_, event, InputEventInjectionSync::NONE);
        event.action = AMOTION_EVENT_ACTION_UP;
        event.action_button = 0;
      } else {
        for (int i = event.pointer_count; --i > 1;) {
          event.action = AMOTION_EVENT_ACTION_POINTER_UP | (i << AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT);
          pointer_helper_->SetPointerPressure(pointer_coordinates_.GetElement(jni_, i), 0);
          InjectMotionEvent(jni_, event, InputEventInjectionSync::NONE);
          event.pointer_count = i;
        }
        event.action = AMOTION_EVENT_ACTION_UP;
      }
    }
    InjectMotionEvent(jni_, event, InputEventInjectionSync::NONE);
  }

  if (action == AMOTION_EVENT_ACTION_UP) {
    // This event may have started an app. Update the app-level display orientation.
    Agent::SetVideoOrientation(display_id, DisplayStreamer::CURRENT_VIDEO_ORIENTATION);
  }

  // Wake up the device if the display was turned off.
  if (action == AMOTION_EVENT_ACTION_DOWN && !display_info.IsOn()) {
    WakeUpDevice();
  }
}

void Controller::ProcessKeyboardEvent(Jni jni, const KeyEventMessage& message) {
  nanoseconds event_time = UptimeNanos();
  if (Agent::feature_level() >= 29 && Agent::flags() & USE_UINPUT) {
    InitializeVirtualKeyboard();
    int32_t action = message.action();
    virtual_keyboard_->WriteKeyEvent(
        message.keycode(), action == KeyEventMessage::ACTION_DOWN_AND_UP ? AKEY_EVENT_ACTION_DOWN : action, event_time);
    if (action == KeyEventMessage::ACTION_DOWN_AND_UP) {
      virtual_keyboard_->WriteKeyEvent(message.keycode(), AKEY_EVENT_ACTION_UP, event_time);
    }
  } else {
    KeyEvent event(jni);
    event.event_time_millis = duration_cast<milliseconds>(event_time).count();
    event.down_time_millis = event.event_time_millis;
    int32_t action = message.action();
    event.action = action == KeyEventMessage::ACTION_DOWN_AND_UP ? AKEY_EVENT_ACTION_DOWN : action;
    event.code = message.keycode();
    event.meta_state = message.meta_state();
    event.source = KeyCharacterMap::VIRTUAL_KEYBOARD;
    InjectKeyEvent(jni, event, InputEventInjectionSync::NONE);
    if (action == KeyEventMessage::ACTION_DOWN_AND_UP) {
      event.action = AKEY_EVENT_ACTION_UP;
      InjectKeyEvent(jni, event, InputEventInjectionSync::NONE);
    }
  }
}

void Controller::ProcessTextInput(const TextInputMessage& message) {
  nanoseconds event_time;
  if (Agent::feature_level() >= 29 && Agent::flags() & USE_UINPUT) {
    event_time = UptimeNanos();
    InitializeVirtualKeyboard();
  }
  const u16string& text = message.text();
  for (uint16_t c: text) {
    JObjectArray event_array = key_character_map_->GetEvents(&c, 1);
    if (event_array.IsNull()) {
      Log::W(jni_.GetAndClearException(), "Unable to map character '\\u%04X' to key events", c);
      continue;
    }
    auto len = event_array.GetLength();
    for (int i = 0; i < len; i++) {
      JObject key_event = event_array.GetElement(i);
      if (Agent::feature_level() >= 29 && Agent::flags() & USE_UINPUT) {
        virtual_keyboard_->WriteKeyEvent(KeyEvent::GetKeyCode(key_event), KeyEvent::GetAction(key_event), event_time);
      } else {
        if (Log::IsEnabled(Log::Level::DEBUG)) {
          Log::D("key_event: %s", key_event.ToString().c_str());
        }
        InputManager::InjectInputEvent(jni_, key_event, InputEventInjectionSync::NONE);
      }
    }
  }
}

void Controller::ProcessSetDeviceOrientation(const SetDeviceOrientationMessage& message) {
  int orientation = message.orientation();
  if (orientation < 0 || orientation >= 4) {
    Log::E("An attempt to set an invalid device orientation: %d", orientation);
    return;
  }
  Agent::SetVideoOrientation(PRIMARY_DISPLAY_ID, orientation);
}

void Controller::ProcessSetMaxVideoResolution(const SetMaxVideoResolutionMessage& message) {
  if (CheckVideoSize(message.max_video_size())) {
    Agent::SetMaxVideoResolution(message.display_id(), message.max_video_size());
  }
}

void Controller::StartVideoStream(const StartVideoStreamMessage& message) {
  if (CheckVideoSize(message.max_video_size())) {
    Agent::StartVideoStream(message.display_id(), message.max_video_size());
    WakeUpDevice();
  }
}

void Controller::StopVideoStream(const StopVideoStreamMessage& message) {
  Agent::StopVideoStream(message.display_id());
}

void Controller::StartAudioStream(const StartAudioStreamMessage& message) {
  Agent::StartAudioStream();
}

void Controller::StopAudioStream(const StopAudioStreamMessage& message) {
  Agent::StopAudioStream();
}

void Controller::WakeUpDevice() {
  ProcessKeyboardEvent(Jvm::GetJni(), KeyEventMessage(KeyEventMessage::ACTION_DOWN_AND_UP, AKEYCODE_WAKEUP, 0));
}

void Controller::StartClipboardSync(const StartClipboardSyncMessage& message) {
  ClipboardManager* clipboard_manager = ClipboardManager::GetInstance(jni_);
  if (message.text() != last_clipboard_text_) {
    last_clipboard_text_ = message.text();
    clipboard_manager->SetText(last_clipboard_text_);
  }
  bool was_stopped = max_synced_clipboard_length_ == 0;
  max_synced_clipboard_length_ = message.max_synced_length();
  if (was_stopped) {
    clipboard_manager->AddClipboardListener(&clipboard_listener_);
  }
}

void Controller::StopClipboardSync() {
  if (max_synced_clipboard_length_ != 0) {
    ClipboardManager* clipboard_manager = ClipboardManager::GetInstance(jni_);
    clipboard_manager->RemoveClipboardListener(&clipboard_listener_);
    max_synced_clipboard_length_ = 0;
    last_clipboard_text_.resize(0);
  }
}

void Controller::OnPrimaryClipChanged() {
  Log::D("Controller::OnPrimaryClipChanged");
  clipboard_changed_ = true;
}

void Controller::SendClipboardChangedNotification() {
  if (!clipboard_changed_.exchange(false)) {
    return;
  }
  Log::D("Controller::SendClipboardChangedNotification");
  ClipboardManager* clipboard_manager = ClipboardManager::GetInstance(jni_);
  string text = clipboard_manager->GetText();
  if (text.empty() || text == last_clipboard_text_) {
    return;
  }
  int max_length = max_synced_clipboard_length_;
  if (text.size() > max_length * UTF8_MAX_BYTES_PER_CHARACTER || Utf8CharacterCount(text) > max_length) {
    return;
  }
  last_clipboard_text_ = text;

  ClipboardChangedNotification message(std::move(text));
  message.Serialize(output_stream_);
  output_stream_.Flush();
}

void Controller::RequestDeviceState(const RequestDeviceStateMessage& message) {
  DeviceStateManager::RequestState(jni_, message.state_id(), 0);
}

void Controller::OnDeviceStateChanged(int32_t device_state) {
  Log::D("Controller::OnDeviceStateChanged(%d)", device_state);
  int32_t previous_state = device_state_identifier_.exchange(device_state);
  if (previous_state != device_state) {
    Agent::SetVideoOrientation(PRIMARY_DISPLAY_ID, DisplayStreamer::CURRENT_DISPLAY_ORIENTATION);
  }
}

void Controller::SendDeviceStateNotification() {
  int32_t device_state = device_state_identifier_;
  if (device_state != previous_device_state_) {
    Log::D("Sending DeviceStateNotification(%d)", device_state);
    DeviceStateNotification notification(device_state);
    notification.Serialize(output_stream_);
    output_stream_.Flush();
    previous_device_state_ = device_state;
    // Many OEMs don't produce QPR releases, so their phones may be affected by b/303684492
    // that was fixed in Android 14 QPR1.
    if (Agent::feature_level() == 34 && Agent::device_manufacturer() != "Google") {
      StartDisplayPolling();  // Workaround for b/303684492.
    }
  }
}

void Controller::SendDisplayConfigurations(const DisplayConfigurationRequest& request) {
  vector<int32_t> display_ids = DisplayManager::GetDisplayIds(jni_);
  vector<pair<int32_t, DisplayInfo>> displays;
  displays.reserve(display_ids.size());
  for (auto display_id : display_ids) {
    DisplayInfo display_info = DisplayManager::GetDisplayInfo(jni_, display_id);
    if (display_info.IsOn() && (display_info.flags & DisplayInfo::FLAG_PRIVATE) == 0) {
      Log::D("Returning display configuration: displayId=%d state=%d flags=0x%2x size=%dx%d orientation=%d",
             display_id, display_info.state, display_info.flags, display_info.logical_size.width, display_info.logical_size.height,
             display_info.rotation);
      displays.emplace_back(display_id, display_info);
    }
  }
  DisplayConfigurationResponse response(request.request_id(), std::move(displays));
  response.Serialize(output_stream_);
  output_stream_.Flush();
}

void Controller::SendUiSettings(const UiSettingsRequest& message) {
  UiSettingsResponse response(message.request_id());
  ui_settings_.Get(&response);
  response.Serialize(output_stream_);
  output_stream_.Flush();
}

void Controller::SetDarkMode(const SetDarkModeRequest& message) {
  UiSettingsCommandResponse response(message.request_id());
  ui_settings_.SetDarkMode(message.dark_mode(), &response);
  response.Serialize(output_stream_);
  output_stream_.Flush();
}

void Controller::SetAppLanguage(const SetAppLanguageRequest& message) {
  UiSettingsCommandResponse response(message.request_id());
  ui_settings_.SetAppLanguage(message.application_id(), message.locale(), &response);
  response.Serialize(output_stream_);
  output_stream_.Flush();
}

void Controller::SetGestureNavigation(const SetGestureNavigationRequest& message) {
  UiSettingsCommandResponse response(message.request_id());
  ui_settings_.SetGestureNavigation(message.gesture_navigation(), &response);
  response.Serialize(output_stream_);
  output_stream_.Flush();
}

void Controller::SetTalkBack(const SetTalkBackRequest& message) {
  UiSettingsCommandResponse response(message.request_id());
  ui_settings_.SetTalkBack(message.talkback_on(), &response);
  response.Serialize(output_stream_);
  output_stream_.Flush();
}

void Controller::SetSelectToSpeak(const SetSelectToSpeakRequest& message) {
  UiSettingsCommandResponse response(message.request_id());
  ui_settings_.SetSelectToSpeak(message.select_to_speak_on(), &response);
  response.Serialize(output_stream_);
  output_stream_.Flush();
}

void Controller::SetFontScale(const SetFontScaleRequest& message) {
  UiSettingsCommandResponse response(message.request_id());
  ui_settings_.SetFontScale(message.font_scale(), &response);
  response.Serialize(output_stream_);
  output_stream_.Flush();
}

void Controller::SetScreenDensity(const SetScreenDensityRequest& message) {
  UiSettingsCommandResponse response(message.request_id());
  ui_settings_.SetScreenDensity(message.density(), &response);
  response.Serialize(output_stream_);
  output_stream_.Flush();
}

void Controller::ResetUiSettings(const ResetUiSettingsRequest& message) {
  UiSettingsResponse response(message.request_id());
  ui_settings_.Reset(&response);
  response.Serialize(output_stream_);
  output_stream_.Flush();
}

void Controller::OnDisplayAdded(int32_t display_id) {
  unique_lock lock(display_events_mutex_);
  pending_display_events_.emplace_back(display_id, DisplayEvent::Type::ADDED);
}

void Controller::OnDisplayRemoved(int32_t display_id) {
  unique_lock lock(display_events_mutex_);
  pending_display_events_.emplace_back(display_id, DisplayEvent::Type::REMOVED);
}

void Controller::OnDisplayChanged(int32_t display_id) {
}

void Controller::SendPendingDisplayEvents() {
  vector<DisplayEvent> display_events;
  {
    unique_lock lock(display_events_mutex_);
    swap(display_events, pending_display_events_);
  }

  for (auto event : display_events) {
    if (event.type == DisplayEvent::Type::ADDED) {
      DisplayAddedNotification notification(event.display_id);
      notification.Serialize(output_stream_);
      output_stream_.Flush();
      Log::D("Sent DisplayAddedNotification(%d)", event.display_id);
    }
    else if (event.type == DisplayEvent::Type::REMOVED) {
      virtual_touchscreens_.erase(event.display_id);

      DisplayRemovedNotification notification(event.display_id);
      notification.Serialize(output_stream_);
      output_stream_.Flush();
      Log::D("Sent DisplayRemovedNotification(%d)", event.display_id);
    }
  }
}

void Controller::StartDisplayPolling() {
  auto displays = GetDisplays();
  for (auto display : displays) {
    // Due to uncertain timing of events we have to assume that the display was both added and changed.
    DisplayManager::OnDisplayAdded(jni_, display.first);
    DisplayManager::OnDisplayChanged(jni_, display.first);
  }
  current_displays_ = displays;
  Log::D("Controller::StartDisplayPolling current_displays_.size()=%d", static_cast<int>(current_displays_.size()));
  poll_displays_until_ = steady_clock::now() + 500ms;
}

void Controller::StopDisplayPolling() {
  Log::D("Controller::StopDisplayPolling");
  current_displays_.clear();
  poll_displays_until_ = steady_clock::time_point();
}

void Controller::PollDisplays() {
  auto displays = GetDisplays();
  for (auto d1 = displays.begin(), d2 = current_displays_.begin(); d1 != displays.end() || d2 != current_displays_.end();) {
    if (d2 == current_displays_.end()) {
      // Due to uncertain timing of events we have to assume that the display was both added and changed.
      DisplayManager::OnDisplayAdded(jni_, d1->first);
      DisplayManager::OnDisplayChanged(jni_, d1->first);
      d1++;
    } else if (d1 == displays.end()) {
      DisplayManager::OnDisplayRemoved(jni_, d2->first);
      d2++;
    } else if (d1->first < d2->first) {
      // Due to uncertain timing of events we have to assume that the display was both added and changed.
      DisplayManager::OnDisplayAdded(jni_, d1->first);
      DisplayManager::OnDisplayChanged(jni_, d1->first);
      d1++;
    } else if (d1->first > d2->first) {
      DisplayManager::OnDisplayRemoved(jni_, d2->first);
      d2++;
    } else {
      if (d1->second != d2->second) {
        DisplayManager::OnDisplayChanged(jni_, d1->first);
      }
      d1++;
      d2++;
    }
  }

  current_displays_ = displays;
  if (steady_clock::now() > poll_displays_until_) {
    StopDisplayPolling();
  }
}

map<int32_t, DisplayInfo> Controller::GetDisplays() {
  vector<int32_t> display_ids = DisplayManager::GetDisplayIds(jni_);
  map<int32_t, DisplayInfo> displays;
  for (auto display_id: display_ids) {
    DisplayInfo display_info = DisplayManager::GetDisplayInfo(jni_, display_id);
    if (display_info.IsOn() && (display_info.flags & DisplayInfo::FLAG_PRIVATE) == 0) {
      displays[display_id] = display_info;
    }
  }
  return displays;
}

Controller::ClipboardListener::~ClipboardListener() = default;

void Controller::ClipboardListener::OnPrimaryClipChanged() {
  controller_->OnPrimaryClipChanged();
}

Controller::DeviceStateListener::~DeviceStateListener() = default;

void Controller::DeviceStateListener::OnDeviceStateChanged(int32_t device_state) {
  controller_->OnDeviceStateChanged(device_state);
}

}  // namespace screensharing
