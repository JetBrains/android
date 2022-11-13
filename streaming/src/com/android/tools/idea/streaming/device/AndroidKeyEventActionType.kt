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
package com.android.tools.idea.streaming.device

/**
 * Keyboard event actions. See https://developer.android.com/reference/android/view/KeyEvent.
 */
internal enum class AndroidKeyEventActionType(val value: Int) {
  /** The key has been pressed down. See android.view.KeyEvent.ACTION_DOWN. */
  ACTION_DOWN(0),

  /** The key has been released. See android.view.KeyEvent.ACTION_UP. */
  ACTION_UP(1),

  /** The key has been pressed and released. */
  ACTION_DOWN_AND_UP(8);

  companion object {
    @JvmStatic
    fun fromValue(value: Int): AndroidKeyEventActionType? = values().find { it.value == value }
  }
}