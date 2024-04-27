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

import org.jetbrains.org.objectweb.asm.Handle
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode
import org.jetbrains.org.objectweb.asm.tree.InsnList

class IrInstructionList(insnList: InsnList): Iterable<IrInstruction> {
  private val list = mutableListOf<IrInstruction>()

  val labels = IrLabels()
  val lines: List<Int>

  val size get() = list.size
  val first get() = list.firstOrNull()
  val last get() = list.lastOrNull()

  init {
    val parser = InstructionParser(labels)
    val lineInstr = mutableListOf<IrInstruction>()
    var node = insnList.first
    while (node != null) {
      val instr = parser.parse(node)

      // Ignore lines in the main instruction list, so they're ignored when diffing.
      if (instr.type == IrInstruction.Type.LINE) {
        lineInstr.add(instr)
      } else {
        list.add(instr)
      }

      node = node.next
    }

    lines = lineInstr.sortedBy { (it.params[1] as IrLabels.IrLabel).index }.map { it.params[0] as Int }
  }

  operator fun get(index: Int): IrInstruction {
    return list[index]
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }

    if (javaClass != other?.javaClass) {
      return false
    }

    other as IrInstructionList
    return list == other.list
  }

  override fun hashCode(): Int {
    return list.hashCode()
  }

  override fun iterator(): Iterator<IrInstruction> {
    return list.iterator()
  }
}

class IrLabels {
  private var nextIndex = 0

  private val labels = mutableMapOf<Label?, IrLabel>()
  private val indexMap = mutableMapOf<Label?, Int>()

  /**
   * The number of labels encountered in the instruction list.
   */
  val size: Int
    get() {
      return nextIndex
    }

  inner class IrLabel(private val label: Label?) {
    /**
     * The index of the label based on its position in the method bytecode. The first label is label 0, the second is 1, etc.
     */
    val index get() = indexMap[label] ?: -1

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as IrLabel
      return index == other.index
    }

    override fun hashCode(): Int {
      return index
    }

    override fun toString() = "LABEL $index"
  }

  /**
   * Associates the [IrLabel] with an index based on the order it was encountered in the method. The [IrLabel] associated with this
   * [IrLabel] will take on that index.
   */
  fun handleLabelInsn(label: Label?) {
    indexMap[label] = nextIndex++
  }

  /**
   * Returns the [IrLabel] associated with this [IrLabel], creating one if it doesn't exist.
   */
  fun get(label: Label?): IrLabel {
    return labels.computeIfAbsent(label) { IrLabel(label) }
  }
}

private class InstructionParser(private val labels: IrLabels) : MethodVisitor(Opcodes.ASM5) {
  private var parsed: IrInstruction? = null

  fun parse(insnNode: AbstractInsnNode): IrInstruction {
    insnNode.accept(this)
    return parsed ?: throw IllegalStateException("Failed to parse instruction")
  }

  private fun handle(opcode: Int, params: List<Any?>) {
    parsed = IrInstruction(IrInstruction.Type.INSTRUCTION, opcode, params, parsed)
  }

  private fun handleLabel(label: IrLabels.IrLabel) {
    parsed = IrInstruction(IrInstruction.Type.LABEL, -1, listOf(label), parsed)
  }

  private fun handleLine(line: Int, label: IrLabels.IrLabel) {
    parsed = IrInstruction(IrInstruction.Type.LINE, -1, listOf(line, label), parsed)
  }

  override fun visitInsn(opcode: Int) {
    handle(opcode, emptyList())
  }

  override fun visitIntInsn(opcode: Int, operand: Int) {
    handle(opcode, listOf(operand))
  }

  override fun visitVarInsn(opcode: Int, v: Int) {
    handle(opcode, listOf(v))
  }

  override fun visitTypeInsn(opcode: Int, type: String?) {
    handle(opcode, listOf(type))
  }

  override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) {
    handle(opcode, listOf(owner, name, descriptor))
  }

  @Deprecated("Deprecated in Java")
  override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) {
    handle(opcode, listOf(owner, name, descriptor))
  }

  override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, descriptor: String?, isInterface: Boolean) {
    handle(opcode, listOf(owner, name, descriptor, isInterface))
  }

  override fun visitInvokeDynamicInsn(name: String?,
                                      descriptor: String?,
                                      bootstrapMethodHandle: Handle?,
                                      vararg bootstrapMethodArguments: Any?) {
    // TODO: decompose Handle? into constituent parts
    handle(Opcodes.INVOKEDYNAMIC, listOf(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments))
  }

  override fun visitJumpInsn(opcode: Int, label: Label?) {
    val idx = labels.get(label)
    handle(opcode, listOf(idx))
  }

  override fun visitLabel(label: Label?) {
    labels.handleLabelInsn(label)
    handleLabel(labels.get(label))
  }

  override fun visitLdcInsn(value: Any?) {
    // From ASM docs: The values parameter must be a nonnull Integer, a Float, a Long, a Double, a String, a Type, a Handle, or a
    // ConstantDynamic. All of these override equals(), and are safe to directly compare.
    handle(Opcodes.LDC, listOf(value))
  }

  override fun visitIincInsn(v: Int, increment: Int) {
    handle(Opcodes.IINC, listOf(v, increment))
  }

  override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label?, vararg labels: Label?) {
    handle(Opcodes.TABLESWITCH, listOf(min, max, this.labels.get(dflt), labels.map { this.labels.get(it) }.toList()))
  }

  override fun visitLookupSwitchInsn(dflt: Label?, keys: IntArray?, labels: Array<out Label>?) {
    handle(Opcodes.LOOKUPSWITCH, listOf(this.labels.get(dflt), keys, labels?.map { this.labels.get(it) }?.toList()))
  }

  override fun visitMultiANewArrayInsn(descriptor: String?, numDimensions: Int) {
    handle(Opcodes.MULTIANEWARRAY, listOf(descriptor, numDimensions))
  }

  override fun visitLineNumber(line: Int, start: Label?) {
    handleLine(line, labels.get(start))
  }
}

