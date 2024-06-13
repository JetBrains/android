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

import com.android.tools.idea.streaming.core.DisplayDescriptor
import com.android.tools.idea.streaming.core.DisplayType
import com.android.tools.idea.streaming.device.RequestDeviceStateMessage.Companion.PHYSICAL_STATE
import com.android.utils.Base128InputStream
import com.android.utils.Base128InputStream.StreamFormatException
import com.android.utils.Base128OutputStream
import com.android.utils.FlightRecorder
import com.android.utils.TraceUtils
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap
import java.awt.Dimension
import kotlin.text.Charsets.UTF_8

// Classes in this file have to be kept in sync with tools/adt/idea/streaming/screen-sharing-agent/app/src/main/cpp/control_messages.h.

/**
 * Common base of all control messages. All subclassed containing data should be data classes.
 */
sealed class ControlMessage(val type: Int) {

  open fun serialize(stream: Base128OutputStream) {
    stream.writeInt(type)
  }

  override fun equals(other: Any?): Boolean =
      javaClass == other?.javaClass

  override fun hashCode(): Int =
      javaClass.hashCode()

  override fun toString(): String =
      javaClass.simpleName

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
        StartAudioStreamMessage.TYPE -> StartAudioStreamMessage.deserialize(stream)
        StopAudioStreamMessage.TYPE -> StopAudioStreamMessage.deserialize(stream)
        StartClipboardSyncMessage.TYPE -> StartClipboardSyncMessage.deserialize(stream)
        StopClipboardSyncMessage.TYPE -> StopClipboardSyncMessage.deserialize(stream)
        RequestDeviceStateMessage.TYPE -> RequestDeviceStateMessage.deserialize(stream)
        DisplayConfigurationRequest.TYPE -> DisplayConfigurationRequest.deserialize(stream)
        ErrorResponse.TYPE -> ErrorResponse.deserialize(stream)
        DisplayConfigurationResponse.TYPE -> DisplayConfigurationResponse.deserialize(stream)
        ClipboardChangedNotification.TYPE -> ClipboardChangedNotification.deserialize(stream)
        SupportedDeviceStatesNotification.TYPE -> SupportedDeviceStatesNotification.deserialize(stream)
        DeviceStateNotification.TYPE -> DeviceStateNotification.deserialize(stream)
        DisplayAddedNotification.TYPE -> DisplayAddedNotification.deserialize(stream)
        DisplayRemovedNotification.TYPE -> DisplayRemovedNotification.deserialize(stream)
        UiSettingsRequest.TYPE -> UiSettingsRequest.deserialize(stream)
        UiSettingsResponse.TYPE -> UiSettingsResponse.deserialize(stream)
        SetDarkModeMessage.TYPE -> SetDarkModeMessage.deserialize(stream)
        SetTalkBackMessage.TYPE -> SetTalkBackMessage.deserialize(stream)
        SetSelectToSpeakMessage.TYPE -> SetSelectToSpeakMessage.deserialize(stream)
        SetFontSizeMessage.TYPE -> SetFontSizeMessage.deserialize(stream)
        SetScreenDensityMessage.TYPE -> SetScreenDensityMessage.deserialize(stream)
        SetAppLanguageMessage.TYPE -> SetAppLanguageMessage.deserialize(stream)
        SetGestureNavigationMessage.TYPE -> SetGestureNavigationMessage.deserialize(stream)
        else -> throw StreamFormatException("Unrecognized control message type $type")
      }
      FlightRecorder.log { "${TraceUtils.currentTime} deserialize: message = $message" }
      return message
    }
  }
}

/** Interface that has to be implemented by all request and response control messages. */
internal abstract class CorrelatedMessage(type: Int) : ControlMessage(type) {
  abstract val requestId: Int

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(requestId)
  }
}

// Messages sent to the device.

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

  override fun toString(): String =
      "MotionEventMessage(pointers=$pointers, action=$action, buttonState=$buttonState actionButton=$actionButton displayId=$displayId)"

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
    const val ACTION_HOVER_ENTER = 9
    const val ACTION_HOVER_EXIT = 10
    const val ACTION_BUTTON_PRESS = 11
    const val ACTION_BUTTON_RELEASE = 12

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

    private fun deserializePointer(stream: Base128InputStream): Pointer {
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
      return if (axisValues == null) "Pointer(x=$x, y=$y, pointerId=$pointerId)"
             else "Pointer(x=$x, y=$y, pointerId=$pointerId, axisValues=$axisValues)"
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

  override fun toString(): String =
      "KeyEventMessage(action=$action, keyCode=$keyCode, metaState=0x${metaState.toString(16)})"

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

  override fun toString(): String =
      "TextInputMessage(text=\"$text\")"

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

  override fun toString(): String =
      "SetDeviceOrientationMessage(orientation=$orientation)"

  companion object : Deserializer {
    const val TYPE = 4

    override fun deserialize(stream: Base128InputStream): SetDeviceOrientationMessage {
      val orientation = stream.readInt()
      return SetDeviceOrientationMessage(orientation)
    }
  }
}

/** Sets maximum display streaming resolution. */
internal data class SetMaxVideoResolutionMessage(val displayId: Int, val maxVideoSize: Dimension) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(displayId)
    stream.writeInt(maxVideoSize.width)
    stream.writeInt(maxVideoSize.height)
  }

  override fun toString(): String =
      "SetMaxVideoResolutionMessage(displayId=$displayId, maxVideoSize=${maxVideoSize.width}x${maxVideoSize.height})"

  companion object : Deserializer {
    const val TYPE = 5

    override fun deserialize(stream: Base128InputStream): SetMaxVideoResolutionMessage {
      val displayId = stream.readInt()
      val width = stream.readInt()
      val height = stream.readInt()
      return SetMaxVideoResolutionMessage(displayId, Dimension(width, height))
    }
  }
}

/** Starts video stream if it was stopped. */
internal class StartVideoStreamMessage(val displayId: Int, val maxVideoSize: Dimension) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(displayId)
    stream.writeInt(maxVideoSize.width)
    stream.writeInt(maxVideoSize.height)
  }

  override fun toString(): String =
      "StartVideoStreamMessage(displayId=$displayId, maxVideoSize=${maxVideoSize.width}x${maxVideoSize.height})"

  companion object : Deserializer {
    const val TYPE = 6

    override fun deserialize(stream: Base128InputStream): StartVideoStreamMessage {
      val displayId = stream.readInt()
      val width = stream.readInt()
      val height = stream.readInt()
      return StartVideoStreamMessage(displayId, Dimension(width, height))
    }
  }
}

/** Stops video stream. */
internal class StopVideoStreamMessage(val displayId: Int) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(displayId)
  }

  override fun toString(): String =
      "StopVideoStreamMessage(displayId=$displayId)"

  companion object : Deserializer {
    const val TYPE = 7

    override fun deserialize(stream: Base128InputStream): StopVideoStreamMessage {
      val displayId = stream.readInt()
      return StopVideoStreamMessage(displayId)
    }
  }
}

/** Starts audio stream. */
internal class StartAudioStreamMessage : ControlMessage(TYPE) {

  override fun toString(): String =
      "StartAudioStreamMessage"

  companion object : Deserializer {
    const val TYPE = 8

    override fun deserialize(stream: Base128InputStream): StartAudioStreamMessage {
      return StartAudioStreamMessage()
    }
  }
}

/** Stops audio stream. */
internal class StopAudioStreamMessage : ControlMessage(TYPE) {

  override fun toString(): String =
      "StopAudioStreamMessage"

  companion object : Deserializer {
    const val TYPE = 9

    override fun deserialize(stream: Base128InputStream): StopAudioStreamMessage {
      return StopAudioStreamMessage()
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

  override fun toString(): String =
      "StartClipboardSyncMessage(maxSyncedLength=$maxSyncedLength, text='$text')"

  companion object : Deserializer {
    const val TYPE = 10

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
    const val TYPE = 11
    val instance = StopClipboardSyncMessage()

    override fun deserialize(stream: Base128InputStream): StopClipboardSyncMessage {
      return instance
    }
  }
}

/**
 * Requests a device state (folding pose) change. A DeviceStateNotification message will be sent
 * when and if the device state actually changes. If [deviceStateId] is equal to [PHYSICAL_STATE],
 * the device will return to its actual physical state.
 */
internal data class RequestDeviceStateMessage(val deviceStateId: Int) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(deviceStateId + 1) // Add 1 to make sure that PHYSICAL_STATE is encoded efficiently.
  }

  override fun toString(): String =
      "RequestDeviceStateMessage(deviceStateId=$deviceStateId)"

  companion object : Deserializer {
    const val TYPE = 12

    const val PHYSICAL_STATE = -1

    override fun deserialize(stream: Base128InputStream): RequestDeviceStateMessage {
      val deviceStateId = stream.readInt() - 1 // Subtracting 1 to account for shifted encoding.
      return RequestDeviceStateMessage(deviceStateId)
    }
  }
}

/** Requests a display screenshot. */
internal data class DisplayConfigurationRequest private constructor(override val requestId: Int) : CorrelatedMessage(TYPE) {

  constructor(requestIdGenerator: () -> Int) : this(requestIdGenerator())

  override fun toString(): String {
    return "DisplayConfigurationRequest(requestId=$requestId)"
  }

  companion object : Deserializer {
    const val TYPE = 13

    override fun deserialize(stream: Base128InputStream): DisplayConfigurationRequest {
      val requestId = stream.readInt()
      return DisplayConfigurationRequest(requestId)
    }
  }
}

// Messages received from the device.

/** Error response from the device to a command that is guaranteed to trigger a response. */
internal data class ErrorResponse(override val requestId: Int, val errorMessage: String) : CorrelatedMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeBytes(errorMessage.toByteArray(UTF_8))
  }

  override fun toString(): String {
    return "ErrorResponse(requestId=$requestId, errorMessage=\"$errorMessage\")"
  }

  companion object : Deserializer {
    const val TYPE = 14

    override fun deserialize(stream: Base128InputStream): ErrorResponse {
      val requestId = stream.readInt()
      val errorMessage = stream.readBytes().toString(UTF_8)
      return ErrorResponse(requestId, errorMessage)
    }
  }
}

/** Screenshot of a device display. */
internal class DisplayConfigurationResponse(override val requestId: Int, val displays: List<DisplayDescriptor>): CorrelatedMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(displays.size)
    for (display in displays) {
      stream.writeInt(display.displayId)
      stream.writeInt(display.width)
      stream.writeInt(display.height)
      stream.writeInt(display.orientation)
      stream.writeInt(display.type.ordinal)
    }
  }

  override fun toString(): String {
    return "ScreenshotResponse(requestId=$requestId, displays=$displays)"
  }

  companion object : Deserializer {
    const val TYPE = 15

    override fun deserialize(stream: Base128InputStream): DisplayConfigurationResponse {
      val requestId = stream.readInt()
      val count = stream.readInt()
      val displays = ArrayList<DisplayDescriptor>(count)
      for (i in 0 until count) {
        val displayId = stream.readInt()
        val width = stream.readInt()
        val height = stream.readInt()
        val orientation = stream.readInt()
        val type = try {
          DisplayType.values()[stream.readInt()]
        }
        catch (e: ArrayIndexOutOfBoundsException) {
          DisplayType.UNKNOWN
        }
        displays.add(DisplayDescriptor(displayId, width, height, orientation, type))
      }
      return DisplayConfigurationResponse(requestId, displays)
    }
  }
}

/** A clipboard update from the device. */
internal data class ClipboardChangedNotification(val text: String) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeBytes(text.toByteArray(UTF_8))
  }

  override fun toString(): String =
      "ClipboardChangedNotification(text=\"$text\")"

  companion object : Deserializer {
    const val TYPE = 16

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
internal data class SupportedDeviceStatesNotification(val deviceStates: List<DeviceState>, val deviceStateId: Int) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(deviceStates.size)
    for (state in deviceStates) {
      state.serialize(stream)
    }
    stream.writeInt(deviceStateId + 1) // Offset by 1 to efficiently represent -1.
  }

  override fun toString(): String =
      "SupportedDeviceStatesNotification(deviceStates=\"$deviceStates\", deviceStateId=$deviceStateId)"

  companion object : Deserializer {
    const val TYPE = 17

    override fun deserialize(stream: Base128InputStream): SupportedDeviceStatesNotification {
      val numStates = stream.readInt()
      val deviceStates = ArrayList<DeviceState>(numStates)
      for (i in 0 until numStates) {
        deviceStates.add(DeviceState.deserialize(stream))
      }
      val deviceStateId = stream.readInt() - 1
      return SupportedDeviceStatesNotification(deviceStates, deviceStateId)
    }
  }
}

/**
 * Notification of a device state change. One such notification is always sent when the screen
 * sharing agent starts on a foldable device,
 */
internal data class DeviceStateNotification(val deviceStateId: Int) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(deviceStateId + 1) // Offset by 1 to efficiently represent -1.
  }

  override fun toString(): String =
      "DeviceStateNotification(deviceStateId=$deviceStateId)"

  companion object : Deserializer {
    const val TYPE = 18

    override fun deserialize(stream: Base128InputStream): DeviceStateNotification {
      val deviceState = stream.readInt() - 1
      return DeviceStateNotification(deviceState)
    }
  }
}

/**
 * Notification of an added display.
 */
internal data class DisplayAddedNotification(val displayId: Int) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(displayId)
  }

  override fun toString(): String =
      "DisplayAddedNotification(displayId=$displayId)"

  companion object : Deserializer {
    const val TYPE = 19

    override fun deserialize(stream: Base128InputStream): DisplayAddedNotification {
      val displayId = stream.readInt()
      return DisplayAddedNotification(displayId)
    }
  }
}

/**
 * Notification of a removed display.
 */
internal data class DisplayRemovedNotification(val displayId: Int) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(displayId)
  }

  override fun toString(): String =
      "DisplayRemovedNotification(displayId=$displayId)"

  companion object : Deserializer {
    const val TYPE = 20

    override fun deserialize(stream: Base128InputStream): DisplayRemovedNotification {
      val displayId = stream.readInt()
      return DisplayRemovedNotification(displayId)
    }
  }
}

/**
 * Queries the current UI settings from a device.
 */
internal class UiSettingsRequest private constructor(override val requestId: Int) : CorrelatedMessage(TYPE) {

  constructor(requestIdGenerator: () -> Int) : this(requestIdGenerator())

  override fun toString(): String =
    "UiSettingsRequest(requestId=$requestId)"

  companion object : Deserializer {
    const val TYPE = 21

    override fun deserialize(stream: Base128InputStream): UiSettingsRequest {
      val requestId = stream.readInt()
      return UiSettingsRequest(requestId)
    }
  }
}

/**
 * The current UI settings received from a device.
 */
internal data class UiSettingsResponse(
  override val requestId: Int,
  val darkMode: Boolean,
  val gestureOverlayInstalled: Boolean,
  val gestureNavigation: Boolean,
  val foregroundApplicationId: String,
  val appLocale: String,
  val tackBackInstalled: Boolean,
  val talkBackOn: Boolean,
  val selectToSpeakOn: Boolean,
  val fontSizeSettable: Boolean,
  val fontSize: Int,
  val densitySettable: Boolean,
  val density: Int
) : CorrelatedMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeBoolean(darkMode)
    stream.writeBoolean(gestureOverlayInstalled)
    stream.writeBoolean(gestureNavigation)
    stream.writeBytes(foregroundApplicationId.toByteArray(UTF_8))
    stream.writeBytes(appLocale.toByteArray(UTF_8))
    stream.writeBoolean(tackBackInstalled)
    stream.writeBoolean(talkBackOn)
    stream.writeBoolean(selectToSpeakOn)
    stream.writeBoolean(fontSizeSettable)
    stream.writeInt(fontSize)
    stream.writeBoolean(densitySettable)
    stream.writeInt(density)
  }

  override fun toString(): String =
    "UiSettingsResponse(" +
    "requestId=$requestId, " +
    "darkMode=$darkMode, " +
    "gestureOverlayInstalled=$gestureOverlayInstalled, " +
    "gestureNavigation=$gestureNavigation, " +
    "foregroundApplicationId=\"$foregroundApplicationId\", " +
    "appLocale=\"$appLocale\", " +
    "tackBackInstalled=$tackBackInstalled, " +
    "talkBackOn=$talkBackOn, " +
    "selectToSpeakOn=$selectToSpeakOn, " +
    "fontSize=$fontSize, " +
    "density=$density)"

  companion object : Deserializer {
    const val TYPE = 22

    override fun deserialize(stream: Base128InputStream): UiSettingsResponse {
      val requestId = stream.readInt()
      val darkMode = stream.readBoolean()
      val gestureOverlayInstalled = stream.readBoolean()
      val gestureNavigation = stream.readBoolean()
      val foregroundApplicationId = stream.readBytes().toString(UTF_8)
      val appLocale = stream.readBytes().toString(UTF_8)
      val tackBackInstalled = stream.readBoolean()
      val talkBackOn = stream.readBoolean()
      val selectToSpeakOn = stream.readBoolean()
      val fontSizeSettable = stream.readBoolean()
      val fontSize = stream.readInt()
      val densitySettable = stream.readBoolean()
      val density = stream.readInt()
      return UiSettingsResponse(
        requestId,
        darkMode,
        gestureOverlayInstalled,
        gestureNavigation,
        foregroundApplicationId,
        appLocale,
        tackBackInstalled,
        talkBackOn,
        selectToSpeakOn,
        fontSizeSettable,
        fontSize,
        densitySettable,
        density
      )
    }
  }
}

/**
 * Changes the Dark Mode setting on a device.
 */
internal data class SetDarkModeMessage(val darkMode: Boolean) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeBoolean(darkMode)
  }

  override fun toString(): String =
    "SetDarkModeMessage(darkMode=$darkMode)"

  companion object : Deserializer {
    const val TYPE = 23

    override fun deserialize(stream: Base128InputStream): SetDarkModeMessage {
      val darkMode = stream.readBoolean()
      return SetDarkModeMessage(darkMode)
    }
  }
}

/**
 * Changes the Font Size setting on a device.
 *
 * The [fontSize] is specified as a percentage of the normal font.
 * A value of 100 is the normal size.
 */
internal data class SetFontSizeMessage(val fontSize: Int) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(fontSize)
  }

  override fun toString(): String =
    "SetFontSizeMessage(fontSize=$fontSize)"

  companion object : Deserializer {
    const val TYPE = 24

    override fun deserialize(stream: Base128InputStream): SetFontSizeMessage {
      val fontSize = stream.readInt()
      return SetFontSizeMessage(fontSize)
    }
  }
}

/**
 * Changes the Screen Density setting on a device.
 */
internal data class SetScreenDensityMessage(val density: Int) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(density)
  }

  override fun toString(): String =
    "SetScreenDensityMessage(density=$density)"

  companion object : Deserializer {
    const val TYPE = 25

    override fun deserialize(stream: Base128InputStream): SetScreenDensityMessage {
      val density = stream.readInt()
      return SetScreenDensityMessage(density)
    }
  }
}

/**
 * Turns TalkBack on or off.
 */
internal data class SetTalkBackMessage(val on: Boolean) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeBoolean(on)
  }

  override fun toString(): String =
    "SetTalkBackMessage(on=$on)"

  companion object : Deserializer {
    const val TYPE = 26

    override fun deserialize(stream: Base128InputStream): SetTalkBackMessage {
      val on = stream.readBoolean()
      return SetTalkBackMessage(on)
    }
  }
}

/**
 * Turns TalkBack on or off.
 */
internal data class SetSelectToSpeakMessage(val on: Boolean) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeBoolean(on)
  }

  override fun toString(): String =
    "SetSelectToSpeakMessage(on=$on)"

  companion object : Deserializer {
    const val TYPE = 27

    override fun deserialize(stream: Base128InputStream): SetSelectToSpeakMessage {
      val on = stream.readBoolean()
      return SetSelectToSpeakMessage(on)
    }
  }
}

/**
 * Changes the [locale] of the application identified by [applicationId].
 */
internal data class SetAppLanguageMessage(val applicationId: String, val locale: String) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeBytes(applicationId.toByteArray(UTF_8))
    stream.writeBytes(locale.toByteArray(UTF_8))
  }

  override fun toString(): String =
    "SetAppLanguageMessage(applicationId=\"$applicationId\", locale=\"$locale\")"

  companion object : Deserializer {
    const val TYPE = 28

    override fun deserialize(stream: Base128InputStream): SetAppLanguageMessage {
      val applicationId = stream.readBytes().toString(UTF_8)
      val locale = stream.readBytes().toString(UTF_8)
      return SetAppLanguageMessage(applicationId, locale)
    }
  }
}

/**
 * Changes the gesture navigation.
 */
internal data class SetGestureNavigationMessage(val on: Boolean) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeBoolean(on)
  }

  override fun toString(): String =
    "SetGestureNavigationMessage(on=$on)"

  companion object : Deserializer {
    const val TYPE = 29

    override fun deserialize(stream: Base128InputStream): SetGestureNavigationMessage {
      val on = stream.readBoolean()
      return SetGestureNavigationMessage(on)
    }
  }
}
