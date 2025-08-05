/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.tools.deploy.proto.Deploy.FindDexResponse
import com.android.tools.deployer.AdbClient
import com.android.tools.deployer.AdbInstaller
import com.android.tools.deployer.Installer
import com.android.tools.deployer.MetricsRecorder
import com.android.tools.idea.debug.DexFinder.Result
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.ApkFileUnit
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.util.LocalInstallerPathManager
import com.android.zipflinger.ZipRepo
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DebuggerEvent
import com.google.wireless.android.sdk.stats.DebuggerEvent.SmartStepTargetFilteringPerformed.DexSearchStatus
import com.google.wireless.android.sdk.stats.DebuggerEvent.SmartStepTargetFilteringPerformed.DexSearchMode
import kotlin.system.measureTimeMillis
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import kexter.Dex
import org.jetbrains.kotlin.psi.KtElement

object DexFinder {
  val LOGGER = LogWrapper(Logger.getInstance(DexFinder::class.java))

  data class Result(val dex: Dex?, val status: DexSearchStatus, val mode: DexSearchMode)

  private class DexCache {
    companion object {
      val DEX_CACHE_KEY = Key.create<DexCache?>("DEX_CACHE_KEY")

      fun instance(debugProcess: DebugProcessImpl): DexCache {
        val vmProxy = debugProcess.suspendManager.pausedContext.virtualMachineProxy
        return vmProxy.getOrCreateUserData<DexCache>(DEX_CACHE_KEY) { DexCache() }
      }
    }

    // Caching [Result] values allows us to perform a search only once
    // per class during a debug session.
    val typeToSearchResult = mutableMapOf<ReferenceType, Result>()
  }

  /**
   * Finds a [Dex] file that contains a [location] and logs statistics.
   *
   * @param debugProcess A debug process.
   * @param expression A kotlin element that is used to find a source code module.
   * @param location A location of a class that should be found in a [Dex] file.
   * @return A [Result] that contains a [Dex] file and a status of a search.
   */
  suspend  fun findDex(debugProcess: DebugProcessImpl, expression: KtElement, location: Location): Dex? {
    val type = location.declaringType()
    val cache = DexCache.instance(debugProcess)
    cache.typeToSearchResult[type]?.let { return it.dex }

    val result: Result
    val time = measureTimeMillis {
      result = findDexImpl(debugProcess, expression, location)
    }

    logSmartStepTargetFilteringEvent(result, time)

    cache.typeToSearchResult[type] = result
    return result.dex
  }

}

private fun logSmartStepTargetFilteringEvent(result: Result, timeMs: Long) {
  val event =
    AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.DEBUGGER_EVENT)
      .setDebuggerEvent(
        DebuggerEvent.newBuilder()
          .setType(DebuggerEvent.Type.SMART_STEP_TARGET_FILTERING)
          .setSmartStepTargetFilteringPerformed(
            DebuggerEvent.SmartStepTargetFilteringPerformed.newBuilder()
              .setDexFetchTimeMs(timeMs)
              .setMode(result.mode)
              .setStatus(result.status)
          )
      )
  UsageTracker.log(event)
}

private suspend fun findDexImpl(debugProcess: DebugProcessImpl, expression: KtElement, location: Location): Result {
  var mode = DexSearchMode.APK_PROVIDER
  try {
    val apkProvider = debugProcess.androidRunConfiguration?.apkProvider
    if (apkProvider != null) {
      findDexViaApkProvider(apkProvider, debugProcess, expression, location)?.let {
        return Result(it, DexSearchStatus.FOUND, mode)
      }
      mode = DexSearchMode.DEVICE_FALLBACK
    } else {
      mode = DexSearchMode.DEVICE
    }

    val (dex, status) = findDexViaInstaller(debugProcess, location)
    when (status) {
      FindDexResponse.Status.OK -> {
        if (dex != null) {
          return Result(dex, DexSearchStatus.FOUND, mode)
        }
        return Result(null, DexSearchStatus.ERROR, mode)
      }
      FindDexResponse.Status.FILE_TOO_LARGE ->
        return Result(null, DexSearchStatus.DEX_FILE_TOO_LARGE, mode)
      else ->
        return Result(null, DexSearchStatus.DEX_NOT_FOUND, mode)
    }
  } catch (_: Exception) {
    return Result(null, DexSearchStatus.ERROR, mode)
  }
}

// Retrieve the dex files from the apk(s) generated by the build system
private suspend fun findDexViaApkProvider(
  apkProvider: ApkProvider,
  debugProcess: DebugProcessImpl,
  expression: KtElement,
  location: Location
): Dex? {
  val device = debugProcess.connectedDevice ?: return null
  val apks = findApksWithExpression(device, apkProvider, expression)
  return apks.firstNotNullOfOrNull {
    findDexWithLocation(it, location)
  }
}

// Retrieve the dex files from the .apk(s) on the device.
private fun findDexViaInstaller(debugProcess: DebugProcessImpl, location: Location): Pair<Dex?, FindDexResponse.Status> {
  val device = debugProcess.connectedDevice ?: return null to FindDexResponse.Status.NOT_FOUND
  val installer = newInstaller(device)
  val signature = location.declaringType().signature()
  for (packageName in debugProcess.applicationPackageNames) {
    val response = installer.findDex(packageName, signature)
    when (response.status) {
      FindDexResponse.Status.OK -> {
        val dex = Dex.fromBytes(response.dexFile.toByteArray()).getDexFileWithClass(signature)
        return dex to response.status
      }
      FindDexResponse.Status.FILE_TOO_LARGE ->
        return null to response.status
      else -> {}
    }
  }
  return null to FindDexResponse.Status.NOT_FOUND
}

private suspend fun findApksWithExpression(
  device: IDevice,
  apkProvider: ApkProvider,
  expression: KtElement,
): List<ApkFileUnit> {
  val module = findModule(expression) ?: return emptyList()
  val result = mutableListOf<ApkFileUnit>()
  for (apkInfos in apkProvider.getApks(device)) {
    for (file in apkInfos.files) {
      /**
       * TODO: b/424108700
       * This heuristic is intended do find an APK that contains compiled code for a debugged
       * module. However, it doesn't work when an [ApkFileUnit] doesn't have module name assigned,
       * which is sometimes the case. Also this heuristic only works when a breakpoint is set in an
       * application module, when debugging libraries it will not yield anything. At the same time,
       * it is intentionally left here because of the reasons below:
       * 1. While not always being correct this heuristic still covers most of the user cases while
       * debugging (which has been measured).
       * 2. When it fails, the debugger will try to fetch the sought for DEX file via
       * [com.android.tools.deployer.Installer]. If a DEX file is still not found after that,
       * smart step target filtering will simply not be performed, which is not critical for
       * "complex" debugging scenarios.
       * 3. If this heuristic is removed and we try to reach a 100% accuracy when searching for a
       * DEX file with an [ApkProvider], we would have to scan through all of the APKs on a device
       * (unless we find a better approach), which is a waste of resources.
       */
      if (module.name.startsWith(file.moduleName)) {
        result.add(file)
      }
    }
  }
  return result
}

private fun findDexWithLocation(file: ApkFileUnit, location: Location): Dex? {
  val signature = location.declaringType().signature()
  ZipRepo(file.apkPath).use { zipRepo ->
    val dexEntries = zipRepo.entries.keys.filter { it.endsWith(".dex") }
    for (entry in dexEntries) {
      val content = zipRepo.getContent(entry).array()
      val dex = Dex.fromBytes(content).getDexFileWithClass(signature)
      if (dex != null) {
        return dex
      }
    }
  }
  return null
}

private suspend fun findModule(element: KtElement): Module? {
  return readAction {
    val file = element.containingFile.virtualFile
    ProjectFileIndex.getInstance(element.project).getModuleForFile(file)
  }
}

fun newInstaller(device: IDevice): Installer {
  val metrics = MetricsRecorder()
  val adb = AdbClient(device, DexFinder.LOGGER)
  return AdbInstaller(
    LocalInstallerPathManager.getLocalInstaller(),
    adb,
    metrics.deployMetrics,
    DexFinder.LOGGER,
    AdbInstaller.Mode.DAEMON
  )
}

@Suppress("UnstableApiUsage")
private val DebugProcessImpl.androidRunConfiguration: AndroidRunConfiguration?
  get() {
    val xDebugSessionImpl = session.xDebugSession as? XDebugSessionImpl ?: return null
    return xDebugSessionImpl.executionEnvironment?.runProfile as? AndroidRunConfiguration
  }
