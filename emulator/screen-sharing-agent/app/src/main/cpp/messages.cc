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

#include "messages.h"

#include "log.h"

namespace screensharing {

using namespace std;

unique_ptr<Message> Message::deserialize(Base128InputStream& stream) {
  int32_t type = stream.ReadInt32();
  switch (type) {
    case MouseEventMessage::TYPE:
      return unique_ptr<Message>(MouseEventMessage::deserialize(stream));

    case KeyEventMessage::TYPE:
      return unique_ptr<Message>(KeyEventMessage::deserialize(stream));

    case TextInputMessage::TYPE:
      return unique_ptr<Message>(TextInputMessage::deserialize(stream));

    default:
      Log::Fatal("Unexpected message type %d", type);
  }
}

MouseEventMessage* MouseEventMessage::deserialize(Base128InputStream& stream) {
  int32_t x = stream.ReadInt32();
  int32_t y = stream.ReadInt32();
  uint32_t buttons = stream.ReadUInt32();
  uint32_t display_id = stream.ReadUInt32();
  return new MouseEventMessage(x, y, buttons, display_id);
}

KeyEventMessage* KeyEventMessage::deserialize(Base128InputStream& stream) {
  int32_t action = stream.ReadInt32();
  int32_t keycode = stream.ReadInt32();
  uint32_t meta_state = stream.ReadUInt32();
  return new KeyEventMessage(action, keycode, meta_state);
}

TextInputMessage* TextInputMessage::deserialize(Base128InputStream& stream) {
  u16string text = stream.ReadString16();
  return new TextInputMessage(text);
}

}  // namespace screensharing
