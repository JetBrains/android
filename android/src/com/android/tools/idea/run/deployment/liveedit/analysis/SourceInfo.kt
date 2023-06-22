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
package com.android.tools.idea.run.deployment.liveedit.analysis

import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrInstruction
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrInstructionList
import org.objectweb.asm.Opcodes

private const val kComposerClass = "androidx/compose/runtime/ComposerKt"

internal fun onlyHasSourceInfoChanges(old: IrInstructionList, new: IrInstructionList): Boolean {
  var oldInsn = old.first
  var newInsn = new.first
  while (oldInsn != null || newInsn != null) {
    if (oldInsn != newInsn && oldInsn != null && newInsn != null) {
      val isSourceInfo = isSourceInformation(oldInsn) && isSourceInformation(newInsn)
      val isSourceInfoMarker = isSourceInformationMarkerStart(oldInsn) && isSourceInformationMarkerStart(newInsn)
      if (!isSourceInfo && !isSourceInfoMarker) {
        return false
      }
    }
    oldInsn = oldInsn?.next
    newInsn = newInsn?.next
  }
  return true
}

private fun isSourceInformation(insn: IrInstruction): Boolean {
  if (insn.type != IrInstruction.Type.INSTRUCTION || insn.opcode != Opcodes.LDC || insn.params[0] !is String) return false

  val sourceInfo = insn.nextInsn ?: return false
  return sourceInfo.opcode == Opcodes.INVOKESTATIC &&
         sourceInfo.params[0] as? String == kComposerClass &&
         sourceInfo.params[1] as? String == "sourceInformation"
}

private fun isSourceInformationMarkerStart(insn: IrInstruction): Boolean {
  if (insn.type != IrInstruction.Type.INSTRUCTION || insn.opcode != Opcodes.LDC || insn.params[0] !is Int) return false

  val stringArg = insn.nextInsn ?: return false
  if (stringArg.type != IrInstruction.Type.INSTRUCTION || stringArg.opcode != Opcodes.LDC || stringArg.params[0] !is String) return false

  val sourceInfo = stringArg.nextInsn ?: return false
  return sourceInfo.opcode == Opcodes.INVOKESTATIC &&
         sourceInfo.params[0] as? String == kComposerClass &&
         sourceInfo.params[1] as? String == "sourceInformationMarkerStart"
}