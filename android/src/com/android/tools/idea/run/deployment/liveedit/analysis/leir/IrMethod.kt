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

import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue

class IrMethod(val clazz: IrClass, val node: MethodNode) {
  val name: String = node.name
  val desc: String = node.desc
  val access = parseAccess(node.access)
  val signature: String? = node.signature

  fun getReadableDesc() : String {
    if (clazz.methods.count { it.name == name } > 1) {
      val type = Type.getMethodType(desc)
      val param = type.argumentTypes.joinToString { it.className }
      return shortClassName(type.returnType.className) + " " + name + "(" + shortClassName(param) + ")"
    } else {
      return name
    }
  }

  // This will only be populated if the kotlin compiler is invoked with the -java-parameters flag.
  val parameters: List<IrParameter> = node.parameters?.mapIndexed { idx, param ->
    val visibleAnnotations = node.visibleParameterAnnotations?.get(idx) ?: emptyList()
    val invisibleAnnotations = node.invisibleParameterAnnotations?.get(idx) ?: emptyList()
    IrParameter(idx, param, visibleAnnotations, invisibleAnnotations)
  }?.sortedBy { it.index } ?: emptyList()

  val annotations = toAnnotationList(node.visibleAnnotations, node.invisibleAnnotations)
  val instructions = IrInstructionList(node.instructions)
  val localVariables = node.localVariables?.map { IrLocalVariable(it, instructions.labels) } ?: emptyList()
  val tryCatchBlocks = node.tryCatchBlocks?.map { IrTryCatchBlock(it, instructions.labels) } ?: emptyList()

  // We currently ignore the following MethodNode members when parsing, because they're either irrelevant to LiveEdit, don't appear in the
  // classfile, or are metadata about other information that we're already parsing.
  // - attrs
  // - maxLocals
  // - maxStack
  // - visibleLocalVariableAnnotations
  // - invisibleLocalVariableAnnotations
  // - invisibleTypeAnnotations
  // - visibleTypeAnnotations
  // - visibleAnnotableParameterCount
  // - invisibleAnnotableParameterCount

  override fun toString(): String {
    return "${clazz.name}.$name$desc"
  }
}

private fun shortClassName(name: String) : String {
  return name.substring(name.lastIndexOf('.') + 1)
}