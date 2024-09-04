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

#include <cinttypes>
#include <map>
#include <memory>
#include <vector>

#include <android/input.h>

#include "accessors/device_state_manager.h"
#include "accessors/display_info.h"
#include "base128_input_stream.h"
#include "base128_output_stream.h"
#include "common.h"
#include "geom.h"

namespace screensharing {

// Common base class of all control messages.
class ControlMessage {
public:
  virtual ~ControlMessage() = default;

  [[nodiscard]] int32_t type() const { return type_; }

  virtual void Serialize(Base128OutputStream& stream) const;
  static std::unique_ptr<ControlMessage> Deserialize(Base128InputStream& stream);
  static std::unique_ptr<ControlMessage> Deserialize(int32_t type, Base128InputStream& stream);

protected:
  explicit ControlMessage(int32_t type)
      : type_(type) {
  }

  int32_t type_;
};

// Common base class of all request and response control messages.
class CorrelatedMessage : public ControlMessage {
public:
  [[nodiscard]] int32_t request_id() const { return request_id_; }

  void Serialize(Base128OutputStream& stream) const override;

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
          axis_values(std::move(axis_values)) {
    }
    Pointer() = default;

    // The horizontal coordinate of a touch corresponding to the display in its original orientation.
    int32_t x = 0;
    // The vertical coordinate of a touch corresponding to the display in its original orientation.
    int32_t y = 0;
    // The ID of the touch that stays the same when the touch point moves.
    int32_t pointer_id = 0;
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
  ~MotionEventMessage() override = default;

  // The touches, one for each finger. The pointers are ordered according to their ids.
  [[nodiscard]] const std::vector<Pointer>& pointers() const { return pointers_; }

  // The action. See android.view.MotionEvent.action.
  [[nodiscard]] int32_t action() const { return action_; }

  // See android.view.MotionEvent.getButtonState().
  [[nodiscard]] int32_t button_state() const { return button_state_; }

  // See android.view.MotionEvent.getActionButton().
  [[nodiscard]] int32_t action_button() const { return action_button_; }

  // The display device where the mouse event occurred. Zero indicates the main display.
  [[nodiscard]] int32_t display_id() const { return display_id_; }

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
  ~KeyEventMessage() override = default;

  // AKEY_EVENT_ACTION_DOWN, AKEY_EVENT_ACTION_UP or ACTION_DOWN_AND_UP.
  [[nodiscard]] int32_t action() const { return action_; }

  // The code of the pressed or released key. */
  [[nodiscard]] int32_t keycode() const { return keycode_; }

  [[nodiscard]] int32_t meta_state() const { return meta_state_; }

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
  explicit TextInputMessage(std::u16string  text)
      : ControlMessage(TYPE),
        text_(std::move(text)) {
  }
  ~TextInputMessage() override = default;

  [[nodiscard]] const std::u16string& text() const { return text_; }

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
  explicit SetDeviceOrientationMessage(int32_t orientation)
      : ControlMessage(TYPE),
        orientation_(orientation) {
  }
  ~SetDeviceOrientationMessage() override = default;

  [[nodiscard]] int32_t orientation() const { return orientation_; }

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
  ~SetMaxVideoResolutionMessage() override = default;

  [[nodiscard]] int32_t display_id() const { return display_id_; }
  [[nodiscard]] const Size& max_video_size() const { return max_video_size_; }

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
  ~StartVideoStreamMessage() override = default;

  [[nodiscard]] int32_t display_id() const { return display_id_; }
  [[nodiscard]] const Size& max_video_size() const { return max_video_size_; }

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
  explicit StopVideoStreamMessage(int32_t display_id)
      : ControlMessage(TYPE),
        display_id_(display_id) {
  }
  ~StopVideoStreamMessage() override = default;

  [[nodiscard]] int32_t display_id() const { return display_id_; }

  static constexpr int TYPE = 7;

private:
  friend class ControlMessage;

  static StopVideoStreamMessage* Deserialize(Base128InputStream& stream);

  int32_t display_id_;

  DISALLOW_COPY_AND_ASSIGN(StopVideoStreamMessage);
};

// Starts audio stream if it was stopped, otherwise has no effect.
class StartAudioStreamMessage : ControlMessage {
public:
  StartAudioStreamMessage()
      : ControlMessage(TYPE) {
  }
  ~StartAudioStreamMessage() override = default;

  static constexpr int TYPE = 8;

private:
  friend class ControlMessage;

  static StartAudioStreamMessage* Deserialize(Base128InputStream& stream);

  DISALLOW_COPY_AND_ASSIGN(StartAudioStreamMessage);
};

// Stops audio stream if it was started, otherwise has no effect.
class StopAudioStreamMessage : ControlMessage {
public:
  StopAudioStreamMessage()
      : ControlMessage(TYPE) {
  }
  ~StopAudioStreamMessage() override = default;

  static constexpr int TYPE = 9;

private:
  friend class ControlMessage;

  static StopAudioStreamMessage* Deserialize(Base128InputStream& stream);

  DISALLOW_COPY_AND_ASSIGN(StopAudioStreamMessage);
};

// Sets contents of the clipboard and requests notifications of clipboard changes.
class StartClipboardSyncMessage : ControlMessage {
public:
  StartClipboardSyncMessage(int max_synced_length, std::string&& text)
      : ControlMessage(TYPE),
        max_synced_length_(max_synced_length),
        text_(text) {
  }
  ~StartClipboardSyncMessage() override = default;

  [[nodiscard]] const std::string& text() const { return text_; }
  [[nodiscard]] int max_synced_length() const { return max_synced_length_; }

  static constexpr int TYPE = 10;

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
  ~StopClipboardSyncMessage() override = default;

  static constexpr int TYPE = 11;

private:
  friend class ControlMessage;

  static StopClipboardSyncMessage* Deserialize(Base128InputStream& stream);

  DISALLOW_COPY_AND_ASSIGN(StopClipboardSyncMessage);
};

// Requests a device state (folding pose) change. A DeviceStateNotification message will be sent
// when and if the device state actually changes. If device_state_id is equal to PHYSICAL_STATE, the device
// will return to its actual physical state.
class RequestDeviceStateMessage : ControlMessage {
public:
  explicit RequestDeviceStateMessage(int device_state_id)
      : ControlMessage(TYPE),
        device_state_id_(device_state_id) {
  }
  ~RequestDeviceStateMessage() override = default;

  [[nodiscard]] int state_id() const { return device_state_id_; }

  static constexpr int PHYSICAL_STATE = -1;

  static constexpr int TYPE = 12;

private:
  friend class ControlMessage;

  static RequestDeviceStateMessage* Deserialize(Base128InputStream& stream);

  int device_state_id_;

  DISALLOW_COPY_AND_ASSIGN(RequestDeviceStateMessage);
};

// Asks the agent to send back configurations of all displays.
class DisplayConfigurationRequest : public CorrelatedMessage {
public:
  explicit DisplayConfigurationRequest(int32_t request_id)
      : CorrelatedMessage(TYPE, request_id) {
  }
  ~DisplayConfigurationRequest() override = default;

  static constexpr int TYPE = 13;

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
  ~ErrorResponse() override = default;

  [[nodiscard]] const std::string& error_message() const { return error_message_; }

  void Serialize(Base128OutputStream& stream) const override;

  static constexpr int TYPE = 14;

private:
  friend class ControlMessage;

  std::string error_message_;

  DISALLOW_COPY_AND_ASSIGN(ErrorResponse);
};

// Parameters of all device displays. Sent in response to DisplayConfigurationRequest.
class DisplayConfigurationResponse : public CorrelatedMessage {
public:
  DisplayConfigurationResponse(int32_t request_id, std::map<int32_t, DisplayInfo>&& displays)
      : CorrelatedMessage(TYPE, request_id),
        displays_(displays) {
  }
  ~DisplayConfigurationResponse() override = default;

  [[nodiscard]] const std::map<int32_t, DisplayInfo>& displays() const { return displays_; }

  void Serialize(Base128OutputStream& stream) const override;

  static constexpr int TYPE = 15;

private:
  friend class ControlMessage;

  std::map<int32_t, DisplayInfo> displays_;

  DISALLOW_COPY_AND_ASSIGN(DisplayConfigurationResponse);
};

// Notification of clipboard content change.
class ClipboardChangedNotification : ControlMessage {
public:
  explicit ClipboardChangedNotification(const std::string& text)
      : ControlMessage(TYPE),
        text_(text) {
  }
  explicit ClipboardChangedNotification(std::string&& text)
      : ControlMessage(TYPE),
        text_(text) {
  }
  ~ClipboardChangedNotification() override = default;

  [[nodiscard]] const std::string& text() const { return text_; }

  void Serialize(Base128OutputStream& stream) const override;

  static constexpr int TYPE = 16;

private:
  friend class ControlMessage;

  std::string text_;

  DISALLOW_COPY_AND_ASSIGN(ClipboardChangedNotification);
};

// Notification of supported device states.
class SupportedDeviceStatesNotification : ControlMessage {
public:
  explicit SupportedDeviceStatesNotification(const std::vector<DeviceState>& device_states, int32_t device_state_id)
      : ControlMessage(TYPE),
        device_states_(device_states),
        device_state_id_(device_state_id) {
  }
  ~SupportedDeviceStatesNotification() override = default;

  [[nodiscard]] const std::vector<DeviceState>& device_states() const { return device_states_; }
  [[nodiscard]] int32_t device_state_id() const { return device_state_id_; }

  void Serialize(Base128OutputStream& stream) const override;

  static constexpr int TYPE = 17;

private:
  friend class ControlMessage;

  const std::vector<DeviceState>& device_states_;
  int32_t device_state_id_;

  DISALLOW_COPY_AND_ASSIGN(SupportedDeviceStatesNotification);
};

// Notification of a device state change. One such notification is always sent when the screen
// sharing agent starts on a foldable device.
class DeviceStateNotification : ControlMessage {
public:
  explicit DeviceStateNotification(int32_t device_state_id)
      : ControlMessage(TYPE),
        device_state_id_(device_state_id) {
  }
  ~DeviceStateNotification() override = default;

  [[nodiscard]] int32_t device_state_id() const { return device_state_id_; }

  void Serialize(Base128OutputStream& stream) const override;

  static constexpr int TYPE = 18;

private:
  friend class ControlMessage;

  int32_t device_state_id_;

  DISALLOW_COPY_AND_ASSIGN(DeviceStateNotification);
};

// Notification of an added or a changed display.
class DisplayAddedOrChangedNotification : ControlMessage {
public:
  explicit DisplayAddedOrChangedNotification(int32_t display_id, Size logical_size, int32_t rotation, int32_t display_type)
      : ControlMessage(TYPE),
        display_id_(display_id),
        logical_size_(std::move(logical_size)),
        rotation_(rotation),
        display_type_(display_type) {
  }
  ~DisplayAddedOrChangedNotification() override = default;

  [[nodiscard]] int32_t display_id() const { return display_id_; }
  [[nodiscard]] const Size& logical_size() const { return logical_size_; }
  [[nodiscard]] int32_t rotation() const { return rotation_; }
  [[nodiscard]] int32_t display_type() const { return display_type_; }
  [[nodiscard]] std::string ToDebugString() const;

  void Serialize(Base128OutputStream& stream) const override;

  static constexpr int TYPE = 19;

private:
  friend class ControlMessage;

  int32_t display_id_;
  Size logical_size_;
  int32_t rotation_;
  int32_t display_type_;

  DISALLOW_COPY_AND_ASSIGN(DisplayAddedOrChangedNotification);
};

// Notification of a removed display.
class DisplayRemovedNotification : ControlMessage {
public:
  explicit DisplayRemovedNotification(int32_t display_id)
      : ControlMessage(TYPE),
        display_id_(display_id) {
  }
  ~DisplayRemovedNotification() override = default;

  [[nodiscard]] int32_t display_id() const { return display_id_; }

  void Serialize(Base128OutputStream& stream) const override;

  static constexpr int TYPE = 20;

private:
  friend class ControlMessage;

  int32_t display_id_;

  DISALLOW_COPY_AND_ASSIGN(DisplayRemovedNotification);
};

// Queries the current UI settings from the device.
class UiSettingsRequest : public CorrelatedMessage {
public:
  explicit UiSettingsRequest(int32_t request_id)
      : CorrelatedMessage(TYPE, request_id) {
  }
  ~UiSettingsRequest() override = default;

  void Serialize(Base128OutputStream& stream) const override;

  static constexpr int TYPE = 21;

private:
  friend class ControlMessage;

  static UiSettingsRequest* Deserialize(Base128InputStream& stream);

  DISALLOW_COPY_AND_ASSIGN(UiSettingsRequest);
};

// The current UI settings read from the device.
class UiSettingsResponse : public CorrelatedMessage {
public:
  explicit UiSettingsResponse(int32_t request_id)
      : CorrelatedMessage(TYPE, request_id) {
  }
  ~UiSettingsResponse() override = default;

  void Serialize(Base128OutputStream& stream) const override;

  void copy(UiSettingsResponse* result) const {
    result->set_dark_mode(dark_mode_);
    result->set_font_scale(font_scale_);
    result->set_density(density_);
    result->set_talkback_on(talkback_on_);
    result->set_select_to_speak_on(select_to_speak_on_);
    result->set_gesture_navigation(gesture_navigation_);
    result->set_debug_layout(debug_layout_);
    result->set_foreground_application_id(foreground_application_id_);
    result->set_app_locale(app_locale_);
    result->set_original_values(original_values_);
    result->set_font_scale_settable(font_scale_settable_);
    result->set_density_settable(density_settable_);
    result->set_talkback_installed(talkback_installed_);
    result->set_gesture_overlay_installed(gesture_overlay_installed_);
  }

  void set_dark_mode(bool dark_mode) {
    dark_mode_ = dark_mode;
  }

  [[nodiscard]] bool dark_mode() const {
    return dark_mode_;
  }

  void set_font_scale(int32_t font_scale) {
    font_scale_ = font_scale;
  }

  [[nodiscard]] int32_t font_scale() const {
    return font_scale_;
  }

  void set_density(int32_t density) {
    density_ = density;
  }

  [[nodiscard]] int32_t density() const {
    return density_;
  }

  void set_talkback_on(bool on) {
    talkback_on_ = on;
  }

  [[nodiscard]] bool talkback_on() const {
    return talkback_on_;
  }

  void set_select_to_speak_on(bool on) {
    select_to_speak_on_ = on;
  }

  [[nodiscard]] bool select_to_speak_on() const {
    return select_to_speak_on_;
  }

  void set_gesture_navigation(bool gesture_navigation) {
    gesture_navigation_ = gesture_navigation;
  }

  [[nodiscard]] bool gesture_navigation() const {
    return gesture_navigation_;
  }

  void set_debug_layout(bool debug_layout) {
    debug_layout_ = debug_layout;
  }

  [[nodiscard]] bool debug_layout() const {
    return debug_layout_;
  }

  void set_foreground_application_id(const std::string& foreground_application_id) {
    foreground_application_id_ = foreground_application_id;
  }

  [[nodiscard]] const std::string& foreground_application_id() const {
    return foreground_application_id_;
  }

  void set_app_locale(const std::string& app_locale) {
    app_locale_ = app_locale;
  }

  [[nodiscard]] const std::string& app_locale() const {
    return app_locale_;
  }

  void set_original_values(bool original_values) {
    original_values_ = original_values;
  }

  [[nodiscard]] bool original_values() const {
    return original_values_;
  }

  void set_font_scale_settable(bool settable) {
    font_scale_settable_ = settable;
  }

  void set_density_settable(bool settable) {
    density_settable_ = settable;
  }

  void set_talkback_installed(bool installed) {
    talkback_installed_ = installed;
  }

  [[nodiscard]] bool talkback_installed() const {
    return talkback_installed_;
  }

  void set_gesture_overlay_installed(bool gesture_overlay_installed) {
    gesture_overlay_installed_ = gesture_overlay_installed;
  }

  [[nodiscard]] bool gesture_overlay_installed() const {
    return gesture_overlay_installed_;
  }

  static constexpr int TYPE = 22;

private:
  friend class ControlMessage;

  bool dark_mode_ = false;
  int32_t font_scale_ = 0;
  int32_t density_ = 0;
  bool talkback_on_ = false;
  bool select_to_speak_on_ = false;
  bool gesture_navigation_ = false;
  bool debug_layout_ = false;
  std::string foreground_application_id_;
  std::string app_locale_;

  bool original_values_ = false;

  bool font_scale_settable_= false;
  bool density_settable_ = false;
  bool talkback_installed_ = false;
  bool gesture_overlay_installed_ = false;

  DISALLOW_COPY_AND_ASSIGN(UiSettingsResponse);
};

// Changes a UI setting on the device.
class UiSettingsChangeRequest : public CorrelatedMessage {
public:
  enum UiCommand {
    DARK_MODE,
    FONT_SCALE,
    DENSITY,
    TALKBACK,
    SELECT_TO_SPEAK,
    GESTURE_NAVIGATION,
    DEBUG_LAYOUT,
    APP_LOCALE,
  };

  static UiSettingsChangeRequest* createDarkModeChangeRequest(int32_t request_id, bool dark_mode);
  static UiSettingsChangeRequest* createFontScaleChangeRequest(int32_t request_id, int32_t font_scale);
  static UiSettingsChangeRequest* createDensityChangeRequest(int32_t request_id, int32_t density);
  static UiSettingsChangeRequest* createTalkbackChangeRequest(int32_t request_id, bool talkback);
  static UiSettingsChangeRequest* createSelectToSpeakChangeRequest(int32_t request_id, bool select_to_speak);
  static UiSettingsChangeRequest* createGestureNavigationChangeRequest(int32_t request_id, bool gesture_navigation);
  static UiSettingsChangeRequest* createDebugLayoutChangeRequest(int32_t request_id, bool debug_layout);
  static UiSettingsChangeRequest* createAppLocaleChangeRequest(int32_t request_id, std::string application_id, std::string locale);

  ~UiSettingsChangeRequest() override = default;

  [[nodiscard]] UiCommand command() const {
    return command_;
  }

  [[nodiscard]] bool dark_mode() const {
    return dark_mode_;
  }

  [[nodiscard]] int32_t font_scale() const {
    return font_scale_;
  }

  [[nodiscard]] int32_t density() const {
    return density_;
  }

  [[nodiscard]] bool talkback() const {
    return talkback_;
  }

  [[nodiscard]] bool select_to_speak() const {
    return select_to_speak_;
  }

  [[nodiscard]] bool gesture_navigation() const {
    return gesture_navigation_;
  }

  [[nodiscard]] bool debug_layout() const {
    return debug_layout_;
  }

  [[nodiscard]] std::string application_id() const {
    return application_id_;
  }

  [[nodiscard]] std::string locale() const {
    return locale_;
  }

  static constexpr int TYPE = 23;

private:
  friend class ControlMessage;

  UiSettingsChangeRequest(int32_t request_id, UiCommand command)
    : CorrelatedMessage(TYPE, request_id),
      command_(command) {
  }

  static UiSettingsChangeRequest* Deserialize(Base128InputStream& stream);

  UiCommand command_;
  union {
    bool dark_mode_;
    int32_t font_scale_;
    int32_t density_;
    bool talkback_;
    bool select_to_speak_;
    bool gesture_navigation_;
    bool debug_layout_;
  };
  std::string application_id_;
  std::string locale_;

  DISALLOW_COPY_AND_ASSIGN(UiSettingsChangeRequest);
};

// The state of original values after executing a command.
class UiSettingsChangeResponse : public CorrelatedMessage {
public:
  explicit UiSettingsChangeResponse(int32_t request_id)
      : CorrelatedMessage(TYPE, request_id) {
  }
  ~UiSettingsChangeResponse() override = default;

  void Serialize(Base128OutputStream& stream) const override;

  void set_original_values(bool original_values) {
    original_values_ = original_values;
  }

  [[nodiscard]] bool original_values() const {
    return original_values_;
  }
  static constexpr int TYPE = 24;

private:
  friend class ControlMessage;

  bool original_values_ = false;

  DISALLOW_COPY_AND_ASSIGN(UiSettingsChangeResponse);
};

// Resets the UI settings to their original values
class ResetUiSettingsRequest : public CorrelatedMessage {
public:
  explicit ResetUiSettingsRequest(int32_t request_id)
      : CorrelatedMessage(TYPE, request_id) {
  }
  ~ResetUiSettingsRequest() override = default;

  static constexpr int TYPE = 25;

private:
  friend class ControlMessage;

  static ResetUiSettingsRequest* Deserialize(Base128InputStream& stream);

  DISALLOW_COPY_AND_ASSIGN(ResetUiSettingsRequest);
};

}  // namespace screensharing
