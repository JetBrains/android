/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator.actions

import com.android.emulator.control.Camera
import com.android.emulator.control.CameraList
import com.android.tools.idea.avd.EnvironmentImage
import com.android.tools.idea.avd.EnvironmentsUpdater
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.emulator.EmptyStreamObserver
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.util.computeUserDataIfAbsent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Key
import java.nio.file.Files
import java.nio.file.Path
import java.text.Collator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking

/** Displays a popup menu of available environments for AI Glasses. */
internal class EmulatorEnvironmentActionGroup : DefaultActionGroup(), DumbAware {

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    presentation.isVisible = EmulatorEnvironmentAction.isApplicable(event)
    presentation.isEnabled = presentation.isVisible && isEmulatorConnected(event)
  }

  override fun getChildren(event: AnActionEvent?): Array<AnAction> {
    val actions = mutableListOf<AnAction>()
    for (child in super.getChildren(event)) {
      if (child is EmulatorBuiltInEnvironmentsActionGroup) {
        actions.addAll(child.getChildren(event))
      } else {
        actions.add(child)
      }
    }
    return actions.toTypedArray()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/** Dynamically populates built-in environments using contents of the "environments" directory of Android SDK. */
internal class EmulatorBuiltInEnvironmentsActionGroup : DefaultActionGroup(), DumbAware {

  init {
    EnvironmentsUpdater.getInstance() // Initialize EnvironmentsUpdater upfront.
  }

  override fun getChildren(event: AnActionEvent?): Array<AnAction> {
    val updater = EnvironmentsUpdater.getInstance()
    val environments = runBlocking { updater.getEnvironments() }
    val sortedEnvironments =
      environments.sortedWith(compareByDescending<EnvironmentImage> { it.isDefault }.thenBy(Collator.getInstance()) { it.title })
    return sortedEnvironments.map { EmulatorEnvironmentAction.BuiltInImage(it.path, it.title) }.toTypedArray()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/** Displays a popup menu of recent custom environments. */
internal class EmulatorRecentEnvironmentsActionGroup : DefaultActionGroup(), DumbAware {

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val hasRecentFiles = EmulatorEnvironmentAction.getRecentFiles().map { Path.of(it) }.any { Files.isRegularFile(it) }
    presentation.isVisible = hasRecentFiles && EmulatorEnvironmentAction.isApplicable(event)
    presentation.isEnabled = presentation.isVisible && isEmulatorConnected(event)
  }

  override fun getChildren(event: AnActionEvent?): Array<AnAction> {
    val recentFiles = EmulatorEnvironmentAction.getRecentFiles().map { Path.of(it) }.filter { Files.isRegularFile(it) }
    return recentFiles.map { EmulatorEnvironmentAction.RecentCustom(it) }.toTypedArray()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/** Displays a popup menu of available host cameras. */
internal class EmulatorCameraActionGroup : DefaultActionGroup(), DumbAware {

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val emulator = getEmulatorController(event)
    presentation.isVisible =
      StudioFlags.EMBEDDED_EMULATOR_CAMERA_ENVIRONMENT.get() &&
        emulator != null &&
        EmulatorEnvironmentAction.isApplicable(emulator.emulatorConfig) &&
        emulator.hostCameras.isNotEmpty()
    presentation.isEnabled = presentation.isVisible && emulator?.connectionState == EmulatorController.ConnectionState.CONNECTED
  }

  override fun getChildren(event: AnActionEvent?): Array<AnAction> {
    event ?: return emptyArray()
    val emulator = getEmulatorController(event) ?: return emptyArray()
    return emulator.hostCameras.map { EmulatorEnvironmentAction.Camera(it.displayName, it.id) }.toTypedArray()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private val EmulatorController.hostCameras: List<Camera>
  get() {
    if (connectionState != EmulatorController.ConnectionState.CONNECTED) return emptyList()
    return computeUserDataIfAbsent(CameraListFetcher.KEY) { CameraListFetcher(this) }.hostCameras
  }

private class CameraListFetcher(private val emulator: EmulatorController) {

  val hostCameras: List<Camera>
    get() {
      val latch = CountDownLatch(1)
      val observer =
        object : EmptyStreamObserver<CameraList>() {
          override fun onNext(message: CameraList) {
            _hostCameras = message.camerasList
            latch.countDown()
          }

          override fun onError(t: Throwable) {
            latch.countDown()
          }
        }
      // The getHostCameras call usually takes only few milliseconds.
      val timeout = if (_hostCameras == null) 200.milliseconds else 20.milliseconds
      emulator.getHostCameras(observer)
      try {
        latch.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
      } catch (_: InterruptedException) {}
      return _hostCameras ?: emptyList()
    }

  @Volatile private var _hostCameras: List<Camera>? = null

  companion object {
    val KEY = Key<CameraListFetcher>(CameraListFetcher::class.java.name)
  }
}
