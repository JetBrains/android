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

#include "control_messages.h"

#include "log.h"

namespace screensharing {

using namespace std;

unique_ptr<ControlMessage> ControlMessage::Deserialize(Base128InputStream& stream) {
  int32_t type = stream.ReadInt32();
  return Deserialize(type, stream);
}

unique_ptr<ControlMessage> ControlMessage::Deserialize(int32_t type, Base128InputStream& stream) {
  switch (type) {
    case MotionEventMessage::TYPE:
      return unique_ptr<ControlMessage>(MotionEventMessage::Deserialize(stream));

    case KeyEventMessage::TYPE:
      return unique_ptr<ControlMessage>(KeyEventMessage::Deserialize(stream));

    case TextInputMessage::TYPE:
      return unique_ptr<ControlMessage>(TextInputMessage::Deserialize(stream));

    case SetDeviceOrientationMessage::TYPE:
      return unique_ptr<ControlMessage>(SetDeviceOrientationMessage::Deserialize(stream));

    case SetMaxVideoResolutionMessage::TYPE:
      return unique_ptr<ControlMessage>(SetMaxVideoResolutionMessage::Deserialize(stream));

    case StartVideoStreamMessage::TYPE:
      return unique_ptr<ControlMessage>(StartVideoStreamMessage::Deserialize(stream));

    case StopVideoStreamMessage::TYPE:
      return unique_ptr<ControlMessage>(StopVideoStreamMessage::Deserialize(stream));

    case StartAudioStreamMessage::TYPE:
      return unique_ptr<ControlMessage>(StartAudioStreamMessage::Deserialize(stream));

    case StopAudioStreamMessage::TYPE:
      return unique_ptr<ControlMessage>(StopAudioStreamMessage::Deserialize(stream));

    case StartClipboardSyncMessage::TYPE:
      return unique_ptr<ControlMessage>(StartClipboardSyncMessage::Deserialize(stream));

    case StopClipboardSyncMessage::TYPE:
      return unique_ptr<ControlMessage>(StopClipboardSyncMessage::Deserialize(stream));

    case RequestDeviceStateMessage::TYPE:
      return unique_ptr<ControlMessage>(RequestDeviceStateMessage::Deserialize(stream));

    case DisplayConfigurationRequest::TYPE:
      return unique_ptr<ControlMessage>(DisplayConfigurationRequest::Deserialize(stream));

    case UiSettingsRequest::TYPE:
      return unique_ptr<ControlMessage>(UiSettingsRequest::Deserialize(stream));

    case UiSettingsChangeRequest::TYPE:
      return unique_ptr<ControlMessage>(UiSettingsChangeRequest::Deserialize(stream));

    case ResetUiSettingsRequest::TYPE:
      return unique_ptr<ControlMessage>(ResetUiSettingsRequest::Deserialize(stream));

    default:
      Log::Fatal(INVALID_CONTROL_MESSAGE, "Unexpected message type %d", type);
  }
}

void ControlMessage::Serialize(Base128OutputStream& stream) const {
  stream.WriteInt32(type_);
}

void CorrelatedMessage::Serialize(Base128OutputStream& stream) const {
  ControlMessage::Serialize(stream);
  stream.WriteInt32(request_id_);
}

MotionEventMessage* MotionEventMessage::Deserialize(Base128InputStream& stream) {
  uint32_t num_pointers = stream.ReadUInt32();
  vector<Pointer> pointers(num_pointers);
  for (auto i = 0; i < num_pointers; i++) {
    Pointer& pointer = pointers.at(i);
    pointer.x = stream.ReadInt32();
    pointer.y = stream.ReadInt32();
    pointer.pointer_id = stream.ReadInt32();
    uint32_t num_axes = stream.ReadUInt32();
    for (auto j = 0; j < num_axes; j++) {
      int32_t axis = stream.ReadInt32();
      float value = stream.ReadFloat();
      pointer.axis_values[axis] = value;
    }
  }
  if (num_pointers > MAX_POINTERS) {
    Log::W("Motion event with %d pointers, pointers after first %d are ignored)", num_pointers, MAX_POINTERS);
    pointers.resize(MAX_POINTERS);
  }
  int32_t action = stream.ReadInt32();
  int32_t button_state = stream.ReadInt32();
  int32_t action_button = stream.ReadInt32();
  int32_t display_id = stream.ReadInt32();
  return new MotionEventMessage(std::move(pointers), action, button_state, action_button, display_id);
}

KeyEventMessage* KeyEventMessage::Deserialize(Base128InputStream& stream) {
  int32_t action = stream.ReadInt32();
  int32_t keycode = stream.ReadInt32();
  uint32_t meta_state = stream.ReadUInt32();
  return new KeyEventMessage(action, keycode, meta_state);
}

TextInputMessage* TextInputMessage::Deserialize(Base128InputStream& stream) {
  unique_ptr<u16string> text = stream.ReadString16();
  if (text == nullptr || text->empty()) {
    Log::Fatal(INVALID_CONTROL_MESSAGE, "Received a TextInputMessage without text");
  }
  return new TextInputMessage(*text);
}

SetDeviceOrientationMessage* SetDeviceOrientationMessage::Deserialize(Base128InputStream& stream) {
  int32_t orientation = stream.ReadInt32();
  return new SetDeviceOrientationMessage(orientation);
}

SetMaxVideoResolutionMessage* SetMaxVideoResolutionMessage::Deserialize(Base128InputStream& stream) {
  int32_t display_id = stream.ReadInt32();
  int32_t width = stream.ReadInt32();
  int32_t height = stream.ReadInt32();
  return new SetMaxVideoResolutionMessage(display_id, Size(width, height));
}

StartVideoStreamMessage* StartVideoStreamMessage::Deserialize(Base128InputStream& stream) {
  int32_t display_id = stream.ReadInt32();
  int32_t width = stream.ReadInt32();
  int32_t height = stream.ReadInt32();
  return new StartVideoStreamMessage(display_id, Size(width, height));
}

StopVideoStreamMessage* StopVideoStreamMessage::Deserialize(Base128InputStream& stream) {
  int32_t display_id = stream.ReadInt32();
  return new StopVideoStreamMessage(display_id);
}

StartAudioStreamMessage* StartAudioStreamMessage::Deserialize(Base128InputStream& stream) {
  return new StartAudioStreamMessage();
}

StopAudioStreamMessage* StopAudioStreamMessage::Deserialize(Base128InputStream& stream) {
  return new StopAudioStreamMessage();
}

StartClipboardSyncMessage* StartClipboardSyncMessage::Deserialize(Base128InputStream& stream) {
  int max_sync_length = stream.ReadInt32();
  string text = stream.ReadBytes();
  return new StartClipboardSyncMessage(max_sync_length, std::move(text));
}

StopClipboardSyncMessage* StopClipboardSyncMessage::Deserialize(Base128InputStream& stream) {
  return new StopClipboardSyncMessage();
}

RequestDeviceStateMessage* RequestDeviceStateMessage::Deserialize(Base128InputStream& stream) {
  int state = stream.ReadInt32() - 1; // Subtracting 1 to account for shifted encoding.
  return new RequestDeviceStateMessage(state);
}

DisplayConfigurationRequest* DisplayConfigurationRequest::Deserialize(Base128InputStream& stream) {
  int32_t request_id = stream.ReadInt32();
  return new DisplayConfigurationRequest(request_id);
}

UiSettingsRequest* UiSettingsRequest::Deserialize(Base128InputStream& stream) {
  int32_t request_id = stream.ReadInt32();
  return new UiSettingsRequest(request_id);
}

UiSettingsChangeRequest* UiSettingsChangeRequest::Deserialize(Base128InputStream& stream) {
  int32_t request_id = stream.ReadInt32();
  UiCommand command = static_cast<UiCommand>(stream.ReadInt32());
  switch (command) {
    case DARK_MODE:
      return createDarkModeChangeRequest(request_id, stream.ReadBool());

    case FONT_SCALE:
      return createFontScaleChangeRequest(request_id, stream.ReadInt32());

    case DENSITY:
      return createDensityChangeRequest(request_id, stream.ReadInt32());

    case TALKBACK:
      return createTalkbackChangeRequest(request_id, stream.ReadBool());

    case SELECT_TO_SPEAK:
      return createSelectToSpeakChangeRequest(request_id, stream.ReadBool());

    case GESTURE_NAVIGATION:
      return createGestureNavigationChangeRequest(request_id, stream.ReadBool());

    case DEBUG_LAYOUT:
      return createDebugLayoutChangeRequest(request_id, stream.ReadBool());

    case APP_LOCALE:
      return createAppLocaleChangeRequest(request_id, stream.ReadBytes(), stream.ReadBytes());

    default:
      Log::Fatal(INVALID_CONTROL_MESSAGE, "Unexpected ui settings command %d", command);
  }
}

ResetUiSettingsRequest* ResetUiSettingsRequest::Deserialize(Base128InputStream& stream) {
  int32_t request_id = stream.ReadInt32();
  return new ResetUiSettingsRequest(request_id);
}

void ErrorResponse::Serialize(Base128OutputStream& stream) const {
  CorrelatedMessage::Serialize(stream);
  stream.WriteBytes(error_message_);
}

void DisplayConfigurationResponse::Serialize(Base128OutputStream& stream) const {
  CorrelatedMessage::Serialize(stream);
  stream.WriteInt32(displays_.size());
  for (auto display : displays_) {
    stream.WriteInt32(display.first);
    stream.WriteInt32(display.second.logical_size.width);
    stream.WriteInt32(display.second.logical_size.height);
    stream.WriteInt32(display.second.rotation);
    stream.WriteInt32(display.second.type);
  }
}

void ClipboardChangedNotification::Serialize(Base128OutputStream& stream) const {
  ControlMessage::Serialize(stream);
  stream.WriteBytes(text_);
}

void SupportedDeviceStatesNotification::Serialize(Base128OutputStream& stream) const {
  ControlMessage::Serialize(stream);
  size_t num_states = device_states_.size();
  stream.WriteUInt32(num_states);
  for (int i = 0; i < num_states; ++i) {
    device_states_[i].Serialize(stream);
  }
  stream.WriteInt32(device_state_id_ + 1);  // Offset by 1 to efficiently represent -1.
}

void DeviceStateNotification::Serialize(Base128OutputStream& stream) const {
  ControlMessage::Serialize(stream);
  stream.WriteInt32(device_state_id_ + 1);  // Offset by 1 to efficiently represent -1.
}

void DisplayAddedNotification::Serialize(Base128OutputStream& stream) const {
  ControlMessage::Serialize(stream);
  stream.WriteInt32(display_id_);
}

void DisplayRemovedNotification::Serialize(Base128OutputStream& stream) const {
  ControlMessage::Serialize(stream);
  stream.WriteInt32(display_id_);
}

void UiSettingsRequest::Serialize(Base128OutputStream& stream) const {
  CorrelatedMessage::Serialize(stream);
}

void UiSettingsResponse::Serialize(Base128OutputStream& stream) const {
  CorrelatedMessage::Serialize(stream);
  stream.WriteBool(dark_mode_);
  stream.WriteInt32(font_scale_);
  stream.WriteInt32(density_);
  stream.WriteBool(talkback_on_);
  stream.WriteBool(select_to_speak_on_);
  stream.WriteBool(gesture_navigation_);
  stream.WriteInt32(debug_layout_);
  stream.WriteBytes(foreground_application_id_);
  stream.WriteBytes(app_locale_);

  stream.WriteBool(original_values_);

  stream.WriteBool(font_scale_settable_);
  stream.WriteBool(density_settable_);
  stream.WriteBool(talkback_installed_);
  stream.WriteBool(gesture_overlay_installed_);
}

void UiSettingsChangeResponse::Serialize(Base128OutputStream& stream) const {
  CorrelatedMessage::Serialize(stream);
  stream.WriteBool(original_values_);
}

UiSettingsChangeRequest* UiSettingsChangeRequest::createDarkModeChangeRequest(int32_t request_id, bool dark_mode) {
  auto request = new UiSettingsChangeRequest(request_id, DARK_MODE);
  request->dark_mode_ = dark_mode;
  return request;
}

UiSettingsChangeRequest* UiSettingsChangeRequest::createFontScaleChangeRequest(int32_t request_id, int32_t font_scale) {
  auto request = new UiSettingsChangeRequest(request_id, FONT_SCALE);
  request->font_scale_ = font_scale;
  return request;
}

UiSettingsChangeRequest* UiSettingsChangeRequest::createDensityChangeRequest(int32_t request_id, int32_t density) {
  auto request = new UiSettingsChangeRequest(request_id, DENSITY);
  request->density_ = density;
  return request;
}

UiSettingsChangeRequest* UiSettingsChangeRequest::createTalkbackChangeRequest(int32_t request_id, bool talkback) {
  auto request = new UiSettingsChangeRequest(request_id, TALKBACK);
  request->talkback_ = talkback;
  return request;
}

UiSettingsChangeRequest* UiSettingsChangeRequest::createSelectToSpeakChangeRequest(int32_t request_id, bool select_to_speak) {
  auto request = new UiSettingsChangeRequest(request_id, SELECT_TO_SPEAK);
  request->select_to_speak_ = select_to_speak;
  return request;
}

UiSettingsChangeRequest* UiSettingsChangeRequest::createGestureNavigationChangeRequest(int32_t request_id, bool gesture_navigation) {
  auto request = new UiSettingsChangeRequest(request_id, GESTURE_NAVIGATION);
  request->gesture_navigation_ = gesture_navigation;
  return request;
}

UiSettingsChangeRequest* UiSettingsChangeRequest::createDebugLayoutChangeRequest(int32_t request_id, bool debug_layout) {
  auto request = new UiSettingsChangeRequest(request_id, DEBUG_LAYOUT);
  request->debug_layout_ = debug_layout;
  return request;
}

UiSettingsChangeRequest* UiSettingsChangeRequest::createAppLocaleChangeRequest(int32_t request_id, std::string application_id, std::string locale) {
  auto request = new UiSettingsChangeRequest(request_id, APP_LOCALE);
  request->application_id_ = application_id;
  request->locale_ = locale;
  return request;
}

}  // namespace screensharing
