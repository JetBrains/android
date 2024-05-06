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

    case SetDarkModeMessage::TYPE:
      return unique_ptr<ControlMessage>(SetDarkModeMessage::Deserialize(stream));

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

SetDarkModeMessage* SetDarkModeMessage::Deserialize(Base128InputStream& stream) {
  bool dark_mode = stream.ReadBool();
  return new SetDarkModeMessage(dark_mode);
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
  stream.WriteBytes(text_);
}

void DeviceStateNotification::Serialize(Base128OutputStream& stream) const {
  ControlMessage::Serialize(stream);
  stream.WriteInt32(device_state_);
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
}

void SetDarkModeMessage::Serialize(Base128OutputStream& stream) const {
  ControlMessage::Serialize(stream);
  stream.WriteInt32(dark_mode_);
}

}  // namespace screensharing
