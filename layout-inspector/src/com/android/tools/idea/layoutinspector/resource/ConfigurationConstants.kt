/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.resource

// These constants are taken from android.content.res.Configuration

const val SCREENLAYOUT_SIZE_SMALL = 0x1
const val SCREENLAYOUT_SIZE_NORMAL = 0x2
const val SCREENLAYOUT_SIZE_LARGE = 0x3
const val SCREENLAYOUT_SIZE_XLARGE = 0x4
const val SCREENLAYOUT_SIZE_MASK = 0xf
const val SCREENLAYOUT_LONG_NO = 0x10
const val SCREENLAYOUT_LONG_YES = 0x20
const val SCREENLAYOUT_LAYOUTDIR_LTR = 0x40
const val SCREENLAYOUT_LAYOUTDIR_RTL = 0x80
const val SCREENLAYOUT_ROUND_NO = 0x100
const val SCREENLAYOUT_ROUND_YES = 0x200

const val COLOR_MODE_WIDE_COLOR_GAMUT_MASK = 0x3
const val COLOR_MODE_WIDE_COLOR_GAMUT_NO = 0x1
const val COLOR_MODE_WIDE_COLOR_GAMUT_YES = 0x2

const val COLOR_MODE_HDR_MASK = 0xc
const val COLOR_MODE_HDR_NO = 0x4
const val COLOR_MODE_HDR_YES = 0x8

const val ORIENTATION_PORTRAIT = 0x1
const val ORIENTATION_LANDSCAPE = 0x2

const val UI_MODE_TYPE_MASK = 0xf
const val UI_MODE_TYPE_NORMAL = 0x1
const val UI_MODE_TYPE_DESK = 0x2
const val UI_MODE_TYPE_CAR = 0x3
const val UI_MODE_TYPE_TELEVISION = 0x4
const val UI_MODE_TYPE_APPLIANCE = 0x5
const val UI_MODE_TYPE_WATCH = 0x6
const val UI_MODE_TYPE_VR_HEADSET = 0x7

const val UI_MODE_NIGHT_MASK = 0x30
const val UI_MODE_NIGHT_NO = 0x10
const val UI_MODE_NIGHT_YES = 0x20

const val TOUCHSCREEN_NOTOUCH = 0x1
const val TOUCHSCREEN_STYLUS = 0x2
const val TOUCHSCREEN_FINGER = 0x3

const val KEYBOARD_NOKEYS = 0x1
const val KEYBOARD_QWERTY = 0x2
const val KEYBOARD_12KEY = 0x3

const val KEYBOARDHIDDEN_NO = 0x1
const val KEYBOARDHIDDEN_YES = 0x2

const val HARDKEYBOARDHIDDEN_NO = 0x1
const val HARDKEYBOARDHIDDEN_YES = 0x2

const val NAVIGATIONHIDDEN_NO = 0x1
const val NAVIGATIONHIDDEN_YES = 0x2

const val NAVIGATION_NONAV = 0x1
const val NAVIGATION_DPAD = 0x2
const val NAVIGATION_TRACKBALL = 0x3
const val NAVIGATION_WHEEL = 0x4
