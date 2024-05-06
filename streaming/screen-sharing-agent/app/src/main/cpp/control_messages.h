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

#include <map>
#include <memory>
#include <vector>

#include <android/input.h>

#include "accessors/display_info.h"
#include "base128_input_stream.h"
#include "base128_output_stream.h"
#include "common.h"
#include "geom.h"

namespace screensharing {

// Common base class of all control messages.
class ControlMessage {
public:
  virtual ~ControlMessage() {}

  int32_t type() const { return type_; }

  virtual void Serialize(Base128OutputStream& stream) const;
  static std::unique_ptr<ControlMessage> Deserialize(Base128InputStream& stream);
  static std::unique_ptr<ControlMessage> Deserialize(int32_t type, Base128InputStream& stream);

protected:
  ControlMessage(int32_t type)
      : type_(type) {
  }

  int32_t type_;
};

// Common base class of all request and response control messages.
class CorrelatedMessage : public ControlMessage {
public:
  int32_t request_id() const { return request_id_; }

  virtual void Serialize(Base128OutputStream& stream) const;

protected:
  CorrelatedMessage(int32_t type, int32_t request_id)
      : ControlMessage(type),
        request_id_(request_id) {
  }

private:
  int32_t request_id_;

  DISALLOW_COPY_AND_ASSIGN(CorrelatedMessage);
};

// Messages received from the host.

// Represents an Android MotionEvent.
class MotionEventMessage : ControlMessage {
public:
  struct Pointer {
    Pointer(int32_t x, int32_t y, int32_t pointer_id, std::map<int32_t, float> axis_values)
        : x(x),
          y(y),
          pointer_id(pointer_id),
          axis_values(axis_values) {
    }
    Pointer() = default;

    // The horizontal coordinate of a touch corresponding to the display in its original orientation.
    int32_t x;
    // The vertical coordinate of a touch corresponding to the display in its original orientation.
    int32_t y;
    // The ID of the touch that stays the same when the touch point moves.
    int32_t pointer_id;
    // Values for the various axes of the pointer (e.g. scroll wheel, joystick, etc).
    std::map<int32_t, float> axis_values;
  };

  // Pointers are expected to be ordered according to their ids.
  // The action translates directly to android.view.MotionEvent.action.
  MotionEventMessage(std::vector<Pointer>&& pointers, int32_t action, int32_t button_state, int32_t action_button, int32_t display_id)
      : ControlMessage(TYPE),
        pointers_(pointers),
        action_(action),
        button_state_(button_state),
        action_button_(action_button),
        display_id_(display_id) {
  }
  virtual ~MotionEventMessage() {};

  // The touches, one for each finger. The pointers are ordered according to their ids.
  const std::vector<Pointer>& pointers() const { return pointers_; }

  // The action. See android.view.MotionEvent.action.
  int32_t action() const { return action_; }

  // See android.view.MotionEvent.getButtonState().
  int32_t button_state() const { return button_state_; }

  // See android.view.MotionEvent.getActionButton().
  int32_t action_button() const { return action_button_; }

  // The display device where the mouse event occurred. Zero indicates the main display.
  int32_t display_id() const { return display_id_; }

  static constexpr int TYPE = 1;

  static constexpr int MAX_POINTERS = 2;

private:
  friend class ControlMessage;

  static MotionEventMessage* Deserialize(Base128InputStream& stream);

  const std::vector<Pointer> pointers_;
  const int32_t action_;
  const int32_t button_state_;
  const int32_t action_button_;
  const int32_t display_id_;

  DISALLOW_COPY_AND_ASSIGN(MotionEventMessage);
};

// Represents a key being pressed or released on a keyboard.
class KeyEventMessage : ControlMessage {
public:
  KeyEventMessage(int32_t action, int32_t keycode, uint32_t meta_state)
      : ControlMessage(TYPE),
        action_(action),
        keycode_(keycode),
        meta_state_(meta_state) {
  }
  virtual ~KeyEventMessage() {};

  // AKEY_EVENT_ACTION_DOWN, AKEY_EVENT_ACTION_UP or ACTION_DOWN_AND_UP.
  int32_t action() const { return action_; }

  // The code of the pressed or released key. */
  int32_t keycode() const { return keycode_; }

  int32_t meta_state() const { return meta_state_; }

  static constexpr int TYPE = 2;

  static constexpr int ACTION_DOWN_AND_UP = 8;

private:
  friend class ControlMessage;

  static KeyEventMessage* Deserialize(Base128InputStream& stream);

  int32_t action_;
  int32_t keycode_;
  uint32_t meta_state_;

  DISALLOW_COPY_AND_ASSIGN(KeyEventMessage);
};

// Represents one or more characters typed on a keyboard.
class TextInputMessage : ControlMessage {
public:
  TextInputMessage(const std::u16string& text)
      : ControlMessage(TYPE),
        text_(text) {
  }
  virtual ~TextInputMessage() {};

  const std::u16string& text() const { return text_; }

  static constexpr int TYPE = 3;

private:
  friend class ControlMessage;

  static TextInputMessage* Deserialize(Base128InputStream& stream);

  std::u16string text_;

  DISALLOW_COPY_AND_ASSIGN(TextInputMessage);
};

// Represents one or more characters typed on a keyboard.
class SetDeviceOrientationMessage : ControlMessage {
public:
  SetDeviceOrientationMessage(int32_t orientation)
      : ControlMessage(TYPE),
        orientation_(orientation) {
  }
  virtual ~SetDeviceOrientationMessage() {};

  int32_t orientation() const { return orientation_; }

  static constexpr int TYPE = 4;

private:
  friend class ControlMessage;

  static SetDeviceOrientationMessage* Deserialize(Base128InputStream& stream);

  int32_t orientation_;

  DISALLOW_COPY_AND_ASSIGN(SetDeviceOrientationMessage);
};

// Sets maximum display streaming resolution.
class SetMaxVideoResolutionMessage : ControlMessage {
public:
  SetMaxVideoResolutionMessage(int32_t display_id, Size max_video_size)
      : ControlMessage(TYPE),
        display_id_(display_id),
        max_video_size_(max_video_size) {
  }
  virtual ~SetMaxVideoResolutionMessage() {};

  int32_t display_id() const { return display_id_; }
  const Size& max_video_size() const { return max_video_size_; }

  static constexpr int TYPE = 5;

private:
  friend class ControlMessage;

  static SetMaxVideoResolutionMessage* Deserialize(Base128InputStream& stream);

  int32_t display_id_;
  Size max_video_size_;

  DISALLOW_COPY_AND_ASSIGN(SetMaxVideoResolutionMessage);
};

// Starts video stream if it was stopped, otherwise has no effect.
class StartVideoStreamMessage : ControlMessage {
public:
  StartVideoStreamMessage(int32_t display_id, Size max_video_size)
      : ControlMessage(TYPE),
        display_id_(display_id),
        max_video_size_(max_video_size) {
  }
  virtual ~StartVideoStreamMessage() {};

  int32_t display_id() const { return display_id_; }
  const Size& max_video_size() const { return max_video_size_; }

  static constexpr int TYPE = 6;

private:
  friend class ControlMessage;

  static StartVideoStreamMessage* Deserialize(Base128InputStream& stream);

  int32_t display_id_;
  Size max_video_size_;

  DISALLOW_COPY_AND_ASSIGN(StartVideoStreamMessage);
};

// Stops video stream if it was started, otherwise has no effect.
class StopVideoStreamMessage : ControlMessage {
public:
  StopVideoStreamMessage(int32_t display_id)
      : ControlMessage(TYPE),
        display_id_(display_id) {
  }
  virtual ~StopVideoStreamMessage() {};

  int32_t display_id() const { return display_id_; }

  static constexpr int TYPE = 7;

private:
  friend class ControlMessage;

  static StopVideoStreamMessage* Deserialize(Base128InputStream& stream);

  int32_t display_id_;

  DISALLOW_COPY_AND_ASSIGN(StopVideoStreamMessage);
};

// Sets contents of the clipboard and requests notifications of clipboard changes.
class StartClipboardSyncMessage : ControlMessage {
public:
  StartClipboardSyncMessage(int max_synced_length, std::string&& text)
      : ControlMessage(TYPE),
        max_synced_length_(max_synced_length),
        text_(text) {
  }
  virtual ~StartClipboardSyncMessage() {};

  const std::string& text() const { return text_; }
  int max_synced_length() const { return max_synced_length_; }

  static constexpr int TYPE = 8;

private:
  friend class ControlMessage;

  static StartClipboardSyncMessage* Deserialize(Base128InputStream& stream);

  int max_synced_length_;
  std::string text_;

  DISALLOW_COPY_AND_ASSIGN(StartClipboardSyncMessage);
};

// Stops notifications of clipboard changes.
class StopClipboardSyncMessage : ControlMessage {
public:
  StopClipboardSyncMessage()
      : ControlMessage(TYPE) {
  }
  virtual ~StopClipboardSyncMessage() {};

  static constexpr int TYPE = 9;

private:
  friend class ControlMessage;

  static StopClipboardSyncMessage* Deserialize(Base128InputStream& stream);

  DISALLOW_COPY_AND_ASSIGN(StopClipboardSyncMessage);
};

// Requests a device state (folding pose) change. A DeviceStateNotification message will be sent
// when and if the device state actually changes. If state is equal to PHYSICAL_STATE, the device
// will return to its actual physical state.
class RequestDeviceStateMessage : ControlMessage {
public:
  RequestDeviceStateMessage(int state)
      : ControlMessage(TYPE),
        state_(state) {
  }
  virtual ~RequestDeviceStateMessage() = default;

  int state() const { return state_; }

  static constexpr int PHYSICAL_STATE = -1;

  static constexpr int TYPE = 10;

private:
  friend class ControlMessage;

  static RequestDeviceStateMessage* Deserialize(Base128InputStream& stream);

  int state_;

  DISALLOW_COPY_AND_ASSIGN(RequestDeviceStateMessage);
};

// Asks the agent to send back configurations of all displays.
class DisplayConfigurationRequest : public CorrelatedMessage {
public:
  DisplayConfigurationRequest(int32_t request_id)
      : CorrelatedMessage(TYPE, request_id) {
  }
  virtual ~DisplayConfigurationRequest() {};

  static constexpr int TYPE = 11;

private:
  friend class ControlMessage;

  static DisplayConfigurationRequest* Deserialize(Base128InputStream& stream);

  DISALLOW_COPY_AND_ASSIGN(DisplayConfigurationRequest);
};

// Messages sent to the host.

// Error response to a request message.
class ErrorResponse : public CorrelatedMessage {
public:
  ErrorResponse(int32_t request_id, const std::string& error_message)
      : CorrelatedMessage(TYPE, request_id),
        error_message_(error_message) {
  }
  ErrorResponse(int32_t request_id, std::string&& error_message)
      : CorrelatedMessage(TYPE, request_id),
        error_message_(error_message) {
  }
  virtual ~ErrorResponse() {};

  const std::string& error_message() const { return error_message_; }

  virtual void Serialize(Base128OutputStream& stream) const;

  static constexpr int TYPE = 12;

private:
  friend class ControlMessage;

  std::string error_message_;

  DISALLOW_COPY_AND_ASSIGN(ErrorResponse);
};

// Parameters of all device displays. Sent in response to DisplayConfigurationRequest.
class DisplayConfigurationResponse : public CorrelatedMessage {
public:
  DisplayConfigurationResponse(int32_t request_id, std::vector<std::pair<int32_t, DisplayInfo>>&& displays)
      : CorrelatedMessage(TYPE, request_id),
        displays_(displays) {
  }
  virtual ~DisplayConfigurationResponse() {}

  const std::vector<std::pair<int32_t, DisplayInfo>>& displays() const { return displays_; }

  virtual void Serialize(Base128OutputStream& stream) const;

  static constexpr int TYPE = 13;

private:
  friend class ControlMessage;

  std::vector<std::pair<int32_t, DisplayInfo>> displays_;

  DISALLOW_COPY_AND_ASSIGN(DisplayConfigurationResponse);
};

// Notification of clipboard content change.
class ClipboardChangedNotification : ControlMessage {
public:
  ClipboardChangedNotification(const std::string& text)
      : ControlMessage(TYPE),
        text_(text) {
  }
  ClipboardChangedNotification(std::string&& text)
      : ControlMessage(TYPE),
        text_(text) {
  }
  virtual ~ClipboardChangedNotification() {};

  const std::string& text() const { return text_; }

  virtual void Serialize(Base128OutputStream& stream) const;

  static constexpr int TYPE = 14;

private:
  friend class ControlMessage;

  std::string text_;

  DISALLOW_COPY_AND_ASSIGN(ClipboardChangedNotification);
};

// Notification of supported device states.
class SupportedDeviceStatesNotification : ControlMessage {
public:
  SupportedDeviceStatesNotification(const std::string& text)
      : ControlMessage(TYPE),
        text_(text) {
  }
  SupportedDeviceStatesNotification(std::string&& text)
      : ControlMessage(TYPE),
        text_(text) {
  }
  virtual ~SupportedDeviceStatesNotification() = default;

  const std::string& text() const { return text_; }

  virtual void Serialize(Base128OutputStream& stream) const;

  static constexpr int TYPE = 15;

private:
  friend class ControlMessage;

  std::string text_;

  DISALLOW_COPY_AND_ASSIGN(SupportedDeviceStatesNotification);
};

// Notification of a device state change. One such notification is always sent when the screen
// sharing agent starts on a foldable device.
class DeviceStateNotification : ControlMessage {
public:
  DeviceStateNotification(int32_t device_state)
      : ControlMessage(TYPE),
        device_state_(device_state) {
  }
  virtual ~DeviceStateNotification() = default;

  int32_t device_state() const { return device_state_; }

  virtual void Serialize(Base128OutputStream& stream) const;

  static constexpr int TYPE = 16;

private:
  friend class ControlMessage;

  int32_t device_state_;

  DISALLOW_COPY_AND_ASSIGN(DeviceStateNotification);
};

// Notification of an added display.
class DisplayAddedNotification : ControlMessage {
public:
  DisplayAddedNotification(int32_t display_id)
      : ControlMessage(TYPE),
        display_id_(display_id) {
  }
  virtual ~DisplayAddedNotification() = default;

  int32_t display_id() const { return display_id_; }

  virtual void Serialize(Base128OutputStream& stream) const;

  static constexpr int TYPE = 17;

private:
  friend class ControlMessage;

  int32_t display_id_;

  DISALLOW_COPY_AND_ASSIGN(DisplayAddedNotification);
};

// Notification of a removed display.
class DisplayRemovedNotification : ControlMessage {
public:
  DisplayRemovedNotification(int32_t display_id)
      : ControlMessage(TYPE),
        display_id_(display_id) {
  }
  virtual ~DisplayRemovedNotification() = default;

  int32_t display_id() const { return display_id_; }

  virtual void Serialize(Base128OutputStream& stream) const;

  static constexpr int TYPE = 18;

private:
  friend class ControlMessage;

  int32_t display_id_;

  DISALLOW_COPY_AND_ASSIGN(DisplayRemovedNotification);
};

// Queries the current UI settings from the device.
class UiSettingsRequest : public CorrelatedMessage {
public:
  UiSettingsRequest(int32_t request_id)
      : CorrelatedMessage(TYPE, request_id) {
  }
  virtual ~UiSettingsRequest() = default;

  virtual void Serialize(Base128OutputStream& stream) const;

  static constexpr int TYPE = 19;

private:
  friend class ControlMessage;

  static UiSettingsRequest* Deserialize(Base128InputStream& stream);

  DISALLOW_COPY_AND_ASSIGN(UiSettingsRequest);
};

// The current UI settings read from the device.
class UiSettingsResponse : public CorrelatedMessage {
public:
  UiSettingsResponse(int32_t request_id)
      : CorrelatedMessage(TYPE, request_id) {
  }
  virtual ~UiSettingsResponse() = default;

  virtual void Serialize(Base128OutputStream& stream) const;

  void set_dark_mode(bool dark_mode) {
    dark_mode_ = dark_mode;
  }

  static constexpr int TYPE = 20;

private:
  friend class ControlMessage;

  bool dark_mode_;

  DISALLOW_COPY_AND_ASSIGN(UiSettingsResponse);
};

// Changes the DarkMode setting on the device.
class SetDarkModeMessage : ControlMessage {
public:
  SetDarkModeMessage(bool dark_mode)
      : ControlMessage(TYPE),
        dark_mode_(dark_mode) {
  }
  virtual ~SetDarkModeMessage() = default;

  virtual void Serialize(Base128OutputStream& stream) const;

  bool dark_mode() const {
    return dark_mode_;
  }

  static constexpr int TYPE = 21;

private:
  friend class ControlMessage;

  static SetDarkModeMessage* Deserialize(Base128InputStream& stream);

  bool dark_mode_;

  DISALLOW_COPY_AND_ASSIGN(SetDarkModeMessage);
};

}  // namespace screensharing
