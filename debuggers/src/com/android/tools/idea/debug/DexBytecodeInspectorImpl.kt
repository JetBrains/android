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
package com.android.tools.idea.debug

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DebuggerEvent
import com.google.wireless.android.sdk.stats.DebuggerEvent.SmartStepTargetFilteringPerformed.DexSearchStatus
import com.intellij.debugger.PositionManager
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.openapi.application.readAction
import com.intellij.psi.util.parentOfType
import com.sun.jdi.Location
import com.sun.jdi.Method
import kexter.Dex
import kexter.DexBytecode
import kexter.DexMethod
import kexter.DexMethodDebugInfo
import kexter.InvokeInstruction
import kexter.LineTableEntry
import kexter.Opcode
import org.jetbrains.kotlin.idea.debugger.base.util.safeLocation
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.core.DexBytecodeInspector
import org.jetbrains.kotlin.idea.debugger.core.getInlineFunctionAndArgumentVariablesToBordersMap
import org.jetbrains.kotlin.idea.debugger.core.isInlineFunctionMarkerVariableName
import org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto.KotlinMethodSmartStepTarget
import org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto.KotlinSmartStepTargetFilterer
import org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto.SmartStepIntoContext
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.util.LinkedList

class DexBytecodeInspectorImpl : DexBytecodeInspector {
  /**
   * Checks if the method is a simple delegate to a static method by performing a bytecode
   * instruction test.
   *
   * A simple delegate to a static method can be defined by these sets of opcodes:
   * ```
   * invoke-static
   * return
   * ```
   *
   * or
   *
   * ```
   * invoke-static
   * move-result
   * return
   * ```
   *
   * # Context
   * In Kotlin when providing a default implementation of a method in an interface:
   * ```
   * interface I {
   *     fun foo() {
   *     }
   * }
   * ```
   *
   * Two classes will be generated:
   * - `I.class`
   * - `I$DefaultImpls.class`
   *
   * The `I$DefaultImpls.class` will contain a static `foo` method with the default implementation.
   *
   * When implementing the `I` interface:
   * ```
   * class X : I
   * ```
   *
   * The `foo` method in `X` will simply call the `foo` method of `I$DefaultImpls`:
   * ```java
   *  public void foo();
   *    0: aload_0
   *    1: invokestatic  #18 // Method I$DefaultImpls.foo:(LI;)V
   *    4: return
   * ```
   */
  override fun hasOnlyInvokeStatic(method: Method): Boolean {
    val instructions = DexBytecode.fromBytes(method.bytecodes()).instructions
    if (instructions.isEmpty() || instructions.size > 3) {
      return false
    }

    return instructions.first().opcode.isInvokeStatic() &&
      instructions.last().opcode.isReturn() &&
      (instructions.size == 2 || instructions[1].opcode.isMoveResult())
  }

  override suspend fun filterAlreadyExecutedTargets(
    targets: List<KotlinMethodSmartStepTarget>,
    context: SmartStepIntoContext,
  ): List<KotlinMethodSmartStepTarget> {
    val (expression, debugProcess, _, _) = context
    val location =
      debugProcess.suspendManager.pausedContext?.frameProxy?.safeLocation() ?: return targets
    val method = location.safeMethod() ?: return targets
    val start = System.currentTimeMillis()
    val (dex, status) = DexFinder.findDex(debugProcess, expression, location)
    val time = System.currentTimeMillis() - start

    // TODO: b/430271228
    // Statistics are temporarily disabled until the corresponding proto is updated in G3.
    //logSmartStepTargetFilteringEvent(status, time)

    if (dex == null) {
      return targets
    }

    val filterer = KotlinSmartStepTargetFilterer(targets, debugProcess)
    filterer.visitMethodUntilLocation(debugProcess, method, location, dex)
    return filterer.getUnvisitedTargets()
  }
}

private fun logSmartStepTargetFilteringEvent(status: DexSearchStatus, timeMs: Long) {
  val event =
    AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.DEBUGGER_EVENT)
      .setDebuggerEvent(
        DebuggerEvent.newBuilder()
          .setType(DebuggerEvent.Type.SMART_STEP_TARGET_FILTERING)
          .setSmartStepTargetFilteringPerformed(
            DebuggerEvent.SmartStepTargetFilteringPerformed.newBuilder()
              .setDexFetchTimeMs(timeMs)
              .setStatus(status)
          )
      )
  UsageTracker.log(event)
}

private suspend fun KotlinSmartStepTargetFilterer.visitMethodUntilLocation(
  debugProcess: DebugProcessImpl,
  method: Method,
  location: Location,
  dex: Dex,
) {
  val debugInfo = method.getDebugInfo()
  if (debugInfo.lineTable.isEmpty()) {
    return
  }

  val methodBytecode = DexBytecode.fromBytes(method.bytecodes(), debugInfo)
  if (methodBytecode.instructions.isEmpty()) {
    return
  }

  val lineTableIterator = debugInfo.lineTable.iterator()
  var nextLine = lineTableIterator.next()
  var currentLineNumber: Int? = null
  var lineEverMatched = false
  var inInline = false
  val sortedInlineCalls = LinkedList(extractInlineCalls(location))
  for (insn in methodBytecode.instructions) {
    if (insn.index >= location.codeIndex().toUInt()) {
      break
    }

    if (insn.index >= nextLine.index) {
      currentLineNumber = nextLine.lineNumber
      if (lineTableIterator.hasNext()) {
        nextLine = lineTableIterator.next()
      }
    }

    if (currentLineNumber == location.lineNumber()) {
      lineEverMatched = true
      if (insn is InvokeInstruction) {
        val methodInfo = dex.retrieveMethod(insn.methodIndex())
        if (methodInfo != null) {
          visitOrdinaryFunction(
            methodInfo.owner,
            methodInfo.name,
            methodInfo.signature,
            insn.opcode.name.startsWith("INVOKE_STATIC"),
          )
        }
      }
    }

    if (sortedInlineCalls.isNotEmpty() && lineEverMatched) {
      while (sortedInlineCalls.first.bciRange.last.toUInt() < insn.index) {
        sortedInlineCalls.pop()
      }
      val inlineCall = sortedInlineCalls.firstOrNull { insn.index.toLong() in it.bciRange }
      if (inlineCall == null) {
        inInline = false
        continue
      } else if (inInline) {
        continue
      }
      inInline = true
      if (inlineCall.isInlineFun) {
        val call =
          getCalledInlineFunction(debugProcess.positionManager, inlineCall.startLocation)
            ?: continue
        visitInlineFunction(call)
      } else {
        visitInlineInvokeCall()
      }
    }
  }
}

private val DexMethod.owner: String
  // Drop first 'L' and last ';'
  get() = type.drop(1).dropLast(1)

private val DexMethod.signature: String
  get() = params.joinToString(separator = "", prefix = "(", postfix = ")") + returnType

private fun Opcode.isInvokeStatic(): Boolean {
  return this == Opcode.INVOKE_STATIC || this == Opcode.INVOKE_STATIC_RANGE
}

private fun Opcode.isMoveResult(): Boolean {
  return this == Opcode.MOVE_RESULT ||
    this == Opcode.MOVE_RESULT_WIDE ||
    this == Opcode.MOVE_RESULT_OBJECT
}

private fun Opcode.isReturn(): Boolean {
  return this == Opcode.RETURN_VOID ||
    this == Opcode.RETURN_OBJECT ||
    this == Opcode.RETURN_WIDE ||
    this == Opcode.RETURN
}

private fun Method.getDebugInfo(): DexMethodDebugInfo {
  val lineTable =
    allLineLocations().map { LineTableEntry(it.codeIndex().toUInt(), it.lineNumber()) }
  return DexMethodDebugInfo(lineTable)
}

// Copied from
// src/org/jetbrains/kotlin/idea/debugger/stepping/smartStepInto/KotlinSmartStepTargetFiltererAdapter.kt
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
internal data class InlineCallInfo(
  val isInlineFun: Boolean,
  val bciRange: LongRange,
  val startLocation: Location,
)

private fun extractInlineCalls(location: Location): List<InlineCallInfo> =
  location
    .safeMethod()
    ?.getInlineFunctionAndArgumentVariablesToBordersMap()
    ?.toList()
    .orEmpty()
    .map { (variable, locationRange) ->
      InlineCallInfo(
        isInlineFun = variable.name().isInlineFunctionMarkerVariableName,
        bciRange = locationRange.start.codeIndex()..locationRange.endInclusive.codeIndex(),
        startLocation = locationRange.start,
      )
    }
    // Filter already visible variables to support smart-step-into while inside an inline function
    .filterNot { location.codeIndex() in it.bciRange }
    .sortedBy { it.bciRange.first }

private suspend fun getCalledInlineFunction(
  positionManager: PositionManager,
  location: Location,
): KtNamedFunction? {
  val sourcePosition = positionManager.getSourcePosition(location) ?: return null
  return readAction { sourcePosition.elementAt?.parentOfType<KtNamedFunction>() }
}
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
