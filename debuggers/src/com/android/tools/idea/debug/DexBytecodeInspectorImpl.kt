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

import com.android.ddmlib.IDevice
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.ApkFileUnit
import com.android.tools.idea.run.ApkProvider
import com.android.zipflinger.ZipRepo
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DebuggerEvent
import com.google.wireless.android.sdk.stats.DebuggerEvent.SmartStepTargetFilteringPerformed.DexSearchStatus
import com.intellij.debugger.PositionManager
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.util.parentOfType
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.sun.jdi.Location
import com.sun.jdi.Method
import java.util.LinkedList
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
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction

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
    val (dex, status) = findDexWithLocationCacheAwareSafe(debugProcess, expression, location)
    val time = System.currentTimeMillis() - start

    logSmartStepTargetFilteringEvent(status, time)

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

private suspend fun findDexWithLocationCacheAwareSafe(
  debugProcess: DebugProcessImpl,
  expression: KtElement,
  location: Location,
): Pair<Dex?, DexSearchStatus> {
  return try {
    findDexWithLocationCacheAware(debugProcess, expression, location)
  } catch (ex: Exception) {
    Pair(null, DexSearchStatus.UNKNOWN)
  }
}

private suspend fun findDexWithLocationCacheAware(
  debugProcess: DebugProcessImpl,
  expression: KtElement,
  location: Location,
): Pair<Dex?, DexSearchStatus> {
  val mapping =
    DebugSessionCache.getInstance(debugProcess.project)
      .getMapping(debugProcess, DebugSessionCache.DEX_CACHE_TOKEN)
  mapping?.get(location.declaringType())?.let {
    return it as Dex to DexSearchStatus.FOUND
  }

  val configuration =
    debugProcess.androidRunConfiguration
      ?: return null to DexSearchStatus.APK_PROVIDER_NOT_AVAILABLE
  val apkProvider =
    configuration.apkProvider ?: return null to DexSearchStatus.APK_PROVIDER_NOT_AVAILABLE
  val module = findModule(expression) ?: return null to DexSearchStatus.MODULE_NOT_FOUND
  val project = debugProcess.project
  val androidDevices =
    configuration.deployTargetContext.currentDeployTargetProvider
      .getDeployTarget(project)
      .getAndroidDevices(project)

  // Waiting for devices to become online can hang the debugger for a while,
  // so fetch only the ones that are already running
  val devices = androidDevices.mapNotNull { it.ddmlibDevice }
  if (devices.isEmpty()) {
    return null to DexSearchStatus.DEVICES_NOT_RUNNING
  }

  val apks = findApksWithModule(devices, apkProvider, module)
  if (apks.isEmpty()) {
    return null to DexSearchStatus.APK_NOT_FOUND
  }

  val dex = apks.firstNotNullOfOrNull { findDexWithLocation(it, location) }
  if (dex != null) {
    mapping?.put(location.declaringType(), dex)
    return dex to DexSearchStatus.FOUND
  }
  return null to DexSearchStatus.DEX_NOT_FOUND
}

private fun findApksWithModule(
  devices: List<IDevice>,
  apkProvider: ApkProvider,
  module: Module,
): List<ApkFileUnit> {
  val result = mutableListOf<ApkFileUnit>()
  for (device in devices) {
    for (apkInfos in apkProvider.getApks(device)) {
      for (file in apkInfos.files) {
        if (module.name.startsWith(file.moduleName)) {
          result.add(file)
        }
      }
    }
  }
  return result
}

private fun findDexWithLocation(file: ApkFileUnit, location: Location): Dex? {
  val signature = location.declaringType().signature()
  ZipRepo(file.apkPath).use { zipRepo ->
    val dexEntries = zipRepo.entries.filter { (name, _) -> name.endsWith(".dex") }
    for (entry in dexEntries) {
      val content = zipRepo.getContent(entry.key).array()
      val dex = Dex.fromBytes(content)
      val containsLocation = dex.classes.any { (name, _) -> name == signature }
      if (containsLocation) {
        return dex
      }
    }
  }
  return null
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
      }
    }
  }
}

private suspend fun findModule(element: KtElement): Module? {
  return readAction {
    val file = element.containingFile.virtualFile
    ProjectFileIndex.getInstance(element.project).getModuleForFile(file)
  }
}

@Suppress("UnstableApiUsage")
private val DebugProcessImpl.androidRunConfiguration: AndroidRunConfiguration?
  get() {
    val xDebugSessionImpl = session.xDebugSession as? XDebugSessionImpl ?: return null
    return xDebugSessionImpl.executionEnvironment?.runProfile as? AndroidRunConfiguration
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
