/*
 * Copyright 2023 The Android Open Source Project
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

#include "virtual_input_device.h"

#include <atomic>
#include <cerrno>
#include <cmath>
#include <string>

#include <android/input.h>
#include <android/keycodes.h>
#include <fcntl.h>
#include <linux/uinput.h>
#include <unistd.h>

#include "log.h"
#include "string_printf.h"

namespace screensharing {

using namespace std;
using namespace std::chrono;

enum class DeviceType { DPAD, KEYBOARD, MOUSE, TOUCHSCREEN, STYLUS };

namespace {

const char* const TYPE_NAMES[] = { "Dpad", "Keyboard", "Mouse", "Touchscreen", "Stylus" };

constexpr int32_t INVALID_FD = -1;
constexpr int32_t VENDOR_ID = 0x18D1;  // Google vendor id according to http://www.linux-usb.org/usb.ids.

const map<int, UinputAction> TOUCH_ACTION_MAPPING = {
    {AMOTION_EVENT_ACTION_DOWN, UinputAction::PRESS},
    {AMOTION_EVENT_ACTION_UP, UinputAction::RELEASE},
    {AMOTION_EVENT_ACTION_MOVE, UinputAction::MOVE},
    {AMOTION_EVENT_ACTION_CANCEL, UinputAction::CANCEL},
};

atomic_int32_t next_phys_id;

const char* GetName(DeviceType type) {
  return TYPE_NAMES[static_cast<int32_t>(type)];
}

int32_t GetProductId(DeviceType type) {
  return static_cast<int32_t>(type) + 1;
}

string GetPhysName(DeviceType type) {
  return StringPrintf("studio.screen.sharing.%s:%d", GetName(type), next_phys_id++);
}

void CloseAndReportError(int fd) {
  Log::E("Error creating uinput device: %s", strerror(errno));
  close(fd);
}

// Creates a new uinput device and returns its file descriptor. The values of screen_width and screen_height
// arguments are ignored unless device_type is TOUCHSCREEN or STYLUS.
int OpenUInput(DeviceType device_type, const char* phys, int32_t screen_width, int32_t screen_height) {
  int32_t fd(TEMP_FAILURE_RETRY(::open("/dev/uinput", O_WRONLY | O_NONBLOCK)));
  if (fd < 0) {
    Log::E("Error creating uinput device: %s", strerror(errno));
    return INVALID_FD;
  }

  ioctl(fd, UI_SET_PHYS, phys);
  ioctl(fd, UI_SET_EVBIT, EV_KEY);
  ioctl(fd, UI_SET_EVBIT, EV_SYN);

  switch (device_type) {
    case DeviceType::DPAD:
      for (const auto& [_, keyCode] : VirtualDpad::DPAD_KEY_CODE_MAPPING) {
        ioctl(fd, UI_SET_KEYBIT, keyCode);
      }
      break;

    case DeviceType::KEYBOARD:
      for (const auto& [_, keyCode] : VirtualKeyboard::KEY_CODE_MAPPING) {
        ioctl(fd, UI_SET_KEYBIT, keyCode);
      }
      break;

    case DeviceType::MOUSE:
      ioctl(fd, UI_SET_EVBIT, EV_REL);
      ioctl(fd, UI_SET_KEYBIT, BTN_LEFT);
      ioctl(fd, UI_SET_KEYBIT, BTN_RIGHT);
      ioctl(fd, UI_SET_KEYBIT, BTN_MIDDLE);
      ioctl(fd, UI_SET_KEYBIT, BTN_BACK);
      ioctl(fd, UI_SET_KEYBIT, BTN_FORWARD);
      ioctl(fd, UI_SET_RELBIT, REL_X);
      ioctl(fd, UI_SET_RELBIT, REL_Y);
      ioctl(fd, UI_SET_RELBIT, REL_WHEEL);
      ioctl(fd, UI_SET_RELBIT, REL_HWHEEL);
      break;

    case DeviceType::TOUCHSCREEN:
      ioctl(fd, UI_SET_EVBIT, EV_ABS);
      ioctl(fd, UI_SET_KEYBIT, BTN_TOUCH);
      ioctl(fd, UI_SET_ABSBIT, ABS_MT_SLOT);
      ioctl(fd, UI_SET_ABSBIT, ABS_MT_POSITION_X);
      ioctl(fd, UI_SET_ABSBIT, ABS_MT_POSITION_Y);
      ioctl(fd, UI_SET_ABSBIT, ABS_MT_TRACKING_ID);
      ioctl(fd, UI_SET_ABSBIT, ABS_MT_TOOL_TYPE);
      ioctl(fd, UI_SET_ABSBIT, ABS_MT_TOUCH_MAJOR);
      ioctl(fd, UI_SET_ABSBIT, ABS_MT_PRESSURE);
      ioctl(fd, UI_SET_PROPBIT, INPUT_PROP_DIRECT);
      break;

    case DeviceType::STYLUS:
      ioctl(fd, UI_SET_EVBIT, EV_ABS);
      ioctl(fd, UI_SET_KEYBIT, BTN_TOUCH);
      ioctl(fd, UI_SET_KEYBIT, BTN_STYLUS);
      ioctl(fd, UI_SET_KEYBIT, BTN_STYLUS2);
      ioctl(fd, UI_SET_KEYBIT, BTN_TOOL_PEN);
      ioctl(fd, UI_SET_KEYBIT, BTN_TOOL_RUBBER);
      ioctl(fd, UI_SET_ABSBIT, ABS_X);
      ioctl(fd, UI_SET_ABSBIT, ABS_Y);
      ioctl(fd, UI_SET_ABSBIT, ABS_TILT_X);
      ioctl(fd, UI_SET_ABSBIT, ABS_TILT_Y);
      ioctl(fd, UI_SET_ABSBIT, ABS_PRESSURE);
      ioctl(fd, UI_SET_PROPBIT, INPUT_PROP_DIRECT);
      break;
  }

  int version;
  if (ioctl(fd, UI_GET_VERSION, &version) == 0 && version >= 5) {
    uinput_setup setup{};
    strlcpy(setup.name, GetName(device_type), UINPUT_MAX_NAME_SIZE);
    setup.id.version = 1;
    setup.id.bustype = BUS_VIRTUAL;
    setup.id.vendor = VENDOR_ID;
    setup.id.product = GetProductId(device_type);
    if (device_type == DeviceType::TOUCHSCREEN) {
      uinput_abs_setup xAbsSetup {.code = ABS_MT_POSITION_X};
      xAbsSetup.absinfo.maximum = screen_width - 1;
      xAbsSetup.absinfo.minimum = 0;
      if (ioctl(fd, UI_ABS_SETUP, &xAbsSetup) != 0) {
        CloseAndReportError(fd);
        return INVALID_FD;
      }
      uinput_abs_setup yAbsSetup {.code = ABS_MT_POSITION_Y};
      yAbsSetup.absinfo.maximum = screen_height - 1;
      yAbsSetup.absinfo.minimum = 0;
      if (ioctl(fd, UI_ABS_SETUP, &yAbsSetup) != 0) {
        CloseAndReportError(fd);
        return INVALID_FD;
      }
      uinput_abs_setup majorAbsSetup {.code = ABS_MT_TOUCH_MAJOR};
      majorAbsSetup.absinfo.maximum = screen_width - 1;
      majorAbsSetup.absinfo.minimum = 0;
      if (ioctl(fd, UI_ABS_SETUP, &majorAbsSetup) != 0) {
        CloseAndReportError(fd);
        return INVALID_FD;
      }
      uinput_abs_setup pressureAbsSetup {.code = ABS_MT_PRESSURE};
      pressureAbsSetup.absinfo.maximum = VirtualInputDevice::MAX_PRESSURE;
      pressureAbsSetup.absinfo.minimum = 0;
      if (ioctl(fd, UI_ABS_SETUP, &pressureAbsSetup) != 0) {
        CloseAndReportError(fd);
        return INVALID_FD;
      }
      uinput_abs_setup slotAbsSetup {.code = ABS_MT_SLOT};
      slotAbsSetup.absinfo.maximum = VirtualInputDevice::MAX_POINTERS - 1;
      slotAbsSetup.absinfo.minimum = 0;
      if (ioctl(fd, UI_ABS_SETUP, &slotAbsSetup) != 0) {
        CloseAndReportError(fd);
        return INVALID_FD;
      }
      uinput_abs_setup trackingIdAbsSetup {.code = ABS_MT_TRACKING_ID};
      trackingIdAbsSetup.absinfo.maximum = VirtualInputDevice::MAX_POINTERS - 1;
      trackingIdAbsSetup.absinfo.minimum = 0;
      if (ioctl(fd, UI_ABS_SETUP, &trackingIdAbsSetup) != 0) {
        CloseAndReportError(fd);
        return INVALID_FD;
      }
    } else if (device_type == DeviceType::STYLUS) {
      uinput_abs_setup xAbsSetup {.code = ABS_X};
      xAbsSetup.absinfo.maximum = screen_width - 1;
      xAbsSetup.absinfo.minimum = 0;
      if (ioctl(fd, UI_ABS_SETUP, &xAbsSetup) != 0) {
        CloseAndReportError(fd);
        return INVALID_FD;
      }
      uinput_abs_setup yAbsSetup {.code = ABS_Y};
      yAbsSetup.absinfo.maximum = screen_height - 1;
      yAbsSetup.absinfo.minimum = 0;
      if (ioctl(fd, UI_ABS_SETUP, &yAbsSetup) != 0) {
        CloseAndReportError(fd);
        return INVALID_FD;
      }
      uinput_abs_setup tiltXAbsSetup {.code = ABS_TILT_X};
      tiltXAbsSetup.absinfo.maximum = 90;
      tiltXAbsSetup.absinfo.minimum = -90;
      if (ioctl(fd, UI_ABS_SETUP, &tiltXAbsSetup) != 0) {
        CloseAndReportError(fd);
        return INVALID_FD;
      }
      uinput_abs_setup tiltYAbsSetup {.code = ABS_TILT_Y};
      tiltYAbsSetup.absinfo.maximum = 90;
      tiltYAbsSetup.absinfo.minimum = -90;
      if (ioctl(fd, UI_ABS_SETUP, &tiltYAbsSetup) != 0) {
        CloseAndReportError(fd);
        return INVALID_FD;
      }
      uinput_abs_setup pressureAbsSetup {.code = ABS_PRESSURE};
      pressureAbsSetup.absinfo.maximum = VirtualInputDevice::MAX_PRESSURE;
      pressureAbsSetup.absinfo.minimum = 0;
      if (ioctl(fd, UI_ABS_SETUP, &pressureAbsSetup) != 0) {
        CloseAndReportError(fd);
        return INVALID_FD;
      }
    }
    if (ioctl(fd, UI_DEV_SETUP, &setup) != 0) {
      CloseAndReportError(fd);
      return INVALID_FD;
    }
  } else {
    // UI_DEV_SETUP was not introduced until version 5. Try setting up manually.
    Log::I("Falling back to version %d manual setup", version);
    uinput_user_dev fallback{};
    strlcpy(fallback.name, GetName(device_type), UINPUT_MAX_NAME_SIZE);
    fallback.id.version = 1;
    fallback.id.bustype = BUS_VIRTUAL;
    fallback.id.vendor = VENDOR_ID;
    fallback.id.product = GetProductId(device_type);
    if (device_type == DeviceType::TOUCHSCREEN) {
      fallback.absmin[ABS_MT_POSITION_X] = 0;
      fallback.absmax[ABS_MT_POSITION_X] = screen_width - 1;
      fallback.absmin[ABS_MT_POSITION_Y] = 0;
      fallback.absmax[ABS_MT_POSITION_Y] = screen_height - 1;
      fallback.absmin[ABS_MT_TOUCH_MAJOR] = 0;
      fallback.absmax[ABS_MT_TOUCH_MAJOR] = screen_width - 1;
      fallback.absmin[ABS_MT_PRESSURE] = 0;
      fallback.absmax[ABS_MT_PRESSURE] = VirtualInputDevice::MAX_PRESSURE;
    } else if (device_type == DeviceType::STYLUS) {
      fallback.absmin[ABS_X] = 0;
      fallback.absmax[ABS_X] = screen_width - 1;
      fallback.absmin[ABS_Y] = 0;
      fallback.absmax[ABS_Y] = screen_height - 1;
      fallback.absmin[ABS_TILT_X] = -90;
      fallback.absmax[ABS_TILT_X] = 90;
      fallback.absmin[ABS_TILT_Y] = -90;
      fallback.absmax[ABS_TILT_Y] = 90;
      fallback.absmin[ABS_PRESSURE] = 0;
      fallback.absmax[ABS_PRESSURE] = VirtualInputDevice::MAX_PRESSURE;
    }
    if (TEMP_FAILURE_RETRY(write(fd, &fallback, sizeof(fallback))) != sizeof(fallback)) {
      CloseAndReportError(fd);
      return INVALID_FD;
    }
  }

  if (ioctl(fd, UI_DEV_CREATE) != 0) {
    CloseAndReportError(fd);
    return INVALID_FD;
  }

  return fd;
}

}  // namespace

VirtualInputDevice::VirtualInputDevice(string phys)
    : fd_(INVALID_FD),
      phys_(std::move(phys)) {
}

VirtualInputDevice::~VirtualInputDevice() {
  if (fd_ != INVALID_FD) {
    ioctl(fd_, UI_DEV_DESTROY);
  }
}

bool VirtualInputDevice::WriteInputEvent(uint16_t type, uint16_t code, int32_t value, nanoseconds event_time) {
  if (type == EV_KEY) {
    Log::D("VirtualInputDevice::WriteInputEvent(%u, %u, %d, %lld)", type, code, value, event_time.count());
  } else {
    Log::V("VirtualInputDevice::WriteInputEvent(%u, %u, %d, %lld)", type, code, value, event_time.count());
  }
  auto event_seconds = duration_cast<seconds>(event_time);
  auto event_microseconds = duration_cast<microseconds>(event_time - event_seconds);
  struct input_event ev = {.type = type, .code = code, .value = value};
  ev.input_event_sec = static_cast<decltype(ev.input_event_sec)>(event_seconds.count());
  ev.input_event_usec = static_cast<decltype(ev.input_event_usec)>(event_microseconds.count());

  return TEMP_FAILURE_RETRY(write(fd_, &ev, sizeof(struct input_event))) == sizeof(ev);
}

/** Utility method to write keyboard key events or mouse/stylus button events. */
bool VirtualInputDevice::WriteEvKeyEvent(
    int32_t android_code, int32_t android_action,
    const std::map<int, int>& ev_key_code_mapping, const std::map<int, UinputAction>& action_mapping, nanoseconds event_time) {
  auto evKeyCodeIterator = ev_key_code_mapping.find(android_code);
  if (evKeyCodeIterator == ev_key_code_mapping.end()) {
    Log::E("Unsupported native EV keycode for android code %d", android_code);
    return false;
  }
  auto actionIterator = action_mapping.find(android_action);
  if (actionIterator == action_mapping.end()) {
    Log::E("Unsupported native action for android action %d", android_action);
    return false;
  }
  auto action = static_cast<int32_t>(actionIterator->second);
  auto evKeyCode = static_cast<uint16_t>(evKeyCodeIterator->second);
  if (!WriteInputEvent(EV_KEY, evKeyCode, action, event_time)) {
    Log::E("Failed to write native action %d and EV keycode %u.", action, evKeyCode);
    return false;
  }
  if (!WriteInputEvent(EV_SYN, SYN_REPORT, 0, event_time)) {
    Log::E("Failed to write SYN_REPORT for EV_KEY event.");
    return false;
  }
  return true;
}

bool VirtualInputDevice::IsValid() const {
  return fd_ != INVALID_FD;
}

// --- VirtualKeyboard ---

VirtualKeyboard::VirtualKeyboard()
    : VirtualInputDevice(GetPhysName(DeviceType::KEYBOARD)) {
  fd_ = OpenUInput(DeviceType::KEYBOARD, phys_.c_str(), 0, 0);
}

VirtualKeyboard::~VirtualKeyboard() = default;

bool VirtualKeyboard::WriteKeyEvent(int32_t android_key_code, int32_t android_action, nanoseconds event_time) {
  return WriteEvKeyEvent(android_key_code, android_action, KEY_CODE_MAPPING, KEY_ACTION_MAPPING, event_time);
}

const map<int, UinputAction> VirtualKeyboard::KEY_ACTION_MAPPING = {
    {AKEY_EVENT_ACTION_DOWN, UinputAction::PRESS},
    {AKEY_EVENT_ACTION_UP, UinputAction::RELEASE},
};

// The following table was derived from https://android.googlesource.com/platform/frameworks/base/+/master/data/keyboards/Generic.kl
// in combination with https://github.com/torvalds/linux/blob/master/include/uapi/linux/input-event-codes.h
// and https://android.googlesource.com/platform/frameworks/native/+/master/include/android/keycodes.h
// It is similar to https://source.android.com/docs/core/interaction/input/keyboard-devices#hid-keyboard-and-keypad-page-0x07.
const map<int, int> VirtualKeyboard::KEY_CODE_MAPPING = {
    {AKEYCODE_HOME, KEY_HOMEPAGE},
    {AKEYCODE_BACK, KEY_BACK},
    {AKEYCODE_CALL, KEY_PHONE},
    {AKEYCODE_0, KEY_0},
    {AKEYCODE_1, KEY_1},
    {AKEYCODE_2, KEY_2},
    {AKEYCODE_3, KEY_3},
    {AKEYCODE_4, KEY_4},
    {AKEYCODE_5, KEY_5},
    {AKEYCODE_6, KEY_6},
    {AKEYCODE_7, KEY_7},
    {AKEYCODE_8, KEY_8},
    {AKEYCODE_9, KEY_9},
    {AKEYCODE_STAR, KEY_NUMERIC_STAR},
    {AKEYCODE_POUND, KEY_NUMERIC_POUND},
    {AKEYCODE_DPAD_UP, KEY_UP},
    {AKEYCODE_DPAD_DOWN, KEY_DOWN},
    {AKEYCODE_DPAD_LEFT, KEY_LEFT},
    {AKEYCODE_DPAD_RIGHT, KEY_RIGHT},
    {AKEYCODE_DPAD_CENTER, KEY_SELECT},
    {AKEYCODE_VOLUME_UP, KEY_VOLUMEUP},
    {AKEYCODE_VOLUME_DOWN, KEY_VOLUMEDOWN},
    {AKEYCODE_POWER, KEY_POWER},
    {AKEYCODE_CAMERA, KEY_CAMERA},
    {AKEYCODE_A, KEY_A},
    {AKEYCODE_B, KEY_B},
    {AKEYCODE_C, KEY_C},
    {AKEYCODE_D, KEY_D},
    {AKEYCODE_E, KEY_E},
    {AKEYCODE_F, KEY_F},
    {AKEYCODE_G, KEY_G},
    {AKEYCODE_H, KEY_H},
    {AKEYCODE_I, KEY_I},
    {AKEYCODE_J, KEY_J},
    {AKEYCODE_K, KEY_K},
    {AKEYCODE_L, KEY_L},
    {AKEYCODE_M, KEY_M},
    {AKEYCODE_N, KEY_N},
    {AKEYCODE_O, KEY_O},
    {AKEYCODE_P, KEY_P},
    {AKEYCODE_Q, KEY_Q},
    {AKEYCODE_R, KEY_R},
    {AKEYCODE_S, KEY_S},
    {AKEYCODE_T, KEY_T},
    {AKEYCODE_U, KEY_U},
    {AKEYCODE_V, KEY_V},
    {AKEYCODE_W, KEY_W},
    {AKEYCODE_X, KEY_X},
    {AKEYCODE_Y, KEY_Y},
    {AKEYCODE_Z, KEY_Z},
    {AKEYCODE_COMMA, KEY_COMMA},
    {AKEYCODE_PERIOD, KEY_DOT},
    {AKEYCODE_ALT_LEFT, KEY_LEFTALT},
    {AKEYCODE_ALT_RIGHT, KEY_RIGHTALT},
    {AKEYCODE_SHIFT_LEFT, KEY_LEFTSHIFT},
    {AKEYCODE_SHIFT_RIGHT, KEY_RIGHTSHIFT},
    {AKEYCODE_TAB, KEY_TAB},
    {AKEYCODE_SPACE, KEY_SPACE},
    {AKEYCODE_EXPLORER, KEY_WWW},
    {AKEYCODE_ENVELOPE, KEY_MAIL},
    {AKEYCODE_ENTER, KEY_ENTER},
    {AKEYCODE_DEL, KEY_BACKSPACE},
    {AKEYCODE_GRAVE, KEY_GRAVE},
    {AKEYCODE_MINUS, KEY_MINUS},
    {AKEYCODE_EQUALS, KEY_EQUAL},
    {AKEYCODE_LEFT_BRACKET, KEY_LEFTBRACE},
    {AKEYCODE_RIGHT_BRACKET, KEY_RIGHTBRACE},
    {AKEYCODE_BACKSLASH, KEY_BACKSLASH},
    {AKEYCODE_SEMICOLON, KEY_SEMICOLON},
    {AKEYCODE_APOSTROPHE, KEY_APOSTROPHE},
    {AKEYCODE_SLASH, KEY_SLASH},
    {AKEYCODE_HEADSETHOOK, KEY_MEDIA},
    {AKEYCODE_FOCUS, KEY_CAMERA_FOCUS},
    {AKEYCODE_MENU, KEY_COMPOSE},
    {AKEYCODE_NOTIFICATION, KEY_DASHBOARD},
    {AKEYCODE_SEARCH, KEY_SEARCH},
    {AKEYCODE_MEDIA_PLAY_PAUSE, KEY_PLAYPAUSE},
    {AKEYCODE_MEDIA_STOP, KEY_STOP},
    {AKEYCODE_MEDIA_NEXT, KEY_NEXTSONG},
    {AKEYCODE_MEDIA_PREVIOUS, KEY_PREVIOUSSONG},
    {AKEYCODE_MEDIA_REWIND, KEY_REWIND},
    {AKEYCODE_MEDIA_FAST_FORWARD, KEY_FASTFORWARD},
    {AKEYCODE_MUTE, KEY_MICMUTE},
    {AKEYCODE_PAGE_UP, KEY_PAGEUP},
    {AKEYCODE_PAGE_DOWN, KEY_PAGEDOWN},
    {AKEYCODE_ESCAPE, KEY_ESC},
    {AKEYCODE_FORWARD_DEL, KEY_DELETE},
    {AKEYCODE_CTRL_LEFT, KEY_LEFTCTRL},
    {AKEYCODE_CTRL_RIGHT, KEY_RIGHTCTRL},
    {AKEYCODE_CAPS_LOCK, KEY_CAPSLOCK},
    {AKEYCODE_SCROLL_LOCK, KEY_SCROLLLOCK},
    {AKEYCODE_META_LEFT, KEY_LEFTMETA},
    {AKEYCODE_META_RIGHT, KEY_RIGHTMETA},
    {AKEYCODE_FUNCTION, KEY_FN},
    {AKEYCODE_SYSRQ, KEY_SYSRQ},
    {AKEYCODE_BREAK, KEY_PAUSE},
    {AKEYCODE_MOVE_HOME, KEY_HOME},
    {AKEYCODE_MOVE_END, KEY_END},
    {AKEYCODE_INSERT, KEY_INSERT},
    {AKEYCODE_FORWARD, KEY_FORWARD},
    {AKEYCODE_MEDIA_PLAY, KEY_PLAYCD},
    {AKEYCODE_MEDIA_PAUSE, KEY_PAUSECD},
    {AKEYCODE_MEDIA_CLOSE, KEY_CLOSECD},
    {AKEYCODE_MEDIA_EJECT, KEY_EJECTCD},
    {AKEYCODE_MEDIA_RECORD, KEY_RECORD},
    {AKEYCODE_F1, KEY_F1},
    {AKEYCODE_F2, KEY_F2},
    {AKEYCODE_F3, KEY_F3},
    {AKEYCODE_F4, KEY_F4},
    {AKEYCODE_F5, KEY_F5},
    {AKEYCODE_F6, KEY_F6},
    {AKEYCODE_F7, KEY_F7},
    {AKEYCODE_F8, KEY_F8},
    {AKEYCODE_F9, KEY_F9},
    {AKEYCODE_F10, KEY_F10},
    {AKEYCODE_F11, KEY_F11},
    {AKEYCODE_F12, KEY_F12},
    {AKEYCODE_NUM_LOCK, KEY_NUMLOCK},
    {AKEYCODE_NUMPAD_0, KEY_KP0},
    {AKEYCODE_NUMPAD_1, KEY_KP1},
    {AKEYCODE_NUMPAD_2, KEY_KP2},
    {AKEYCODE_NUMPAD_3, KEY_KP3},
    {AKEYCODE_NUMPAD_4, KEY_KP4},
    {AKEYCODE_NUMPAD_5, KEY_KP5},
    {AKEYCODE_NUMPAD_6, KEY_KP6},
    {AKEYCODE_NUMPAD_7, KEY_KP7},
    {AKEYCODE_NUMPAD_8, KEY_KP8},
    {AKEYCODE_NUMPAD_9, KEY_KP9},
    {AKEYCODE_NUMPAD_DIVIDE, KEY_KPSLASH},
    {AKEYCODE_NUMPAD_MULTIPLY, KEY_KPASTERISK},
    {AKEYCODE_NUMPAD_SUBTRACT, KEY_KPMINUS},
    {AKEYCODE_NUMPAD_ADD, KEY_KPPLUS},
    {AKEYCODE_NUMPAD_DOT, KEY_KPDOT},
    {AKEYCODE_NUMPAD_COMMA, KEY_KPJPCOMMA},
    {AKEYCODE_NUMPAD_ENTER, KEY_KPENTER},
    {AKEYCODE_NUMPAD_EQUALS, KEY_KPEQUAL},
    {AKEYCODE_NUMPAD_LEFT_PAREN, KEY_KPLEFTPAREN},
    {AKEYCODE_NUMPAD_RIGHT_PAREN, KEY_KPRIGHTPAREN},
    {AKEYCODE_VOLUME_MUTE, KEY_MUTE},
    {AKEYCODE_CHANNEL_UP, KEY_CHANNELUP},
    {AKEYCODE_CHANNEL_DOWN, KEY_CHANNELDOWN},
    {AKEYCODE_ZOOM_IN, KEY_ZOOMIN},
    {AKEYCODE_ZOOM_OUT, KEY_ZOOMOUT},
    {AKEYCODE_TV, KEY_TV},
    {AKEYCODE_GUIDE, KEY_PROGRAM},
    {AKEYCODE_DVR, KEY_PVR},
    {AKEYCODE_BOOKMARK, KEY_BOOKMARKS},
    {AKEYCODE_CAPTIONS, KEY_SUBTITLE},
    {AKEYCODE_PROG_RED, KEY_RED},
    {AKEYCODE_PROG_GREEN, KEY_GREEN},
    {AKEYCODE_PROG_YELLOW, KEY_YELLOW},
    {AKEYCODE_PROG_BLUE, KEY_BLUE},
    {AKEYCODE_APP_SWITCH, KEY_APPSELECT},
    {AKEYCODE_LANGUAGE_SWITCH, KEY_LANGUAGE},
    {AKEYCODE_CONTACTS, KEY_ADDRESSBOOK},
    {AKEYCODE_CALENDAR, KEY_CALENDAR},
    {AKEYCODE_MUSIC, KEY_CONFIG},
    {AKEYCODE_CALCULATOR, KEY_CALC},
    {AKEYCODE_ZENKAKU_HANKAKU, KEY_ZENKAKUHANKAKU},
    {AKEYCODE_EISU, KEY_HANJA},
    {AKEYCODE_MUHENKAN, KEY_MUHENKAN},
    {AKEYCODE_HENKAN, KEY_HENKAN},
    {AKEYCODE_KATAKANA_HIRAGANA, KEY_KATAKANAHIRAGANA},
    {AKEYCODE_YEN, KEY_YEN},
    {AKEYCODE_RO, KEY_RO},
    {AKEYCODE_KANA, KEY_HANGEUL},
    {AKEYCODE_ASSIST, KEY_ASSISTANT},
    {AKEYCODE_BRIGHTNESS_DOWN, KEY_BRIGHTNESSDOWN},
    {AKEYCODE_BRIGHTNESS_UP, KEY_BRIGHTNESSUP},
    {AKEYCODE_SLEEP, KEY_SLEEP},
    {AKEYCODE_WAKEUP, KEY_WAKEUP},
    {AKEYCODE_LAST_CHANNEL, KEY_LAST},
    {AKEYCODE_VOICE_ASSIST, KEY_VOICECOMMAND},
    {AKEYCODE_CUT, KEY_CUT},
    {AKEYCODE_COPY, KEY_COPY},
    {AKEYCODE_PASTE, KEY_PASTE},
    {AKEYCODE_REFRESH, KEY_REFRESH},
};

// --- VirtualDpad ---

VirtualDpad::VirtualDpad()
    : VirtualInputDevice(GetPhysName(DeviceType::DPAD)) {
  fd_ = OpenUInput(DeviceType::DPAD, phys_.c_str(), 0, 0);
}

VirtualDpad::~VirtualDpad() = default;

bool VirtualDpad::WriteDpadKeyEvent(int32_t android_key_code, int32_t android_action, nanoseconds event_time) {
  return WriteEvKeyEvent(android_key_code, android_action, DPAD_KEY_CODE_MAPPING, VirtualKeyboard::KEY_ACTION_MAPPING, event_time);
}

// Dpad keycode mapping from https://source.android.com/devices/input/keyboard-devices
const map<int, int> VirtualDpad::DPAD_KEY_CODE_MAPPING = {
    {AKEYCODE_DPAD_DOWN, KEY_DOWN},
    {AKEYCODE_DPAD_UP, KEY_UP},
    {AKEYCODE_DPAD_LEFT, KEY_LEFT},
    {AKEYCODE_DPAD_RIGHT, KEY_RIGHT},
    {AKEYCODE_DPAD_CENTER, KEY_SELECT},
    {AKEYCODE_BACK, KEY_BACK},
};

// --- VirtualMouse ---

VirtualMouse::VirtualMouse()
    : VirtualInputDevice(GetPhysName(DeviceType::MOUSE)) {
  fd_ = OpenUInput(DeviceType::MOUSE, phys_.c_str(), 0, 0);
}

VirtualMouse::~VirtualMouse() = default;

bool VirtualMouse::WriteButtonEvent(int32_t android_button_code, int32_t android_action, nanoseconds event_time) {
  return WriteEvKeyEvent(android_button_code, android_action, BUTTON_CODE_MAPPING, BUTTON_ACTION_MAPPING, event_time);
}

bool VirtualMouse::WriteRelativeEvent(int32_t relative_x, int32_t relative_y, nanoseconds event_time) {
  return WriteInputEvent(EV_REL, REL_X, relative_x, event_time) &&
         WriteInputEvent(EV_REL, REL_Y, relative_y, event_time) &&
         WriteInputEvent(EV_SYN, SYN_REPORT, 0, event_time);
}

bool VirtualMouse::WriteScrollEvent(int32_t x_axis_movement, int32_t y_axis_movement, nanoseconds event_time) {
  return WriteInputEvent(EV_REL, REL_HWHEEL, x_axis_movement, event_time) &&
         WriteInputEvent(EV_REL, REL_WHEEL, y_axis_movement, event_time) &&
         WriteInputEvent(EV_SYN, SYN_REPORT, 0, event_time);
}

const map<int, UinputAction> VirtualMouse::BUTTON_ACTION_MAPPING = {
    {AMOTION_EVENT_ACTION_BUTTON_PRESS, UinputAction::PRESS},
    {AMOTION_EVENT_ACTION_BUTTON_RELEASE, UinputAction::RELEASE},
};

// Button code mapping from https://source.android.com/devices/input/touch-devices
const map<int, int> VirtualMouse::BUTTON_CODE_MAPPING = {
    {AMOTION_EVENT_BUTTON_PRIMARY, BTN_LEFT},
    {AMOTION_EVENT_BUTTON_SECONDARY, BTN_RIGHT},
    {AMOTION_EVENT_BUTTON_TERTIARY, BTN_MIDDLE},
    {AMOTION_EVENT_BUTTON_BACK, BTN_BACK},
    {AMOTION_EVENT_BUTTON_FORWARD, BTN_FORWARD},
};

// --- VirtualTouchscreen ---

VirtualTouchscreen::VirtualTouchscreen(int32_t screen_width, int32_t screen_height)
    : VirtualInputDevice(GetPhysName(DeviceType::TOUCHSCREEN)),
      screen_width_(screen_width),
      screen_height_(screen_height) {
  fd_ = OpenUInput(DeviceType::TOUCHSCREEN, phys_.c_str(), screen_width, screen_height);
  Log::D("VirtualTouchscreen::VirtualTouchscreen(%d, %d)", screen_width, screen_height);
}

VirtualTouchscreen::~VirtualTouchscreen() = default;

bool VirtualTouchscreen::IsValidPointerId(int32_t pointer_id, UinputAction uinput_action) {
  if (pointer_id < -1 || pointer_id >= static_cast<int>(MAX_POINTERS)) {
    Log::E("Virtual touch event has invalid pointer id %d; value must be between -1 and %zu", pointer_id, MAX_POINTERS - 0);
    return false;
  }

  if (uinput_action == UinputAction::PRESS && active_pointers_.test(pointer_id)) {
    Log::E("Repetitive action DOWN event received on a pointer %d that is already down.", pointer_id);
    return false;
  }
  if (uinput_action == UinputAction::RELEASE && !active_pointers_.test(pointer_id)) {
    Log::E("Pointer %d action UP received with no prior action DOWN on touchscreen %d.", pointer_id, fd_);
    return false;
  }
  return true;
}

bool VirtualTouchscreen::WriteTouchEvent(int32_t pointer_id, int32_t tool_type, int32_t action,
                                         int32_t location_x, int32_t location_y, int32_t pressure,
                                         int32_t major_axis_size, nanoseconds event_time) {
  Log::D("VirtualTouchscreen::WriteTouchEvent(%d, %d, %d, %d, %d, %d, %d, %lld)",
         pointer_id, tool_type, action, location_x, location_y, pressure, major_axis_size, event_time.count());
  auto actionIterator = TOUCH_ACTION_MAPPING.find(action);
  if (actionIterator == TOUCH_ACTION_MAPPING.end()) {
    return false;
  }
  UinputAction uinput_action = actionIterator->second;
  if (!IsValidPointerId(pointer_id, uinput_action)) {
    return false;
  }
  if (!WriteInputEvent(EV_ABS, ABS_MT_SLOT, pointer_id, event_time)) {
    return false;
  }
  auto tool_type_iterator = TOOL_TYPE_MAPPING.find(tool_type);
  if (tool_type_iterator == TOOL_TYPE_MAPPING.end()) {
    return false;
  }
  if (!WriteInputEvent(EV_ABS, ABS_MT_TOOL_TYPE, static_cast<int32_t>(tool_type_iterator->second), event_time)) {
    return false;
  }
  if (uinput_action == UinputAction::PRESS && !HandleTouchDown(pointer_id, event_time)) {
    return false;
  }
  if (uinput_action == UinputAction::RELEASE && !HandleTouchUp(pointer_id, event_time)) {
    return false;
  }
  if (!WriteInputEvent(EV_ABS, ABS_MT_POSITION_X, location_x, event_time)) {
    return false;
  }
  if (!WriteInputEvent(EV_ABS, ABS_MT_POSITION_Y, location_y, event_time)) {
    return false;
  }
  if (!isnan(pressure)) {
    if (!WriteInputEvent(EV_ABS, ABS_MT_PRESSURE, pressure, event_time)) {
      return false;
    }
  }
  if (!isnan(major_axis_size)) {
    if (!WriteInputEvent(EV_ABS, ABS_MT_TOUCH_MAJOR, major_axis_size, event_time)) {
      return false;
    }
  }
  return WriteInputEvent(EV_SYN, SYN_REPORT, 0, event_time);
}

bool VirtualTouchscreen::HandleTouchUp(int32_t pointer_id, nanoseconds event_time) {
  if (!WriteInputEvent(EV_ABS, ABS_MT_TRACKING_ID, static_cast<int32_t>(-1), event_time)) {
    return false;
  }
  // When a pointer is no longer in touch, remove the pointer id from the corresponding
  // entry in the unreleased touches map.
  active_pointers_.reset(pointer_id);
  Log::D("Pointer %d erased from the touchscreen %d", pointer_id, fd_);

  // Only sends the BTN UP event when there's no pointers on the touchscreen.
  if (active_pointers_.none()) {
    if (!WriteInputEvent(EV_KEY, BTN_TOUCH, static_cast<int32_t>(UinputAction::RELEASE), event_time)) {
      return false;
    }
    Log::D("No pointers on touchscreen %d, BTN UP event sent.", fd_);
  }
  return true;
}

bool VirtualTouchscreen::HandleTouchDown(int32_t pointer_id, nanoseconds event_time) {
  // When a new pointer is down on the touchscreen, add the pointer id in the corresponding
  // entry in the unreleased touches map.
  if (active_pointers_.none()) {
    // Only sends the BTN Down event when the first pointer on the touchscreen is down.
    if (!WriteInputEvent(EV_KEY, BTN_TOUCH, static_cast<int32_t>(UinputAction::PRESS), event_time)) {
      return false;
    }
    Log::D("First pointer %d down under touchscreen %d, BTN DOWN event sent", pointer_id, fd_);
  }

  active_pointers_.set(pointer_id);
  Log::D("Added pointer %d under touchscreen %d in the map", pointer_id, fd_);
  if (!WriteInputEvent(EV_ABS, ABS_MT_TRACKING_ID, static_cast<int32_t>(pointer_id), event_time)) {
    return false;
  }
  return true;
}

// Tool type mapping from https://source.android.com/devices/input/touch-devices
const map<int, int> VirtualTouchscreen::TOOL_TYPE_MAPPING = {
    {AMOTION_EVENT_TOOL_TYPE_FINGER, MT_TOOL_FINGER},
};

// --- VirtualStylus ---

VirtualStylus::VirtualStylus(int32_t screen_width, int32_t screen_height)
    : VirtualInputDevice(GetPhysName(DeviceType::STYLUS)),
      screen_width_(screen_width),
      screen_height_(screen_height) {
  fd_ = OpenUInput(DeviceType::STYLUS, phys_.c_str(), screen_width, screen_height);
}

VirtualStylus::~VirtualStylus() = default;

bool VirtualStylus::WriteMotionEvent(int32_t tool_type, int32_t action, int32_t location_x,
                                     int32_t location_y, int32_t pressure, int32_t tilt_x,
                                     int32_t tilt_y, nanoseconds event_time) {
  auto actionIterator = TOUCH_ACTION_MAPPING.find(action);
  if (actionIterator == TOUCH_ACTION_MAPPING.end()) {
    Log::E("Unsupported action passed for stylus: %d.", action);
    return false;
  }
  UinputAction uinput_action = actionIterator->second;
  auto tool_type_iterator = TOOL_TYPE_MAPPING.find(tool_type);
  if (tool_type_iterator == TOOL_TYPE_MAPPING.end()) {
    Log::E("Unsupported tool type passed for stylus: %d.", tool_type);
    return false;
  }
  auto tool = static_cast<uint16_t>(tool_type_iterator->second);
  if (uinput_action == UinputAction::PRESS && !HandleStylusDown(tool, event_time)) {
    return false;
  }
  if (!is_stylus_down_) {
    Log::E("Action UP or MOVE received with no prior action DOWN for stylus %d.", fd_);
    return false;
  }
  if (uinput_action == UinputAction::RELEASE && !HandleStylusUp(tool, event_time)) {
    return false;
  }
  if (!WriteInputEvent(EV_ABS, ABS_X, location_x, event_time)) {
    Log::E("Unsupported x-axis location passed for stylus: %d.", location_x);
    return false;
  }
  if (!WriteInputEvent(EV_ABS, ABS_Y, location_y, event_time)) {
    Log::E("Unsupported y-axis location passed for stylus: %d.", location_y);
    return false;
  }
  if (!WriteInputEvent(EV_ABS, ABS_TILT_X, tilt_x, event_time)) {
    Log::E("Unsupported x-axis tilt passed for stylus: %d.", tilt_x);
    return false;
  }
  if (!WriteInputEvent(EV_ABS, ABS_TILT_Y, tilt_y, event_time)) {
    Log::E("Unsupported y-axis tilt passed for stylus: %d.", tilt_y);
    return false;
  }
  if (!WriteInputEvent(EV_ABS, ABS_PRESSURE, pressure, event_time)) {
    Log::E("Unsupported pressure passed for stylus: %d.", pressure);
    return false;
  }
  if (!WriteInputEvent(EV_SYN, SYN_REPORT, 0, event_time)) {
    Log::E("Failed to write SYN_REPORT for stylus motion event.");
    return false;
  }
  return true;
}

bool VirtualStylus::WriteButtonEvent(int32_t android_button_code, int32_t android_action, nanoseconds event_time) {
  return WriteEvKeyEvent(android_button_code, android_action, BUTTON_CODE_MAPPING, VirtualMouse::BUTTON_ACTION_MAPPING, event_time);
}

bool VirtualStylus::HandleStylusDown(uint16_t tool, nanoseconds event_time) {
  if (is_stylus_down_) {
    Log::E("Repetitive action DOWN event received for a stylus that is already down.");
    return false;
  }
  if (!WriteInputEvent(EV_KEY, tool, static_cast<int32_t>(UinputAction::PRESS), event_time)) {
    Log::E("Failed to write EV_KEY for stylus tool type: %u.", tool);
    return false;
  }
  if (!WriteInputEvent(EV_KEY, BTN_TOUCH, static_cast<int32_t>(UinputAction::PRESS), event_time)) {
    Log::E("Failed to write BTN_TOUCH for stylus press.");
    return false;
  }
  is_stylus_down_ = true;
  return true;
}

bool VirtualStylus::HandleStylusUp(uint16_t tool, nanoseconds event_time) {
  if (!WriteInputEvent(EV_KEY, tool, static_cast<int32_t>(UinputAction::RELEASE), event_time)) {
    Log::E("Failed to write EV_KEY for stylus tool type: %u.", tool);
    return false;
  }
  if (!WriteInputEvent(EV_KEY, BTN_TOUCH, static_cast<int32_t>(UinputAction::RELEASE), event_time)) {
    Log::E("Failed to write BTN_TOUCH for stylus release.");
    return false;
  }
  is_stylus_down_ = false;
  return true;
}

const map<int, int> VirtualStylus::TOOL_TYPE_MAPPING = {
    {AMOTION_EVENT_TOOL_TYPE_STYLUS, BTN_TOOL_PEN},
    {AMOTION_EVENT_TOOL_TYPE_ERASER, BTN_TOOL_RUBBER},
};

// Button code mapping from https://source.android.com/devices/input/touch-devices
const map<int, int> VirtualStylus::BUTTON_CODE_MAPPING = {
    {AMOTION_EVENT_BUTTON_STYLUS_PRIMARY, BTN_STYLUS},
    {AMOTION_EVENT_BUTTON_STYLUS_SECONDARY, BTN_STYLUS2},
};

}  // namespace screensharing