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
        MouseEventMessage.type -> MouseEventMessage.deserialize(stream)
        else -> throw StreamFormatException("Unrecognized control message type $type")
      }
    }
  }
}

/** Represents a mouse button being pressed or released or a mouse being moved. */
internal data class MouseEventMessage(
  val x: Int,
  val y: Int,
  val buttonMask: Int,
  val displayId: Int
) : ControlMessage(type) {

  override fun serialize(stream: Base128OutputStream) {
    super.serialize(stream)
    stream.writeInt(x)
    stream.writeInt(y)
    stream.writeInt(buttonMask)
    stream.writeInt(displayId)
  }

  companion object : Deserializer() {
    const val type = 0

    override fun deserialize(stream: Base128InputStream): ControlMessage {
      val x = stream.readInt()
      val y = stream.readInt()
      val buttonMask = stream.readInt()
      val displayId = stream.readInt()
      return MouseEventMessage(x, y, buttonMask, displayId)
    }
  }
}
