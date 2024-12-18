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

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.streaming.core.AndroidInputEvent.TouchEvent.Touch
import com.intellij.openapi.components.Service
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import it.unimi.dsi.fastutil.ints.IntArraySet

@Service(Service.Level.PROJECT)
class DeviceInputListenerManager {

  private val deviceInputDispatchers = mutableMapOf<String, DeviceInputListenerDispatcher>()

  /** Adds a [listener] that will be called when an input event is sent to the device. */
  @UiThread
  fun addDeviceInputListener(deviceSerialNumber: String, listener: DeviceInputListener) {
    val dispatcher = deviceInputDispatchers.computeIfAbsent(deviceSerialNumber) { serialNumber ->
      DeviceInputListenerDispatcher(serialNumber)
    }
    dispatcher.addListener(listener)
  }

  /** Adds a [listener] that will be called when an input event is sent to the device. */
  @UiThread
  fun removeDeviceInputListener(deviceSerialNumber: String, listener: DeviceInputListener) {
    val dispatcher = deviceInputDispatchers[deviceSerialNumber] ?: return
    if (dispatcher.removeListener(listener) && !dispatcher.hasListeners()) {
      deviceInputDispatchers.remove(deviceSerialNumber)
    }
  }

  internal fun notifyListenersOfTouchEvent(deviceSerialNumber: String, displayId: Int, displayWidth: Int, displayHeight: Int,
                                           displayOrientation: Int, x: Int, y: Int, endOfTouch: Boolean, multiTouch: Boolean) {
    val dispatcher = deviceInputDispatchers[deviceSerialNumber] ?: return
    dispatcher.notifyListenersOfTouchEvent(displayId, displayWidth, displayHeight, displayOrientation, x, y, endOfTouch, multiTouch)
  }
}

@UiThread
private class DeviceInputListenerDispatcher(val deviceSerialNumber: String) {

  private val listeners = ContainerUtil.createLockFreeCopyOnWriteList<DeviceInputListener>()
  private val backgroundExecutor =
      AppExecutorUtil.createBoundedApplicationPoolExecutor("DeviceInputListenerDispatcher($deviceSerialNumber)", 1)
  private val draggingOnDisplays = IntArraySet()

  fun addListener(listener: DeviceInputListener) {
    listeners.add(listener)
  }

  fun removeListener(listener: DeviceInputListener): Boolean =
      listeners.remove(listener)

  fun hasListeners(): Boolean =
      listeners.isNotEmpty()

  fun notifyListenersOfTouchEvent(displayId: Int, displayWidth: Int, displayHeight: Int, displayOrientation: Int, x: Int, y: Int,
                                  endOfTouch: Boolean, multiTouch: Boolean) {
    val timestamp = System.currentTimeMillis()
    val display = AndroidInputEvent.DisplayInfo(displayId, displayWidth, displayHeight, displayOrientation)
    val touches = when {
      endOfTouch -> {
        if (draggingOnDisplays.remove(displayId)) {
          emptyList<Touch>()
        }
        else {
          return
        }
      }
      multiTouch -> {
        draggingOnDisplays.add(displayId)
        listOf(Touch(x, y, 0), Touch(displayWidth - 1 - x, displayHeight - 1 - y, 1,))
      }
      else -> {
        draggingOnDisplays.add(displayId)
        listOf(Touch(x, y, 0))
      }
    }
    val event = AndroidInputEvent.TouchEvent(deviceSerialNumber, display, timestamp, touches)
    backgroundExecutor.submit {
      for (listener in listeners) {
        listener.eventSent(event)
      }
    }
  }
}