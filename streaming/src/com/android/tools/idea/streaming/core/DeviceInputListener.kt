/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.android.annotations.concurrency.AnyThread

/** Listener of input events sent by the Running Devices window to a physical or virtual Android device. */
interface DeviceInputListener {

  /** Called when an input event is sent to the device. All calls for the same device are serialized. */
  @AnyThread
  fun eventSent(event: AndroidInputEvent)
}

/** The value of [timestamp] is Time in milliseconds since epoch. */
sealed class AndroidInputEvent(val deviceSerialNumber: String, val display: DisplayInfo, val timestamp: Long) {

  /** Lifting fingers from the device display is communicated by a TouchEvent with an empty list of touches. */
  class TouchEvent(
    deviceSerialNumber: String,
    display: DisplayInfo,
    timestamp: Long, // Time in milliseconds since epoch.
    val touches: List<Touch>,
  ) : AndroidInputEvent(deviceSerialNumber, display, timestamp) {

    /** The touch coordinates correspond to the default display orientation. */
    data class Touch(val x: Int, val y: Int, val identifier: Int)
  }

  /** Key codes are listed in https://developer.android.com/reference/android/view/KeyEvent. */
  class KeyEvent(
    deviceSerialNumber: String,
    display: DisplayInfo,
    timestamp: Long, // Time in milliseconds since epoch.
    val eventType: KeyEventType,
    val keyCode: Int,
  ) : AndroidInputEvent(deviceSerialNumber, display, timestamp) {

    enum class KeyEventType {
      KEY_DOWN, KEY_UP
    }
  }

  class CharTypedEvent(
    deviceSerialNumber: String,
    display: DisplayInfo,
    timestamp: Long, // Time in milliseconds since epoch.
    val character: Char,
  ) : AndroidInputEvent(deviceSerialNumber, display, timestamp)

  /** The [width] and [height] properties correspond to the default display orientation. */
  data class DisplayInfo(val displayId: Int, val width: Int, val height: Int, val orientationQuadrants: Int)
}

