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
package com.android.tools.idea.run.deployment.liveedit.analysis

import com.android.SdkConstants
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrMethod
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.psi.KtFile
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame

interface GroupTable {
  /**
   * Map of @Composable methods to their corresponding group
   */
  val methodGroups: Map<IrMethod, ComposeGroup>

  /**
   * Map of restart lambda classes to the method they recompose. Restart lambdas are a cached lambda invoked by Compose to re-run the body
   * of a @Composable method when it is invalidated
   */
  val restartLambdas: Map<IrClass, IrMethod>

  /**
   * Map of @Composable lambda classes to their corresponding group
   */
  val lambdaGroups: Map<IrClass, ComposeGroup>

  /**
   * Map of @Composable lambda classes to the method where they are instantiated
   */
  val lambdaParents: Map<IrClass, IrMethod>

  /**
   * Map of inner classes of @Composable lambdas to their top-level lambda parent; i.e, ComposableSingletons$lambda-2$1$1$2$1 maps to
   * ComposableSingletons$lambda-2
   *
   * This information makes it possible to determine which inner classes can be changed without requiring an activity restart
   */
  val composableInnerClasses: Map<IrClass, IrMethod>

  /**
   * A method is associated with a group key if:
   *  - it is a @Composable method associated with a group key
   *  - it is a lambda class associated with a group key via a ComposableLambda
   *  - it is an inner class of either of the above.
   *
   *  These rules apply recursively; an inner class of an inner class of a @Composable lambda group is treated as being in that group
   */
  fun getComposeGroup(method: IrMethod): ComposeGroup? {
    if (method in methodGroups) {
      return methodGroups[method]
    }

    if (method.clazz in lambdaGroups) {
      return lambdaGroups[method.clazz]
    }

    if (method.clazz in composableInnerClasses) {
      val composableMethod = composableInnerClasses[method.clazz]!!
      return getComposeGroup(composableMethod)
    }

    return null
  }
}

class MutableGroupTable : GroupTable {
  override val methodGroups = mutableMapOf<IrMethod, ComposeGroup>()
  override val restartLambdas = mutableMapOf<IrClass, IrMethod>()
  override val lambdaGroups = mutableMapOf<IrClass, ComposeGroup>()
  override val lambdaParents = mutableMapOf<IrClass, IrMethod>()
  override val composableInnerClasses = mutableMapOf<IrClass, IrMethod>()
}

fun computeGroupTable(classes: List<IrClass>, groups: List<ComposeGroup>): GroupTable {
  val classesByName = classes.associateBy { it.name }
  val groupsByKey = groups.associateBy { it.key }
  val singletons = classes.singleOrNull { isComposableSingleton(it.name) }

  val groupTable = MutableGroupTable()
  val analyzer = ComposeAnalyzer()

  val singletonMethods = singletons?.methods ?: emptyList()

  val singletonInit = singletonMethods.singleOrNull { it.name == SdkConstants.CLASS_CONSTRUCTOR }
  if (singletonInit != null) {
    analyzeMethod(analyzer, singletonInit, classesByName, groupsByKey, groupTable)
    for (method in singletonMethods.filter { it != singletonInit }) {
      analyzeMethod(analyzer, method, classesByName, groupsByKey, groupTable)
    }
  }

  for (method in classes.filter { it != singletons }.flatMap { it.methods }) {
    analyzeMethod(analyzer, method, classesByName, groupsByKey, groupTable)
  }

  val inners = mutableMapOf<IrMethod, MutableList<IrClass>>()
  for (clazz in classes) {
    val outerClass = classesByName[clazz.enclosingMethod?.outerClass] ?: continue
    val outerMethod =
      outerClass.methods.singleOrNull { it.name == clazz.enclosingMethod?.outerMethod && it.desc == clazz.enclosingMethod.outerMethodDesc }
        ?: continue
    inners.computeIfAbsent(outerMethod) { mutableListOf() }.add(clazz)
  }

  // Starting from the lambda classes associated with group keys, walk down the inner class hierarchy and associate each inner class with
  // the lambda at the root of the tree
  for (entry in inners.filter { it.key.clazz in groupTable.lambdaGroups || it.key in groupTable.methodGroups }) {
    val queue = ArrayDeque(entry.value)
    while (queue.isNotEmpty()) {
      val cur = queue.removeFirst()
      groupTable.composableInnerClasses[cur] = entry.key
      cur.methods.mapNotNull { inners[it] }.forEach { queue.addAll(it) }
    }
  }
  return groupTable
}

fun GroupTable.toStringWithLineInfo(sourceFile: KtFile): String {
  val doc = sourceFile.fileDocument
  with(StringBuilder()) {
    appendLine("===Composable Methods===")
    for ((method, group) in methodGroups) {
      val startLine = doc.getLineNumber(group.range.startOffset) + 1
      val endLine = doc.getLineNumber(group.range.endOffset) + 1
      appendLine("\t$method} - group: ${group.key} - lines: [$startLine, $endLine]")
    }
    appendLine("===Restart Lambdas===")
    for ((clazz, method) in restartLambdas) {
      appendLine("\t${clazz.name} - $method")
    }
    appendLine("===Composable Lambdas===")
    for ((clazz, group) in lambdaGroups) {
      val startLine = doc.getLineNumber(group.range.startOffset) + 1
      val endLine = doc.getLineNumber(group.range.endOffset) + 1
      appendLine("\t${clazz.name} - group: ${group.key} - lines: [$startLine, $endLine] - parent: ${lambdaParents[clazz]}")
    }
    appendLine("===Inner Classes===")
    for ((clazz, method) in composableInnerClasses) {
      appendLine("\t${clazz.name} - $method")
    }
    return toString()
  }
}

private data class IntValue(val value: Int) : BasicValue(Type.INT_TYPE)
private data class ComposableLambdaValue(val key: Int, val block: Type) : BasicValue(COMPOSABLE_LAMBDA_TYPE)

private val COMPOSABLE_LAMBDA_TYPE = Type.getObjectType("androidx/compose/runtime/internal/ComposableLambda")
private val GROUP_START_METHOD_NAMES = setOf("startRestartGroup", "startReplaceableGroup", "startReplaceGroup", "startMovableGroup",
                                             "startReusableGroup")

private fun analyzeMethod(
  analyzer: ComposeAnalyzer,
  method: IrMethod,
  classesByName: Map<String, IrClass>,
  groupsByKey: Map<Int, ComposeGroup>,
  groupTable: MutableGroupTable
) {
  val frames = analyzer.analyze(method.clazz.name, method.node)
  for (i in 0 until method.node.instructions.size()) {
    val instr = method.node.instructions[i]
    val frame = frames[i]
    when (instr.opcode) {
      INVOKEINTERFACE -> {
        val methodInstr = instr as MethodInsnNode
        if (methodInstr.owner == "androidx/compose/runtime/ScopeUpdateScope" && methodInstr.name == "updateScope") {
          val type = frame.getStackValue(0)?.type
          val clazz = classesByName[type?.internalName] ?: throw RuntimeException("Unexpected restart lambda type: $type")
          groupTable.restartLambdas[clazz] = method
        }

        if (methodInstr.owner == "androidx/compose/runtime/Composer" && methodInstr.name in GROUP_START_METHOD_NAMES) {
          val key = (frame.getStackValue(0) as IntValue).value

          // Ignore groups that were not specified in the FunctionKeyMeta information that we parsed; we use that as the source of truth for
          // which group keys we need to care about.
          val group = groupsByKey[key] ?: continue
          groupTable.methodGroups[method] = group
        }
      }

      INVOKESTATIC -> {
        val methodInstr = instr as MethodInsnNode
        if (methodInstr.owner == "androidx/compose/runtime/internal/ComposableLambdaKt" && Type.getReturnType(methodInstr.desc) == COMPOSABLE_LAMBDA_TYPE) {
          val key = (frame.getStackValue(2) as IntValue).value
          val block = frame.getStackValue(0)?.type
          val clazz = classesByName[block?.internalName] ?: throw RuntimeException("Unknown class type in ComposableLambda: $block")
          val group = groupsByKey[key] ?: throw RuntimeException("Unknown group key in ComposableLambda: $key")
          groupTable.lambdaGroups[clazz] = group
          groupTable.lambdaParents[clazz] = method
        }
      }

      INVOKEVIRTUAL -> {
        val methodInstr = instr as MethodInsnNode
        if (isComposableSingleton(methodInstr.owner)) { // Look at the  next stack frame to see what was returned from this method invocation
          val lambda = frames[i + 1].getStackValue(0) as ComposableLambdaValue
          val clazz = classesByName[lambda.block.internalName] ?: throw RuntimeException("Unknown singleton lambda type: ${lambda.block}")
          groupTable.lambdaParents[clazz] = method
        }
      }
    }
  }
}

private fun Frame<BasicValue?>.getStackValue(idx: Int): BasicValue? {
  // getStack() treats index 0 as the bottom of the stack; we'd prefer to treat 0 as the most recently pushed entry
  return getStack(stackSize - 1 - idx)
}

private class ComposeAnalyzer private constructor(private val interpreter: ComposeInterpreter) : Analyzer<BasicValue?>(interpreter) {
  constructor() : this(ComposeInterpreter())

  override fun analyze(owner: String?, method: MethodNode?): Array<Frame<BasicValue?>> {
    interpreter.setCurrentMethod(owner, method)
    return super.analyze(owner, method)
  }
}

private class ComposeInterpreter : BasicInterpreter(ASM9) {
  private var owner: String? = null
  private var method: MethodNode? = null
  private val singletonFields = mutableMapOf<String, ComposableLambdaValue>()
  private val singletonGetters = mutableMapOf<String, ComposableLambdaValue>()

  fun setCurrentMethod(owner: String?, method: MethodNode?) {
    this.owner = owner
    this.method = method
  }

  override fun newValue(type: Type?): BasicValue? {
    val value = super.newValue(type)
    return if (value == BasicValue.REFERENCE_VALUE) {
      BasicValue(type)
    } else {
      value
    }
  }

  override fun newOperation(instr: AbstractInsnNode): BasicValue? {
    when (instr.opcode) {
      GETSTATIC -> {
        if (isComposableSingleton((instr as FieldInsnNode).owner) && instr.name in singletonFields) {
          return singletonFields[instr.name]
        }
      }

      LDC -> {
        val value = (instr as LdcInsnNode).cst
        if (value is Int) {
          return IntValue(value)
        }
      }
    }
    return super.newOperation(instr)
  }

  override fun unaryOperation(instr: AbstractInsnNode, value: BasicValue?): BasicValue? {
    val newValue = super.unaryOperation(instr, value)
    when (instr.opcode) {
      PUTSTATIC -> {
        if (!isComposableSingleton((instr as FieldInsnNode).owner) || value !is ComposableLambdaValue) {
          return newValue
        }

        if (instr.name in singletonFields) {
          throw RuntimeException("Repeated assignment of singleton field ${instr.name}")
        }

        singletonFields[instr.name] = value
      }

      CHECKCAST -> return value // Ignore type casting, so we can keep track of function interface implementations
    }
    return newValue
  }

  override fun naryOperation(instr: AbstractInsnNode, values: List<BasicValue?>): BasicValue? {
    when (instr.opcode) {
      INVOKESTATIC -> {
        if ((instr as MethodInsnNode).owner == "androidx/compose/runtime/internal/ComposableLambdaKt" &&
            Type.getReturnType(instr.desc) == COMPOSABLE_LAMBDA_TYPE) {
          val key = (values[values.size - 3] as IntValue).value
          val block = values[values.size - 1]?.type!!
          return ComposableLambdaValue(key, block)
        }
      }

      INVOKEVIRTUAL -> {
        if (isComposableSingleton((instr as MethodInsnNode).owner) && instr.name in singletonGetters) {
          return singletonGetters[instr.name]
        }
      }
    }
    return super.naryOperation(instr, values)
  }

  override fun returnOperation(instr: AbstractInsnNode?, value: BasicValue?, expected: BasicValue?) {
    if (!isComposableSingleton(owner!!) || value !is ComposableLambdaValue) {
      return
    }

    if (method!!.name in singletonGetters) {
      throw RuntimeException("Multiple return branches in singleton getter: $method")
    }

    singletonGetters[method!!.name] = value
  }
}

private fun isComposableSingleton(typeInternalName: String): Boolean {
  val classInternalName = typeInternalName.split('/').last()
  return classInternalName.split(
    '$'
  ).let { // Ensure we don't accidentally treat com/my/package/ComposableSingletons$MyActivity$lambda-1 as a singleton parent class
    it.size == 2 && it.first() == "ComposableSingletons"
  }
}