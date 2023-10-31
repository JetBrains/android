/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.streaming.device.RequestDeviceStateMessage.Companion.PHYSICAL_STATE
import com.android.utils.Base128InputStream
import com.android.utils.Base128InputStream.StreamFormatException
import com.android.utils.Base128OutputStream
import com.android.utils.FlightRecorder
import com.android.utils.TraceUtils
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap
import kotlin.text.Charsets.UTF_8

// Classes in this file have to be kept in sync with tools/adt/idea/streaming/screen-sharing-agent/app/src/main/cpp/control_messages.h.

/**
 * Common base of all control messages. All subclassed containing data should be data classes.
 */
sealed class ControlMessage(val type: Int) {

  open fun serialize(stream: Base128OutputStream) {
    stream.writeInt(type)
  }

  override fun equals(other: Any?): Boolean {
    return javaClass == other?.javaClass
  }

  override fun hashCode(): Int {
    return javaClass.hashCode()
  }

  override fun toString(): String {
    return javaClass.simpleName
  }

  interface Deserializer {
    fun deserialize(stream: Base128InputStream): ControlMessage
  }

  companion object : Deserializer {
    override fun deserialize(stream: Base128InputStream): ControlMessage {
      val message = when (val type = stream.readInt()) {
        MotionEventMessage.TYPE -> MotionEventMessage.deserialize(stream)
        KeyEventMessage.TYPE -> KeyEventMessage.deserialize(stream)
        TextInputMessage.TYPE -> TextInputMessage.deserialize(stream)
        SetDeviceOrientationMessage.TYPE -> SetDeviceOrientationMessage.deserialize(stream)
        SetMaxVideoResolutionMessage.TYPE -> SetMaxVideoResolutionMessage.deserialize(stream)
        StartVideoStreamMessage.TYPE -> StartVideoStreamMessage.deserialize(stream)
        StopVideoStreamMessage.TYPE -> StopVideoStreamMessage.deserialize(stream)
        StartClipboardSyncMessage.TYPE -> StartClipboardSyncMessage.deserialize(stream)
        StopClipboardSyncMessage.TYPE -> StopClipboardSyncMessage.deserialize(stream)
        RequestDeviceStateMessage.TYPE -> RequestDeviceStateMessage.deserialize(stream)
        ClipboardChangedNotification.TYPE -> ClipboardChangedNotification.deserialize(stream)
        SupportedDeviceStatesNotification.TYPE -> SupportedDeviceStatesNotification.deserialize(stream)
        DeviceStateNotification.TYPE -> DeviceStateNotification.deserialize(stream)
        else -> throw StreamFormatException("Unrecognized control message type $type")
      }
      FlightRecorder.log { "${TraceUtils.currentTime()} deserialize: message = $message" }
      return message
    }
  }
}

/** Represents an Android [MotionEvent](https://developer.android.com/reference/android/view/MotionEvent). */
internal data class MotionEventMessage(
  val pointers: List<Pointer>,
  val action: Int,
  val buttonState: Int,
  val actionButton: Int,
  val displayId: Int
) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(pointers.size)
    for (pointer in pointers) {
      pointer.serialize(stream)
    }
    stream.writeInt(action)
    stream.writeInt(buttonState)
    stream.writeInt(actionButton)
    stream.writeInt(displayId)
  }

  override fun toString(): String {
    return "MotionEventMessage(pointers=$pointers, action=$action, buttonState=$buttonState actionButton=$actionButton displayId=$displayId)"
  }

  companion object : Deserializer {
    const val TYPE = 1

    // Constants from android.view.MotionEvent.
    // - Actions
    const val ACTION_DOWN = 0
    const val ACTION_UP = 1
    const val ACTION_MOVE = 2
    const val ACTION_CANCEL = 3
    const val ACTION_OUTSIDE = 4
    const val ACTION_POINTER_DOWN = 5
    const val ACTION_POINTER_UP = 6
    const val ACTION_HOVER_MOVE = 7
    const val ACTION_SCROLL = 8

    // - Axes
    const val AXIS_VSCROLL = 9
    const val AXIS_HSCROLL = 10

    // - Button
    const val BUTTON_PRIMARY = 1 shl 0
    const val BUTTON_SECONDARY = 1 shl 1
    const val BUTTON_TERTIARY = 1 shl 2

    // - Other
    const val ACTION_MASK = 0xff
    const val ACTION_POINTER_INDEX_MASK = 0xff00
    const val ACTION_POINTER_INDEX_SHIFT = 8

    override fun deserialize(stream: Base128InputStream): ControlMessage {
      val n = stream.readInt()
      val pointers = ArrayList<Pointer>(n)
      for (i in 0 until n) {
        pointers.add(deserializePointer(stream))
      }
      val action = stream.readInt()
      val buttonState = stream.readInt()
      val actionButton = stream.readInt()
      val displayId = stream.readInt()
      return MotionEventMessage(pointers, action, buttonState, actionButton, displayId)
    }

    fun deserializePointer(stream: Base128InputStream): Pointer {
      val x = stream.readInt()
      val y = stream.readInt()
      val pointerId = stream.readInt()
      var axisValues: Int2FloatOpenHashMap? = null
      val numAxisValues = stream.readInt()
      if (numAxisValues > 0) {
        axisValues = Int2FloatOpenHashMap(numAxisValues)
        repeat(numAxisValues) {
          axisValues.put(stream.readInt(), stream.readFloat())
        }
      }
      return Pointer(x, y, pointerId, axisValues)
    }
  }

  data class Pointer(val x: Int, val y: Int, val pointerId: Int, val axisValues: Int2FloatOpenHashMap? = null) {

    fun serialize(stream: Base128OutputStream) {
      stream.writeInt(x)
      stream.writeInt(y)
      stream.writeInt(pointerId)
      stream.writeInt(axisValues?.size ?: 0)
      if (axisValues != null) {
        val iterator = axisValues.int2FloatEntrySet().fastIterator()
        while (iterator.hasNext()) {
          val entry = iterator.next()
          stream.writeInt(entry.intKey)
          stream.writeFloat(entry.floatValue)
        }
      }
    }

    override fun toString(): String {
      if (axisValues == null) return "Pointer(x=$x, y=$y, pointerId=$pointerId)"
      return "Pointer(x=$x, y=$y, pointerId=$pointerId, axisValues=$axisValues)"
    }
  }
}

/** Represents a key being pressed or released on a keyboard. */
internal data class KeyEventMessage(
  val action: AndroidKeyEventActionType,
  val keyCode: Int, // One of the values defined in AndroidKeyCodes.kt
  val metaState: Int
) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(action.value)
    stream.writeInt(keyCode)
    stream.writeInt(metaState)
  }

  override fun toString(): String {
    return "KeyEventMessage(action=$action, keyCode=$keyCode, metaState=0x${metaState.toString(16)})"
  }

  companion object : Deserializer {
    const val TYPE = 2

    override fun deserialize(stream: Base128InputStream): KeyEventMessage {
      val actionValue = stream.readInt()
      val action = AndroidKeyEventActionType.fromValue(actionValue) ?: throw StreamFormatException("Unrecognized action: $actionValue")
      val keyCode = stream.readInt()
      val metaState = stream.readInt()
      return KeyEventMessage(action, keyCode, metaState)
    }
  }
}

/** Represents one or more characters typed on a keyboard. */
internal data class TextInputMessage(
  val text: String
) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeString(text)
  }

  override fun toString(): String {
    return "TextInputMessage(text=\"$text\")"
  }

  companion object : Deserializer {
    const val TYPE = 3

    override fun deserialize(stream: Base128InputStream): TextInputMessage {
      val text = stream.readString() ?: throw StreamFormatException("Malformed TextInputMessage")
      return TextInputMessage(text)
    }
  }
}

/** Represents one or more characters typed on a keyboard. */
internal data class SetDeviceOrientationMessage(val orientation: Int) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(orientation)
  }

  override fun toString(): String {
    return "SetDeviceOrientationMessage(orientation=$orientation)"
  }

  companion object : Deserializer {
    const val TYPE = 4

    override fun deserialize(stream: Base128InputStream): SetDeviceOrientationMessage {
      val orientation = stream.readInt()
      return SetDeviceOrientationMessage(orientation)
    }
  }
}

/** Sets maximum display streaming resolution. */
internal data class SetMaxVideoResolutionMessage(val width: Int, val height: Int) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(width)
    stream.writeInt(height)
  }

  override fun toString(): String {
    return "SetMaxVideoResolutionMessage(width=$width, height=$height)"
  }

  companion object : Deserializer {
    const val TYPE = 5

    override fun deserialize(stream: Base128InputStream): SetMaxVideoResolutionMessage {
      val width = stream.readInt()
      val height = stream.readInt()
      return SetMaxVideoResolutionMessage(width, height)
    }
  }
}

/** Starts video stream if it was stopped. */
internal class StartVideoStreamMessage private constructor() : ControlMessage(TYPE) {

  companion object : Deserializer {
    const val TYPE = 6
    val instance = StartVideoStreamMessage()

    override fun deserialize(stream: Base128InputStream): StartVideoStreamMessage {
      return instance
    }
  }
}

/** Stops video stream. */
internal class StopVideoStreamMessage private constructor() : ControlMessage(TYPE) {

  companion object : Deserializer {
    const val TYPE = 7
    val instance = StopVideoStreamMessage()

    override fun deserialize(stream: Base128InputStream): StopVideoStreamMessage {
      return instance
    }
  }
}

/** Sets device clipboard and requests clipboard updates from the device. */
internal data class StartClipboardSyncMessage(val maxSyncedLength: Int, val text: String) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(maxSyncedLength)
    stream.writeBytes(text.toByteArray(UTF_8))
  }

  override fun toString(): String {
    return "StartClipboardSyncMessage(maxSyncedLength=$maxSyncedLength, text='$text')"
  }

  companion object : Deserializer {
    const val TYPE = 8

    override fun deserialize(stream: Base128InputStream): StartClipboardSyncMessage {
      val maxSyncedLength = stream.readInt()
      val bytes = stream.readBytes()
      return StartClipboardSyncMessage(maxSyncedLength, bytes.toString(UTF_8))
    }
  }
}

/** Stops clipboard updates from the device. */
internal class StopClipboardSyncMessage private constructor(): ControlMessage(TYPE) {

  companion object : Deserializer {
    const val TYPE = 9
    val instance = StopClipboardSyncMessage()

    override fun deserialize(stream: Base128InputStream): StopClipboardSyncMessage {
      return instance
    }
  }
}

/**
 * Requests a device state (folding pose) change. A DeviceStateNotification message will be sent
 * when and if the device state actually changes. If [state] is equal to [PHYSICAL_STATE],
 * the device will return to its actual physical state.
 */
internal data class RequestDeviceStateMessage(val state: Int) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(state + 1) // Add 1 to make sure that PHYSICAL_STATE is encoded efficiently.
  }

  override fun toString(): String {
    return "RequestDeviceStateMessage(state=$state)"
  }

  companion object : Deserializer {
    const val TYPE = 10

    const val PHYSICAL_STATE = -1;

    override fun deserialize(stream: Base128InputStream): RequestDeviceStateMessage {
      val state = stream.readInt() - 1 // Subtracting 1 to account for shifted encoding.
      return RequestDeviceStateMessage(state)
    }
  }
}

/** A clipboard update from the device. */
internal data class ClipboardChangedNotification(val text: String) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeBytes(text.toByteArray(UTF_8))
  }

  override fun toString(): String {
    return "ClipboardChangedNotification(text=\"$text\")"
  }

  companion object : Deserializer {
    const val TYPE = 11

    override fun deserialize(stream: Base128InputStream): ClipboardChangedNotification {
      val bytes = stream.readBytes()
      return ClipboardChangedNotification(bytes.toString(UTF_8))
    }
  }
}

/**
 * A notification that the device supports multiple folding states. The text of the notification
 * is the same as output of  the 'adb shell cmd device_state print-states' command, e.g.
 * ```
 * Supported states: [
 *   DeviceState{identifier=0, name='CLOSE', app_accessible=true},
 *   DeviceState{identifier=1, name='TENT', app_accessible=true},
 *   DeviceState{identifier=2, name='HALF_FOLDED', app_accessible=true},
 *   DeviceState{identifier=3, name='OPEN', app_accessible=true},
 * ]
 * ```
 */
internal data class SupportedDeviceStatesNotification(val text: String) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeBytes(text.toByteArray(UTF_8))
  }

  override fun toString(): String {
    return "SupportedDeviceStatesNotification(text=\"$text\")"
  }

  companion object : Deserializer {
    const val TYPE = 12

    override fun deserialize(stream: Base128InputStream): SupportedDeviceStatesNotification {
      val bytes = stream.readBytes()
      return SupportedDeviceStatesNotification(bytes.toString(UTF_8))
    }
  }
}

/**
 * Notification of a device state change. One such notification is always sent when the screen
 * sharing agent starts on a foldable device,
 */
internal data class DeviceStateNotification(val deviceState: Int) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(deviceState)
  }

  override fun toString(): String {
    return "DeviceStateNotification(deviceState=$deviceState)"
  }

  companion object : Deserializer {
    const val TYPE = 13

    override fun deserialize(stream: Base128InputStream): DeviceStateNotification {
      val deviceState = stream.readInt()
      return DeviceStateNotification(deviceState)
    }
  }
}
