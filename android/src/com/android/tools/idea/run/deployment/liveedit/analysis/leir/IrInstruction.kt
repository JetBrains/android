/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit.analysis.leir

class IrInstruction(val type: Type, val opcode: Int, val params: List<Any?>, val prev: IrInstruction?) {
  enum class Type {
    INSTRUCTION,
    LABEL,
    LINE
  }

  var next: IrInstruction? = null
    private set

  /**
   * Returns the next [IrInstruction] with a [Type] of [Type.INSTRUCTION], skipping over [Type.LABEL] and [Type.LINE] instructions.
   */
  val nextInsn: IrInstruction? get() {
    var cur = next
    while (cur != null && cur.type != Type.INSTRUCTION) {
      cur = cur.next
    }
    return cur
  }

  init {
    if (prev != null) {
      prev.next = this
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }

    if (javaClass != other?.javaClass) {
      return false
    }

    other as IrInstruction
    return type == other.type && opcode == other.opcode && params == other.params
  }

  override fun hashCode(): Int {
    var result = type.hashCode()
    result = 31 * result + opcode
    result = 31 * result + params.hashCode()
    return result
  }

  override fun toString(): String {
    return when (type) {
      Type.INSTRUCTION -> "$opcode " + params.joinToString(" ") { "$it" }
      Type.LABEL -> "LABEL ${(params[0] as IrLabels.IrLabel).index}"
      Type.LINE -> "LINE ${params[0]} (LABEL ${(params[1] as IrLabels.IrLabel).index})"
    }
  }
}