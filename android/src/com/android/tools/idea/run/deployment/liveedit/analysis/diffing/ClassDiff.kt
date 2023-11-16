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
package com.android.tools.idea.run.deployment.liveedit.analysis.diffing

import com.android.tools.idea.run.deployment.liveedit.analysis.leir.EnclosingMethod
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrAccessFlag
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrAnnotation
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrField
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrInstructionList
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrLabels
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrLocalVariable
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrMethod
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrParameter
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrTryCatchBlock

interface ClassDiff {
  /**
   * The fully qualified class name, in internal form. Example: com/example/MyClass
   */
  val name: String
  fun accept(visitor: ClassVisitor)

  /**
   * Print the full diff to the console for debugging.
   */
  fun println() = println(diffToString(this))
}

private fun diffToString(diff: ClassDiff) = buildString {
  appendLine("modified class ${diff.name}:")

  diff.accept(object : ClassVisitor {
    override fun visitAccess(added: Set<IrAccessFlag>, removed: Set<IrAccessFlag>) {
      if (added.isNotEmpty()) {
        appendLine("\tadded access flags: $added")
      }
      if (removed.isNotEmpty()) {
        appendLine("\tremoved access flags: $removed")
      }
    }

    override fun visitSignature(old: String?, new: String?) {
      appendLine("\tmodified signature: $old -> $new")
    }

    override fun visitSuperName(old: String?, new: String?) {
      appendLine("\tmodified superName: $old -> $new")
    }

    override fun visitInterfaces(added: Set<String>, removed: Set<String>) {
      if (added.isNotEmpty()) {
        appendLine("\tadded interfaces: $added")
      }
      if (removed.isNotEmpty()) {
        appendLine("\tremoved interfaces: $removed")
      }
    }

    override fun visitVersion(old: Int, new: Int) {
      appendLine("\tmodified version: $old -> $new")
    }

    override fun visitEnclosingMethod(old: EnclosingMethod?, new: EnclosingMethod?) {
      appendLine("\tmodified enclosing method: $old -> $new")
    }

    override fun visitMethods(added: List<IrMethod>, removed: List<IrMethod>, modified: List<MethodDiff>) {
      if (added.isNotEmpty()) {
        appendLine("\tadded methods: ${added.map { it.name + it.desc }}")
      }
      if (removed.isNotEmpty()) {
        appendLine("\tremoved methods: ${removed.map { it.name + it.desc }}")
      }
      if (modified.isNotEmpty()) {
        modified.forEach { appendLine(diffToString(it).prependIndent("\t")) }
      }
    }

    override fun visitFields(added: List<IrField>, removed: List<IrField>, modified: List<FieldDiff>) {
      if (added.isNotEmpty()) {
        appendLine("\tadded fields: ${added.map { it.name }}")
      }
      if (removed.isNotEmpty()) {
        appendLine("\tremoved fields: ${removed.map { it.name }}")
      }
      if (modified.isNotEmpty()) {
        modified.forEach { appendLine(diffToString(it).prependIndent("\t")) }
      }
    }

    override fun visitAnnotations(added: List<IrAnnotation>, removed: List<IrAnnotation>, modified: List<AnnotationDiff>) {
      if (added.isNotEmpty()) {
        appendLine("\tadded annotations: ${added.map { it.desc }}")
      }
      if (removed.isNotEmpty()) {
        appendLine("\tremoved annotations: ${removed.map { it.desc }}")
      }
      if (modified.isNotEmpty()) {
        modified.forEach { appendLine(diffToString(it).prependIndent("\t")) }
      }
    }
  })
}

private fun diffToString(diff: MethodDiff) = buildString {
  appendLine("modified method ${diff.name}${diff.desc}:")
  diff.accept(object : MethodVisitor {
    override fun visitAccess(added: Set<IrAccessFlag>, removed: Set<IrAccessFlag>) {
      if (added.isNotEmpty()) {
        appendLine("\tadded access flags: $added")
      }
      if (removed.isNotEmpty()) {
        appendLine("\tremoved access flags: $removed")
      }
    }

    override fun visitSignature(old: String?, new: String?) {
      appendLine("\tmodified signature: $old -> $new")
    }

    override fun visitInstructions(old: IrInstructionList, new: IrInstructionList) {
      appendLine(toString(old, new).prependIndent("\t"))
    }

    override fun visitParameters(added: List<IrParameter>, removed: List<IrParameter>, modified: List<ParameterDiff>) {
      if (added.isNotEmpty()) {
        appendLine("\tadded parameters: ${added.map { it.name }}")
      }
      if (removed.isNotEmpty()) {
        appendLine("\tremoved parameters: ${removed.map { it.name }}")
      }
      if (modified.isNotEmpty()) {
        modified.forEach { appendLine(diffToString(it).prependIndent("\t")) }
      }
    }

    override fun visitLocalVariables(added: List<IrLocalVariable>, removed: List<IrLocalVariable>, modified: List<LocalVariableDiff>) {
      if (added.isNotEmpty()) {
        appendLine("\tadded localVariables: ${added.map { it.name }}")
      }
      if (removed.isNotEmpty()) {
        appendLine("\tremoved localVariables: ${removed.map { it.name }}")
      }
      if (modified.isNotEmpty()) {
        modified.forEach { appendLine(diffToString(it).prependIndent("\t")) }
      }
    }

    override fun visitTryCatchBlocks(added: List<IrTryCatchBlock>, removed: List<IrTryCatchBlock>, modified: List<TryCatchBlockDiff>) {
      if (added.isNotEmpty()) {
        appendLine("\tadded tryCatchBlocks: ${added.map { it.type }}")
      }
      if (removed.isNotEmpty()) {
        appendLine("\tremoved tryCatchBlocks: ${removed.map { it.type }}")
      }
      if (modified.isNotEmpty()) {
        modified.forEach { appendLine(diffToString(it).prependIndent("\t")) }
      }
    }

    override fun visitAnnotations(added: List<IrAnnotation>, removed: List<IrAnnotation>, modified: List<AnnotationDiff>) {
      if (added.isNotEmpty()) {
        appendLine("\tadded annotations: ${added.map { it.desc }}")
      }
      if (removed.isNotEmpty()) {
        appendLine("\tremoved annotations: ${removed.map { it.desc }}")
      }
      if (modified.isNotEmpty()) {
        modified.forEach { appendLine(diffToString(it).prependIndent("\t")) }
      }
    }
  })
}.trimEnd()

private fun diffToString(diff: FieldDiff) = buildString {
  appendLine("modified field: ${diff.name}")
  diff.accept(object : FieldVisitor {
    override fun visitDesc(old: String?, new: String?) {
      appendLine("\tmodified descriptor: $old -> $new")
    }

    override fun visitAccess(added: Set<IrAccessFlag>, removed: Set<IrAccessFlag>) {
      if (added.isNotEmpty()) {
        appendLine("\tadded access flags: $added")
      }
      if (removed.isNotEmpty()) {
        appendLine("\tremoved access flags: $removed")
      }
    }

    override fun visitSignature(old: String?, new: String?) {
      appendLine("\tmodified signature: $old -> $new")
    }

    override fun visitValue(old: Any?, new: Any?) {
      appendLine("\tmodified value: $old -> $new")
    }

    override fun visitAnnotations(added: List<IrAnnotation>, removed: List<IrAnnotation>, modified: List<AnnotationDiff>) {
      if (added.isNotEmpty()) {
        appendLine("\tadded annotations: ${added.map { it.desc }}")
      }
      if (removed.isNotEmpty()) {
        appendLine("\tremoved annotations: ${removed.map { it.desc }}")
      }
      if (modified.isNotEmpty()) {
        modified.forEach { appendLine(diffToString(it).prependIndent("\t")) }
      }
    }
  })
}.trimEnd()

private fun diffToString(diff: ParameterDiff) = buildString {
  appendLine("modified parameter ${diff.index}:")
  diff.accept(object : ParameterVisitor {
    override fun visitName(old: String?, new: String?) {
      appendLine("\tmodified name: $old -> $new")
    }

    override fun visitAccess(added: Set<IrAccessFlag>, removed: Set<IrAccessFlag>) {
      if (added.isNotEmpty()) {
        appendLine("\tadded access flags: $added")
      }
      if (removed.isNotEmpty()) {
        appendLine("\tremoved access flags: $removed")
      }
    }

    override fun visitAnnotations(added: List<IrAnnotation>, removed: List<IrAnnotation>, modified: List<AnnotationDiff>) {
      if (added.isNotEmpty()) {
        appendLine("\tadded annotations: ${added.map { it.desc }}")
      }
      if (removed.isNotEmpty()) {
        appendLine("\tremoved annotations: ${removed.map { it.desc }}")
      }
      if (modified.isNotEmpty()) {
        modified.forEach { appendLine(diffToString(it).prependIndent("\t")) }
      }
    }
  })
}.trimEnd()

private fun diffToString(diff: LocalVariableDiff) = buildString {
  appendLine("modified local variable ${diff.index}:")
  diff.accept(object : LocalVariableVisitor {
    override fun visitName(old: String, new: String) {
      appendLine("\tmodified name: $old -> $new")
    }

    override fun visitDesc(old: String, new: String) {
      appendLine("\tmodified descriptor: $old -> $new")
    }

    override fun visitSignature(old: String?, new: String?) {
      appendLine("\tmodified signature: $old -> $new")
    }

    override fun visitStart(old: IrLabels.IrLabel, new: IrLabels.IrLabel) {
      appendLine("\tmodified scope start label: $old -> $new")
    }

    override fun visitEnd(old: IrLabels.IrLabel, new: IrLabels.IrLabel) {
      appendLine("\tmodified scope end label: $old -> $new")
    }
  })
}.trimEnd()

private fun diffToString(diff: TryCatchBlockDiff) = buildString {
  println("modified try/catch:")
  diff.accept(object : TryCatchBlockVisitor {
    override fun visitType(old: String?, new: String?) {
      appendLine("\tmodified type: $old -> $new")
    }
  })
}.trimEnd()

private fun diffToString(diff: AnnotationDiff) = buildString {
  appendLine("modified annotation ${diff.desc}:")
  diff.accept(object : AnnotationVisitor {
    override fun visitValues(old: Map<String, Any?>, new: Map<String, Any?>) {
      appendLine("\tmodified values: $old -> $new")
    }

  })
}.trimEnd()

private fun toString(old: IrInstructionList, new: IrInstructionList) = buildString {
  appendLine("Instructions")
  var insn = old.first
  var otherInsn = new.first
  while (insn != null || otherInsn != null) {
    if (insn != otherInsn) {
      appendLine("\t$insn -> $otherInsn")
    } else {
       appendLine("\t$insn")
    }

    insn = insn?.next
    otherInsn = otherInsn?.next
  }
  appendLine("line range: [${new.lines.firstOrNull()}, ${new.lines.lastOrNull()}]")
}.trimEnd()
