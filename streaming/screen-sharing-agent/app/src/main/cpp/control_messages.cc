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

    case ClipboardChangedNotification::TYPE:
      return unique_ptr<ControlMessage>(ClipboardChangedNotification::Deserialize(stream));

    default:
      Log::Fatal("Unexpected message type %d", type);
  }
}

void ControlMessage::Serialize(Base128OutputStream& stream) const {
  stream.WriteInt32(type_);
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
  int32_t display_id = stream.ReadInt32();
  return new MotionEventMessage(std::move(pointers), action, display_id);
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
    Log::Fatal("Received a TextInputMessage without text");
  }
  return new TextInputMessage(*text);
}

SetDeviceOrientationMessage* SetDeviceOrientationMessage::Deserialize(Base128InputStream& stream) {
  int32_t orientation = stream.ReadInt32();
  return new SetDeviceOrientationMessage(orientation);
}

SetMaxVideoResolutionMessage* SetMaxVideoResolutionMessage::Deserialize(Base128InputStream& stream) {
  int32_t width = stream.ReadInt32();
  int32_t height = stream.ReadInt32();
  return new SetMaxVideoResolutionMessage(width, height);
}

StopVideoStreamMessage* StopVideoStreamMessage::Deserialize(Base128InputStream& stream) {
  return new StopVideoStreamMessage();
}

StartVideoStreamMessage* StartVideoStreamMessage::Deserialize(Base128InputStream& stream) {
  return new StartVideoStreamMessage();
}

StartClipboardSyncMessage* StartClipboardSyncMessage::Deserialize(Base128InputStream& stream) {
  int max_sync_length = stream.ReadInt32();
  string text = stream.ReadBytes();
  return new StartClipboardSyncMessage(max_sync_length, std::move(text));
}

StopClipboardSyncMessage* StopClipboardSyncMessage::Deserialize(Base128InputStream& stream) {
  return new StopClipboardSyncMessage();
}

void ClipboardChangedNotification::Serialize(Base128OutputStream& stream) const {
  ControlMessage::Serialize(stream);
  stream.WriteBytes(text_);
}

}  // namespace screensharing
