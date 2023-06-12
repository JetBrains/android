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
@file:Suppress("unused")
package com.android.tools.idea.streaming.device

// Key codes.
// Based on [keycodes.h](https://android.googlesource.com/platform/frameworks/native/+/master/include/android/keycodes.h)

/** Unknown key code. */
const val AKEYCODE_UNKNOWN = 0
/**
 * Soft Left key.
 * Usually situated below the display on phones and used as a multi-function feature key
 * for selecting a software defined function shown on the bottom left of the display.
 */
const val AKEYCODE_SOFT_LEFT = 1
/**
 * Soft Right key.
 * Usually situated below the display on phones and used as a multi-function feature key
 * for selecting a software defined function shown on the bottom right of the display.
 */
const val AKEYCODE_SOFT_RIGHT = 2
/** Home key. This key is handled by the framework and is never delivered to applications. */
const val AKEYCODE_HOME = 3
/** Back key. */
const val AKEYCODE_BACK = 4
/** Call key. */
const val AKEYCODE_CALL = 5
/** End Call key. */
const val AKEYCODE_ENDCALL = 6
/** '0' key. */
const val AKEYCODE_0 = 7
/** '1' key. */
const val AKEYCODE_1 = 8
/** '2' key. */
const val AKEYCODE_2 = 9
/** '3' key. */
const val AKEYCODE_3 = 10
/** '4' key. */
const val AKEYCODE_4 = 11
/** '5' key. */
const val AKEYCODE_5 = 12
/** '6' key. */
const val AKEYCODE_6 = 13
/** '7' key. */
const val AKEYCODE_7 = 14
/** '8' key. */
const val AKEYCODE_8 = 15
/** '9' key. */
const val AKEYCODE_9 = 16
/** '*' key. */
const val AKEYCODE_STAR = 17
/** '#' key. */
const val AKEYCODE_POUND = 18
/** Directional Pad Up key. May also be synthesized from trackball motions. */
const val AKEYCODE_DPAD_UP = 19
/** Directional Pad Down key. May also be synthesized from trackball motions. */
const val AKEYCODE_DPAD_DOWN = 20
/** Directional Pad Left key. May also be synthesized from trackball motions. */
const val AKEYCODE_DPAD_LEFT = 21
/** Directional Pad Right key. May also be synthesized from trackball motions. */
const val AKEYCODE_DPAD_RIGHT = 22
/** Directional Pad Center key. May also be synthesized from trackball motions. */
const val AKEYCODE_DPAD_CENTER = 23
/** Volume Up key. Adjusts the speaker volume up. */
const val AKEYCODE_VOLUME_UP = 24
/** Volume Down key. Adjusts the speaker volume down. */
const val AKEYCODE_VOLUME_DOWN = 25
/** Power key. */
const val AKEYCODE_POWER = 26
/** Camera key. Used to launch a camera application or take pictures. */
const val AKEYCODE_CAMERA = 27
/** Clear key. */
const val AKEYCODE_CLEAR = 28
/** 'A' key. */
const val AKEYCODE_A = 29
/** 'B' key. */
const val AKEYCODE_B = 30
/** 'C' key. */
const val AKEYCODE_C = 31
/** 'D' key. */
const val AKEYCODE_D = 32
/** 'E' key. */
const val AKEYCODE_E = 33
/** 'F' key. */
const val AKEYCODE_F = 34
/** 'G' key. */
const val AKEYCODE_G = 35
/** 'H' key. */
const val AKEYCODE_H = 36
/** 'I' key. */
const val AKEYCODE_I = 37
/** 'J' key. */
const val AKEYCODE_J = 38
/** 'K' key. */
const val AKEYCODE_K = 39
/** 'L' key. */
const val AKEYCODE_L = 40
/** 'M' key. */
const val AKEYCODE_M = 41
/** 'N' key. */
const val AKEYCODE_N = 42
/** 'O' key. */
const val AKEYCODE_O = 43
/** 'P' key. */
const val AKEYCODE_P = 44
/** 'Q' key. */
const val AKEYCODE_Q = 45
/** 'R' key. */
const val AKEYCODE_R = 46
/** 'S' key. */
const val AKEYCODE_S = 47
/** 'T' key. */
const val AKEYCODE_T = 48
/** 'U' key. */
const val AKEYCODE_U = 49
/** 'V' key. */
const val AKEYCODE_V = 50
/** 'W' key. */
const val AKEYCODE_W = 51
/** 'X' key. */
const val AKEYCODE_X = 52
/** 'Y' key. */
const val AKEYCODE_Y = 53
/** 'Z' key. */
const val AKEYCODE_Z = 54
/** ',' key. */
const val AKEYCODE_COMMA = 55
/** '.' key. */
const val AKEYCODE_PERIOD = 56
/** Left Alt modifier key. */
const val AKEYCODE_ALT_LEFT = 57
/** Right Alt modifier key. */
const val AKEYCODE_ALT_RIGHT = 58
/** Left Shift modifier key. */
const val AKEYCODE_SHIFT_LEFT = 59
/** Right Shift modifier key. */
const val AKEYCODE_SHIFT_RIGHT = 60
/** Tab key. */
const val AKEYCODE_TAB = 61
/** Space key. */
const val AKEYCODE_SPACE = 62
/** Symbol modifier key. Used to enter alternate symbols. */
const val AKEYCODE_SYM = 63
/** Explorer special function key. Used to launch a browser application. */
const val AKEYCODE_EXPLORER = 64
/** Envelope special function key. Used to launch a mail application. */
const val AKEYCODE_ENVELOPE = 65
/** Enter key. */
const val AKEYCODE_ENTER = 66
/** Backspace key. Deletes characters before the insertion point, unlike [AKEYCODE_FORWARD_DEL]. */
const val AKEYCODE_DEL = 67
/** '`' (backtick) key. */
const val AKEYCODE_GRAVE = 68
/** '-'. */
const val AKEYCODE_MINUS = 69
/** '=' key. */
const val AKEYCODE_EQUALS = 70
/** '[' key. */
const val AKEYCODE_LEFT_BRACKET = 71
/** ']' key. */
const val AKEYCODE_RIGHT_BRACKET = 72
/** '\' key. */
const val AKEYCODE_BACKSLASH = 73
/** ';' key. */
const val AKEYCODE_SEMICOLON = 74
/** ''' (apostrophe) key. */
const val AKEYCODE_APOSTROPHE = 75
/** '/' key. */
const val AKEYCODE_SLASH = 76
/** '@' key. */
const val AKEYCODE_AT = 77
/**
 * Number modifier key. Used to enter numeric symbols.
 * This key is not [AKEYCODE_NUM_LOCK]; it is more like [AKEYCODE_ALT_LEFT].
 */
const val AKEYCODE_NUM = 78
/** Headset Hook key. Used to hang up calls and stop media. */
const val AKEYCODE_HEADSETHOOK = 79
/** Camera Focus key. Used to focus the camera. */
const val AKEYCODE_FOCUS = 80
/** '+' key. */
const val AKEYCODE_PLUS = 81
/** Menu key. */
const val AKEYCODE_MENU = 82
/** Notification key. */
const val AKEYCODE_NOTIFICATION = 83
/** Search key. */
const val AKEYCODE_SEARCH = 84
/** Play/Pause media key. */
const val AKEYCODE_MEDIA_PLAY_PAUSE = 85
/** Stop media key. */
const val AKEYCODE_MEDIA_STOP = 86
/** Play Next media key. */
const val AKEYCODE_MEDIA_NEXT = 87
/** Play Previous media key. */
const val AKEYCODE_MEDIA_PREVIOUS = 88
/** Rewind media key. */
const val AKEYCODE_MEDIA_REWIND = 89
/** Fast Forward media key. */
const val AKEYCODE_MEDIA_FAST_FORWARD = 90
/** Mute key. Mutes the microphone, unlike [AKEYCODE_VOLUME_MUTE]. */
const val AKEYCODE_MUTE = 91
/** Page Up key. */
const val AKEYCODE_PAGE_UP = 92
/** Page Down key. */
const val AKEYCODE_PAGE_DOWN = 93
/** Picture Symbols modifier key. Used to switch symbol sets (Emoji, Kao-moji). */
const val AKEYCODE_PICTSYMBOLS = 94
/** Switch Charset modifier key. Used to switch character sets (Kanji, Katakana). */
const val AKEYCODE_SWITCH_CHARSET = 95
/**
 * 'A' Button key. On a game controller, the A button should be either the button labeled A
 * or the first button on the bottom row of controller buttons.
 */
const val AKEYCODE_BUTTON_A = 96
/**
 * 'B' Button key. On a game controller, the B button should be either the button labeled B
 * or the second button on the bottom row of controller buttons.
 */
const val AKEYCODE_BUTTON_B = 97
/**
 * 'C' Button key. On a game controller, the C button should be either the button labeled C
 * or the third button on the bottom row of controller buttons.
 */
const val AKEYCODE_BUTTON_C = 98
/**
 * 'X' Button key. On a game controller, the X button should be either the button labeled X
 * or the first button on the upper row of controller buttons.
 */
const val AKEYCODE_BUTTON_X = 99
/**
 * 'Y' Button key. On a game controller, the Y button should be either the button labeled Y
 * or the second button on the upper row of controller buttons.
 */
const val AKEYCODE_BUTTON_Y = 100
/**
 * 'Z' Button key. On a game controller, the Z button should be either the button labeled Z
 * or the third button on the upper row of controller buttons.
 */
const val AKEYCODE_BUTTON_Z = 101
/** L1 Button key. On a game controller, the L1 button should be either the button labeled L1 (or L) or the top left trigger button. */
const val AKEYCODE_BUTTON_L1 = 102
/** R1 Button key. On a game controller, the R1 button should be either the button labeled R1 (or R) or the top right trigger button. */
const val AKEYCODE_BUTTON_R1 = 103
/** L2 Button key. On a game controller, the L2 button should be either the button labeled L2 or the bottom left trigger button. */
const val AKEYCODE_BUTTON_L2 = 104
/** R2 Button key. On a game controller, the R2 button should be either the button labeled R2 or the bottom right trigger button. */
const val AKEYCODE_BUTTON_R2 = 105
/** Left Thumb Button key. On a game controller, the left thumb button indicates that the left (or only) joystick is pressed. */
const val AKEYCODE_BUTTON_THUMBL = 106
/** Right Thumb Button key. On a game controller, the right thumb button indicates that the right joystick is pressed. */
const val AKEYCODE_BUTTON_THUMBR = 107
/** Start Button key. On a game controller, the button labeled Start. */
const val AKEYCODE_BUTTON_START = 108
/** Select Button key. On a game controller, the button labeled Select. */
const val AKEYCODE_BUTTON_SELECT = 109
/** Mode Button key. On a game controller, the button labeled Mode. */
const val AKEYCODE_BUTTON_MODE = 110
/** Escape key. */
const val AKEYCODE_ESCAPE = 111
/** Forward Delete key. Deletes characters ahead of the insertion point, unlike [AKEYCODE_DEL]. */
const val AKEYCODE_FORWARD_DEL = 112
/** Left Control modifier key. */
const val AKEYCODE_CTRL_LEFT = 113
/** Right Control modifier key. */
const val AKEYCODE_CTRL_RIGHT = 114
/** Caps Lock key. */
const val AKEYCODE_CAPS_LOCK = 115
/** Scroll Lock key. */
const val AKEYCODE_SCROLL_LOCK = 116
/** Left Meta modifier key. */
const val AKEYCODE_META_LEFT = 117
/** Right Meta modifier key. */
const val AKEYCODE_META_RIGHT = 118
/** Function modifier key. */
const val AKEYCODE_FUNCTION = 119
/** System Request / Print Screen key. */
const val AKEYCODE_SYSRQ = 120
/** Break / Pause key. */
const val AKEYCODE_BREAK = 121
/** Home Movement key. Used for scrolling or moving the cursor around to the start of a line or to the top of a list. */
const val AKEYCODE_MOVE_HOME = 122
/** End Movement key. Used for scrolling or moving the cursor around to the end of a line or to the bottom of a list. */
const val AKEYCODE_MOVE_END = 123
/** Insert key. Toggles insert / overwrite edit mode. */
const val AKEYCODE_INSERT = 124
/** Forward key. Navigates forward in the history stack.  Complement of [AKEYCODE_BACK]. */
const val AKEYCODE_FORWARD = 125
/** Play media key. */
const val AKEYCODE_MEDIA_PLAY = 126
/** Pause media key. */
const val AKEYCODE_MEDIA_PAUSE = 127
/** Close media key. May be used to close a CD tray, for example. */
const val AKEYCODE_MEDIA_CLOSE = 128
/** Eject media key. May be used to eject a CD tray, for example. */
const val AKEYCODE_MEDIA_EJECT = 129
/** Record media key. */
const val AKEYCODE_MEDIA_RECORD = 130
/** F1 key. */
const val AKEYCODE_F1 = 131
/** F2 key. */
const val AKEYCODE_F2 = 132
/** F3 key. */
const val AKEYCODE_F3 = 133
/** F4 key. */
const val AKEYCODE_F4 = 134
/** F5 key. */
const val AKEYCODE_F5 = 135
/** F6 key. */
const val AKEYCODE_F6 = 136
/** F7 key. */
const val AKEYCODE_F7 = 137
/** F8 key. */
const val AKEYCODE_F8 = 138
/** F9 key. */
const val AKEYCODE_F9 = 139
/** F10 key. */
const val AKEYCODE_F10 = 140
/** F11 key. */
const val AKEYCODE_F11 = 141
/** F12 key. */
const val AKEYCODE_F12 = 142
/**
 * Num Lock key. This is the Num Lock key; it is different from [AKEYCODE_NUM].
 * This key alters the behavior of other keys on the numeric keypad.
 */
const val AKEYCODE_NUM_LOCK = 143
/** Numeric keypad '0' key. */
const val AKEYCODE_NUMPAD_0 = 144
/** Numeric keypad '1' key. */
const val AKEYCODE_NUMPAD_1 = 145
/** Numeric keypad '2' key. */
const val AKEYCODE_NUMPAD_2 = 146
/** Numeric keypad '3' key. */
const val AKEYCODE_NUMPAD_3 = 147
/** Numeric keypad '4' key. */
const val AKEYCODE_NUMPAD_4 = 148
/** Numeric keypad '5' key. */
const val AKEYCODE_NUMPAD_5 = 149
/** Numeric keypad '6' key. */
const val AKEYCODE_NUMPAD_6 = 150
/** Numeric keypad '7' key. */
const val AKEYCODE_NUMPAD_7 = 151
/** Numeric keypad '8' key. */
const val AKEYCODE_NUMPAD_8 = 152
/** Numeric keypad '9' key. */
const val AKEYCODE_NUMPAD_9 = 153
/** Numeric keypad '/' key (for division). */
const val AKEYCODE_NUMPAD_DIVIDE = 154
/** Numeric keypad '*' key (for multiplication). */
const val AKEYCODE_NUMPAD_MULTIPLY = 155
/** Numeric keypad '-' key (for subtraction). */
const val AKEYCODE_NUMPAD_SUBTRACT = 156
/** Numeric keypad '+' key (for addition). */
const val AKEYCODE_NUMPAD_ADD = 157
/** Numeric keypad '.' key (for decimals or digit grouping). */
const val AKEYCODE_NUMPAD_DOT = 158
/** Numeric keypad ',' key (for decimals or digit grouping). */
const val AKEYCODE_NUMPAD_COMMA = 159
/** Numeric keypad Enter key. */
const val AKEYCODE_NUMPAD_ENTER = 160
/** Numeric keypad '=' key. */
const val AKEYCODE_NUMPAD_EQUALS = 161
/** Numeric keypad '(' key. */
const val AKEYCODE_NUMPAD_LEFT_PAREN = 62
/** Numeric keypad ')' key. */
const val AKEYCODE_NUMPAD_RIGHT_PAREN = 163
/**
 * Volume Mute key. Mutes the speaker, unlike [AKEYCODE_MUTE].
 * This key should normally be implemented as a toggle such that the first press
 * mutes the speaker and the second press restores the original volume.
 */
const val AKEYCODE_VOLUME_MUTE = 164
/** Info key. Common on TV remotes to show additional information related to what is currently being viewed. */
const val AKEYCODE_INFO = 165
/** Channel up key. On TV remotes, increments the television channel. */
const val AKEYCODE_CHANNEL_UP = 166
/** Channel down key. On TV remotes, decrements the television channel. */
const val AKEYCODE_CHANNEL_DOWN = 167
/** Zoom in key. */
const val AKEYCODE_ZOOM_IN = 168
/** Zoom out key. */
const val AKEYCODE_ZOOM_OUT = 169
/** TV key. On TV remotes, switches to viewing live TV. */
const val AKEYCODE_TV = 170
/** Window key. On TV remotes, toggles picture-in-picture mode or other windowing functions. */
const val AKEYCODE_WINDOW = 171
/** Guide key. On TV remotes, shows a programming guide. */
const val AKEYCODE_GUIDE = 172
/** DVR key. On some TV remotes, switches to a DVR mode for recorded shows. */
const val AKEYCODE_DVR = 173
/** Bookmark key. On some TV remotes, bookmarks content or web pages. */
const val AKEYCODE_BOOKMARK = 174
/** Toggle captions key. Switches the mode for closed-captioning text, for example during television shows. */
const val AKEYCODE_CAPTIONS = 175
/** Settings key. Starts the system settings activity. */
const val AKEYCODE_SETTINGS = 176
/** TV power key. On TV remotes, toggles the power on a television screen. */
const val AKEYCODE_TV_POWER = 177
/** TV input key. On TV remotes, switches the input on a television screen. */
const val AKEYCODE_TV_INPUT = 178
/** Set-top-box power key. On TV remotes, toggles the power on an external Set-top-box. */
const val AKEYCODE_STB_POWER = 179
/** Set-top-box input key. On TV remotes, switches the input mode on an external Set-top-box. */
const val AKEYCODE_STB_INPUT = 180
/** A/V Receiver power key. On TV remotes, toggles the power on an external A/V Receiver. */
const val AKEYCODE_AVR_POWER = 181
/** A/V Receiver input key. On TV remotes, switches the input mode on an external A/V Receiver. */
const val AKEYCODE_AVR_INPUT = 182
/** Red "programmable" key. On TV remotes, acts as a contextual/programmable key. */
const val AKEYCODE_PROG_RED = 183
/** Green "programmable" key. On TV remotes, acts as a contextual/programmable key. */
const val AKEYCODE_PROG_GREEN = 184
/** Yellow "programmable" key. On TV remotes, acts as a contextual/programmable key. */
const val AKEYCODE_PROG_YELLOW = 185
/** Blue "programmable" key. On TV remotes, acts as a contextual/programmable key. */
const val AKEYCODE_PROG_BLUE = 186
/** App switch key. Should bring up the application switcher dialog. */
const val AKEYCODE_APP_SWITCH = 187
/** Generic Game Pad Button #1.*/
const val AKEYCODE_BUTTON_1 = 188
/** Generic Game Pad Button #2.*/
const val AKEYCODE_BUTTON_2 = 189
/** Generic Game Pad Button #3.*/
const val AKEYCODE_BUTTON_3 = 190
/** Generic Game Pad Button #4.*/
const val AKEYCODE_BUTTON_4 = 191
/** Generic Game Pad Button #5.*/
const val AKEYCODE_BUTTON_5 = 192
/** Generic Game Pad Button #6.*/
const val AKEYCODE_BUTTON_6 = 193
/** Generic Game Pad Button #7.*/
const val AKEYCODE_BUTTON_7 = 194
/** Generic Game Pad Button #8.*/
const val AKEYCODE_BUTTON_8 = 195
/** Generic Game Pad Button #9.*/
const val AKEYCODE_BUTTON_9 = 196
/** Generic Game Pad Button #10.*/
const val AKEYCODE_BUTTON_10 = 197
/** Generic Game Pad Button #11.*/
const val AKEYCODE_BUTTON_11 = 198
/** Generic Game Pad Button #12.*/
const val AKEYCODE_BUTTON_12 = 199
/** Generic Game Pad Button #13.*/
const val AKEYCODE_BUTTON_13 = 200
/** Generic Game Pad Button #14.*/
const val AKEYCODE_BUTTON_14 = 201
/** Generic Game Pad Button #15.*/
const val AKEYCODE_BUTTON_15 = 202
/** Generic Game Pad Button #16.*/
const val AKEYCODE_BUTTON_16 = 203
/**
 * Language Switch key.
 * Toggles the current input language such as switching between English and Japanese on
 * a QWERTY keyboard.  On some devices, the same function may be performed by
 * pressing Shift+Spacebar.
 */
const val AKEYCODE_LANGUAGE_SWITCH = 204
/**
 * Manner Mode key.
 * Toggles silent or vibrate mode on and off to make the device behave more politely
 * in certain settings such as on a crowded train. On some devices, the key may only
 * operate when long-pressed.
 */
const val AKEYCODE_MANNER_MODE = 205
/** 3D Mode key. Toggles the display between 2D and 3D mode. */
const val AKEYCODE_3D_MODE = 206
/** Contacts special function key. Used to launch an address book application. */
const val AKEYCODE_CONTACTS = 207
/** Calendar special function key. Used to launch a calendar application. */
const val AKEYCODE_CALENDAR = 208
/** Music special function key. Used to launch a music player application. */
const val AKEYCODE_MUSIC = 209
/** Calculator special function key. Used to launch a calculator application. */
const val AKEYCODE_CALCULATOR = 210
/** Japanese full-width / half-width key. */
const val AKEYCODE_ZENKAKU_HANKAKU = 211
/** Japanese alphanumeric key. */
const val AKEYCODE_EISU = 212
/** Japanese non-conversion key. */
const val AKEYCODE_MUHENKAN = 213
/** Japanese conversion key. */
const val AKEYCODE_HENKAN = 214
/** Japanese katakana / hiragana key. */
const val AKEYCODE_KATAKANA_HIRAGANA = 215
/** Japanese Yen key. */
const val AKEYCODE_YEN = 216
/** Japanese Ro key. */
const val AKEYCODE_RO = 217
/** Japanese kana key. */
const val AKEYCODE_KANA = 218
/** Assist key. Launches the global assist activity. Not delivered to applications. */
const val AKEYCODE_ASSIST = 219
/** Brightness Down key. Adjusts the screen brightness down. */
const val AKEYCODE_BRIGHTNESS_DOWN = 220
/** Brightness Up key. Adjusts the screen brightness up. */
const val AKEYCODE_BRIGHTNESS_UP = 221
/** Audio Track key. Switches the audio tracks. */
const val AKEYCODE_MEDIA_AUDIO_TRACK = 222
/**
 * Sleep key. Puts the device to sleep. Behaves somewhat like [AKEYCODE_POWER] but
 * has no effect if the device is already asleep.
 */
const val AKEYCODE_SLEEP = 223
/**
 * Wakeup key. Wakes up the device. Behaves somewhat like [AKEYCODE_POWER] but
 * has no effect if the device is already awake.
 */
const val AKEYCODE_WAKEUP = 224
/**
 * Pairing key. Initiates peripheral pairing mode. Useful for pairing remote control
 * devices or game controllers, especially if no other input mode is available.
 */
const val AKEYCODE_PAIRING = 225
/** Media Top Menu key. Goes to the top of media menu. */
const val AKEYCODE_MEDIA_TOP_MENU = 226
/** '11' key. */
const val AKEYCODE_11 = 227
/** '12' key. */
const val AKEYCODE_12 = 228
/** Last Channel key. Goes to the last viewed channel. */
const val AKEYCODE_LAST_CHANNEL = 229
/** TV data service key. Displays data services like weather, sports. */
const val AKEYCODE_TV_DATA_SERVICE = 230
/** Voice Assist key. Launches the global voice assist activity. Not delivered to applications. */
const val AKEYCODE_VOICE_ASSIST = 231
/** Radio key. Toggles TV service / Radio service. */
const val AKEYCODE_TV_RADIO_SERVICE = 232
/** Teletext key. Displays Teletext service. */
const val AKEYCODE_TV_TELETEXT = 233
/**
 * Number entry key.
 * Initiates to enter multi-digit channel number when each digit key is assigned
 * for selecting separate channel. Corresponds to Number Entry Mode(0x1D) of CEC
 * User Control Code.
 */
const val AKEYCODE_TV_NUMBER_ENTRY = 234
/** Analog Terrestrial key. Switches to analog terrestrial broadcast service. */
const val AKEYCODE_TV_TERRESTRIAL_ANALOG = 235
/** Digital Terrestrial key. Switches to digital terrestrial broadcast service. */
const val AKEYCODE_TV_TERRESTRIAL_DIGITAL = 236
/** Satellite key. Switches to digital satellite broadcast service. */
const val AKEYCODE_TV_SATELLITE = 237
/** BS key. Switches to BS digital satellite broadcasting service available in Japan. */
const val AKEYCODE_TV_SATELLITE_BS = 238
/** CS key. Switches to CS digital satellite broadcasting service available in Japan. */
const val AKEYCODE_TV_SATELLITE_CS = 239
/** BS/CS key. Toggles between BS and CS digital satellite services. */
const val AKEYCODE_TV_SATELLITE_SERVICE = 240
/** Toggle Network key. Toggles selecting broadcast services. */
const val AKEYCODE_TV_NETWORK = 241
/** Antenna/Cable key. Toggles broadcast input source between antenna and cable. */
const val AKEYCODE_TV_ANTENNA_CABLE = 242
/** HDMI #1 key. Switches to HDMI input #1. */
const val AKEYCODE_TV_INPUT_HDMI_1 = 243
/** HDMI #2 key. Switches to HDMI input #2. */
const val AKEYCODE_TV_INPUT_HDMI_2 = 244
/** HDMI #3 key. Switches to HDMI input #3. */
const val AKEYCODE_TV_INPUT_HDMI_3 = 245
/** HDMI #4 key. Switches to HDMI input #4. */
const val AKEYCODE_TV_INPUT_HDMI_4 = 246
/** Composite #1 key. Switches to composite video input #1. */
const val AKEYCODE_TV_INPUT_COMPOSITE_1 = 247
/** Composite #2 key. Switches to composite video input #2. */
const val AKEYCODE_TV_INPUT_COMPOSITE_2 = 248
/** Component #1 key. Switches to component video input #1. */
const val AKEYCODE_TV_INPUT_COMPONENT_1 = 249
/** Component #2 key. Switches to component video input #2. */
const val AKEYCODE_TV_INPUT_COMPONENT_2 = 250
/** VGA #1 key. Switches to VGA (analog RGB) input #1. */
const val AKEYCODE_TV_INPUT_VGA_1 = 251
/** Audio description key. Toggles audio description off / on. */
const val AKEYCODE_TV_AUDIO_DESCRIPTION = 252
/** Audio description mixing volume up key. Increases audio description volume as compared with normal audio volume. */
const val AKEYCODE_TV_AUDIO_DESCRIPTION_MIX_UP = 253
/** Audio description mixing volume down key. Reduces audio description volume as compared with normal audio volume. */
const val AKEYCODE_TV_AUDIO_DESCRIPTION_MIX_DOWN = 254
/** Zoom mode key. Changes Zoom mode (Normal, Full, Zoom, Wide-zoom, etc.) */
const val AKEYCODE_TV_ZOOM_MODE = 255
/** Contents menu key. Goes to the title list. Corresponds to Contents Menu(0x0B) of CEC User Control Code */
const val AKEYCODE_TV_CONTENTS_MENU = 256
/**
 * Media context menu key. Goes to the context menu of media contents.
 * Corresponds to Media Context-sensitive Menu(0x11) of CEC User Control Code.
 */
const val AKEYCODE_TV_MEDIA_CONTEXT_MENU = 257
/**
 * Timer programming key. Goes to the timer recording menu. Corresponds to Timer Programming(0x54)
 * of CEC User Control Code.
 */
const val AKEYCODE_TV_TIMER_PROGRAMMING = 258
/** Help key. */
const val AKEYCODE_HELP = 259
const val AKEYCODE_NAVIGATE_PREVIOUS = 260
const val AKEYCODE_NAVIGATE_NEXT = 261
const val AKEYCODE_NAVIGATE_IN = 262
const val AKEYCODE_NAVIGATE_OUT = 263
/** Primary stem key for Wear. Main power/reset button on watch. */
const val AKEYCODE_STEM_PRIMARY = 264
/** Generic stem key 1 for Wear. */
const val AKEYCODE_STEM_1 = 265
/** Generic stem key 2 for Wear. */
const val AKEYCODE_STEM_2 = 266
/** Generic stem key 3 for Wear. */
const val AKEYCODE_STEM_3 = 267
/** Directional Pad Up-Left. */
const val AKEYCODE_DPAD_UP_LEFT = 268
/** Directional Pad Down-Left. */
const val AKEYCODE_DPAD_DOWN_LEFT = 269
/** Directional Pad Up-Right. */
const val AKEYCODE_DPAD_UP_RIGHT = 270
/** Directional Pad Down-Right. */
const val AKEYCODE_DPAD_DOWN_RIGHT = 271
/** Skip forward media key. */
const val AKEYCODE_MEDIA_SKIP_FORWARD = 272
/** Skips backward media key. */
const val AKEYCODE_MEDIA_SKIP_BACKWARD = 273
/** Steps forward media key. Steps media forward one from at a time. */
const val AKEYCODE_MEDIA_STEP_FORWARD = 274
/** Steps backward media key. Steps media backward one from at a time. */
const val AKEYCODE_MEDIA_STEP_BACKWARD = 275
/** Puts device to sleep unless a wakelock is held. */
const val AKEYCODE_SOFT_SLEEP = 276
/** Cut key. */
const val AKEYCODE_CUT = 277
/** Copy key. */
const val AKEYCODE_COPY = 278
/** Paste key. */
const val AKEYCODE_PASTE = 279
/** Fingerprint navigation key, up. */
const val AKEYCODE_SYSTEM_NAVIGATION_UP = 280
/** Fingerprint navigation key, down. */
const val AKEYCODE_SYSTEM_NAVIGATION_DOWN = 281
/** Fingerprint navigation key, left. */
const val AKEYCODE_SYSTEM_NAVIGATION_LEFT = 282
/** Fingerprint navigation key, right. */
const val AKEYCODE_SYSTEM_NAVIGATION_RIGHT = 283
/** All apps. */
const val AKEYCODE_ALL_APPS = 284
