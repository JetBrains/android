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
package com.android.tools.idea.device

import com.android.utils.Base128InputStream
import com.android.utils.Base128InputStream.StreamFormatException
import com.android.utils.Base128OutputStream

/**
 * Common base of all control messages.
 */
sealed class ControlMessage(val type: Int) {

  open fun serialize(stream: Base128OutputStream) {
    stream.writeInt(type)
  }

  abstract class Deserializer {
    abstract fun deserialize(stream: Base128InputStream): ControlMessage
  }

  companion object : Deserializer() {
    override fun deserialize(stream: Base128InputStream): ControlMessage {
      return when (val type = stream.readInt()) {
        MotionEventMessage.TYPE -> MotionEventMessage.deserialize(stream)
        KeyEventMessage.TYPE -> KeyEventMessage.deserialize(stream)
        TextInputMessage.TYPE -> TextInputMessage.deserialize(stream)
        SetDeviceOrientationMessage.TYPE -> SetDeviceOrientationMessage.deserialize(stream)
        SetMaxVideoResolutionMessage.TYPE -> SetMaxVideoResolutionMessage.deserialize(stream)
        else -> throw StreamFormatException("Unrecognized control message type $type")
      }
    }
  }
}

/** Represents an Android [MotionEvent](https://developer.android.com/reference/android/view/MotionEvent). */
internal data class MotionEventMessage(
  val pointers: List<Pointer>,
  val action: Int,
  val displayId: Int
) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(pointers.size)
    for (pointer in pointers) {
      pointer.serialize(stream)
    }
    stream.writeInt(action)
    stream.writeInt(displayId)
  }

  companion object : Deserializer() {
    const val TYPE = 1

    // Constants from android.view.MotionEvent.
    const val ACTION_DOWN = 0
    const val ACTION_UP = 1
    const val ACTION_MOVE = 2
    const val ACTION_CANCEL = 3
    const val ACTION_OUTSIDE = 4
    const val ACTION_POINTER_DOWN = 5
    const val ACTION_POINTER_UP = 6
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
      val displayId = stream.readInt()
      return MotionEventMessage(pointers, action, displayId)
    }

    fun deserializePointer(stream: Base128InputStream): Pointer {
      val x = stream.readInt()
      val y = stream.readInt()
      val pointerId = stream.readInt()
      return Pointer(x, y, pointerId)
    }
  }

  data class Pointer(val x: Int, val y: Int, val pointerId: Int) {

    fun serialize(stream: Base128OutputStream) {
      stream.writeInt(x)
      stream.writeInt(y)
      stream.writeInt(pointerId)
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

  companion object : Deserializer() {
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

  companion object : Deserializer() {
    const val TYPE = 3

    override fun deserialize(stream: Base128InputStream): TextInputMessage {
      val text = stream.readString() ?: throw StreamFormatException("Malformed TextInputMessage")
      return TextInputMessage(text)
    }
  }
}

/** Represents one or more characters typed on a keyboard. */
internal class SetDeviceOrientationMessage(val orientation: Int) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(orientation)
  }

  companion object : Deserializer() {
    const val TYPE = 4

    override fun deserialize(stream: Base128InputStream): SetDeviceOrientationMessage {
      val orientation = stream.readInt()
      return SetDeviceOrientationMessage(orientation)
    }
  }
}

/** Sets maximum display streaming resolution. */
internal class SetMaxVideoResolutionMessage(val width: Int, val height: Int) : ControlMessage(TYPE) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(width)
    stream.writeInt(height)
  }

  companion object : Deserializer() {
    const val TYPE = 5

    override fun deserialize(stream: Base128InputStream): SetMaxVideoResolutionMessage {
      val width = stream.readInt()
      val height = stream.readInt()
      return SetMaxVideoResolutionMessage(width, height)
    }
  }
}
