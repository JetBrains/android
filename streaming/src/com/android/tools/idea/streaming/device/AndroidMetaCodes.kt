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
// Based on [inout.h](https://android.googlesource.com/platform/frameworks/native/+/master/include/android/input.h)

/** No meta keys are pressed. */
const val AMETA_NONE = 0

/** This mask is used to check whether one of the ALT meta keys is pressed. */
const val AMETA_ALT_ON = 0x02

/** This mask is used to check whether the left ALT meta key is pressed. */
const val AMETA_ALT_LEFT_ON = 0x10

/** This mask is used to check whether the right ALT meta key is pressed. */
const val AMETA_ALT_RIGHT_ON = 0x20

/** This mask is used to check whether one of the SHIFT meta keys is pressed. */
const val AMETA_SHIFT_ON = 0x01

/** This mask is used to check whether the left SHIFT meta key is pressed. */
const val AMETA_SHIFT_LEFT_ON = 0x40

/** This mask is used to check whether the right SHIFT meta key is pressed. */
const val AMETA_SHIFT_RIGHT_ON = 0x80

/** This mask is used to check whether the SYM meta key is pressed. */
const val AMETA_SYM_ON = 0x04

/** This mask is used to check whether the FUNCTION meta key is pressed. */
const val AMETA_FUNCTION_ON = 0x08

/** This mask is used to check whether one of the CTRL meta keys is pressed. */
const val AMETA_CTRL_ON = 0x1000

/** This mask is used to check whether the left CTRL meta key is pressed. */
const val AMETA_CTRL_LEFT_ON = 0x2000

/** This mask is used to check whether the right CTRL meta key is pressed. */
const val AMETA_CTRL_RIGHT_ON = 0x4000

/** This mask is used to check whether one of the META meta keys is pressed. */
const val AMETA_META_ON = 0x10000

/** This mask is used to check whether the left META meta key is pressed. */
const val AMETA_META_LEFT_ON = 0x20000

/** This mask is used to check whether the right META meta key is pressed. */
const val AMETA_META_RIGHT_ON = 0x40000

/** This mask is used to check whether the CAPS LOCK meta key is on. */
const val AMETA_CAPS_LOCK_ON = 0x100000

/** This mask is used to check whether the NUM LOCK meta key is on. */
const val AMETA_NUM_LOCK_ON = 0x200000

/** This mask is used to check whether the SCROLL LOCK meta key is on. */
const val AMETA_SCROLL_LOCK_ON = 0x400000

// Mask combinations.
const val AMETA_CTRL_SHIFT_ON = AMETA_CTRL_ON or AMETA_SHIFT_ON
