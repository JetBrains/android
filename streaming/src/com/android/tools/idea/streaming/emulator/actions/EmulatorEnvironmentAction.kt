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

import com.android.emulator.control.Environment
import com.android.repository.Revision
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.avd.EnvironmentImageScanner.is360Image
import com.android.tools.idea.avd.EnvironmentsUpdater
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.protobuf.Empty
import com.android.tools.idea.streaming.emulator.EmulatorConfiguration
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.SuspendingStreamObserver
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooser.chooseFile
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Changes environment of AI Glasses AVD. */
internal sealed class EmulatorEnvironmentAction :
  AbstractEmulatorAction(configFilter = { it.deviceType == DeviceType.AI_GLASSES }), Toggleable {

  override fun actionPerformed(event: AnActionEvent) {
    val emulator = getEmulatorController(event) ?: return
    val project = event.project
    emulator.createCoroutineScope().launch { prepareEnvironment(project)?.let { setEnvironment(emulator, it) } }
  }

  private suspend fun setEnvironment(emulator: EmulatorController, environment: Environment) {
    val observer = SuspendingStreamObserver<Empty>()
    try {
      emulator.setEnvironment(environment, observer)
      observer.getResult()
      onEnvironmentSet(emulator, environment)
    } catch (_: Exception) {
      // Error is already logged.
    }
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    if (!emulatorSupported) {
      event.presentation.isEnabledAndVisible = false
      return
    }
    val emulator = getEmulatorController(event) ?: return
    val environment = EnvironmentTracker.forEmulator(emulator)?.environment
    val selected = environment != null && doesMatchEnvironment(environment)
    Toggleable.setSelected(event.presentation, selected)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  protected abstract suspend fun prepareEnvironment(project: Project?): Environment?

  open fun doesMatchEnvironment(environment: Environment): Boolean = false

  protected open fun onEnvironmentSet(emulator: EmulatorController, environment: Environment) {
    EnvironmentTracker.forEmulator(emulator)?.environment = environment
  }

  protected suspend fun createImageEnvironmentMessage(path: Path): Environment {
    val is360 = StudioFlags.EMBEDDED_EMULATOR_360_IMAGE_ENVIRONMENT.get() && withContext(Dispatchers.IO) { is360Image(path) }
    val pathStr = toSystemIndependentName(path.toString())
    val mode = if (is360) "image360:$pathStr" else "imagefile:$pathStr"
    return Environment.newBuilder().putEnvironment("scene.mode", mode).build()
  }

  class None : EmulatorEnvironmentAction() {
    override suspend fun prepareEnvironment(project: Project?): Environment = Environment.newBuilder().build()

    override fun doesMatchEnvironment(environment: Environment): Boolean {
      val mode = environment.environmentMap["scene.mode"]
      return mode.isNullOrEmpty() || mode == "color:#000000"
    }
  }

  open class Custom : EmulatorEnvironmentAction() {

    private var filePath: String? = null

    override suspend fun prepareEnvironment(project: Project?): Environment? {
      val virtualFile =
        withContext(Dispatchers.EDT) {
          val descriptor =
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
              .withExtensionFilter("Image files", "png", "jpg", "jpeg")
              .withTitle("Select an Image File")
              .withDescription("Select an image file to be used for environment")
          chooseFile(descriptor, project, null)
        } ?: return null

      filePath = toSystemIndependentName(virtualFile.path)
      val path = Path.of(virtualFile.path)
      return createImageEnvironmentMessage(path)
    }

    override fun onEnvironmentSet(emulator: EmulatorController, environment: Environment) {
      super.onEnvironmentSet(emulator, environment)
      filePath?.let { addRecentFile(it) }
    }

    override fun doesMatchEnvironment(environment: Environment): Boolean {
      val path = environment.getImagePath()?.toAbsolutePath()?.normalize() ?: return false
      val builtInEnvironments = runBlocking { EnvironmentsUpdater.getInstance().getEnvironments() }
      val builtInPaths = builtInEnvironments.map { it.path.toAbsolutePath().normalize() }
      return !builtInPaths.contains(path)
    }
  }

  class RecentCustom(val filePath: Path) : EmulatorEnvironmentAction() {

    init {
      templatePresentation.setText(filePath.fileName.toString(), false)
      templatePresentation.description = filePath.toString()
    }

    override suspend fun prepareEnvironment(project: Project?): Environment {
      return createImageEnvironmentMessage(filePath)
    }

    override fun onEnvironmentSet(emulator: EmulatorController, environment: Environment) {
      super.onEnvironmentSet(emulator, environment)
      addRecentFile(filePath.toString())
    }

    override fun doesMatchEnvironment(environment: Environment): Boolean {
      val mode = environment.environmentMap["scene.mode"] ?: return false
      val pathStr = toSystemIndependentName(filePath.toString())
      return mode == "imagefile:$pathStr" || mode == "image360:$pathStr"
    }
  }

  class Camera(val cameraName: String, val cameraId: String) : EmulatorEnvironmentAction() {

    init {
      templatePresentation.text = cameraName
      templatePresentation.description = "Use host camera $cameraName"
    }

    override suspend fun prepareEnvironment(project: Project?): Environment =
      Environment.newBuilder().putEnvironment("scene.mode", "webcam:$cameraId").build()

    override fun doesMatchEnvironment(environment: Environment): Boolean = environment.environmentMap["scene.mode"] == "webcam:$cameraId"
  }

  class BuiltInImage(val environmentPath: Path, title: String) : EmulatorEnvironmentAction() {

    init {
      templatePresentation.text = title
      templatePresentation.description = "Select $title environment"
    }

    override suspend fun prepareEnvironment(project: Project?): Environment = createImageEnvironmentMessage(environmentPath)

    override fun doesMatchEnvironment(environment: Environment): Boolean {
      val mode = environment.environmentMap["scene.mode"] ?: return false
      val pathStr = toSystemIndependentName(environmentPath.toString())
      return mode == "imagefile:$pathStr" || mode == "image360:$pathStr"
    }
  }

  companion object {
    // TODO: Remove emulator version check after 2026-09-01.
    private val emulatorSupported
      get() =
        ApplicationManager.getApplication().isUnitTestMode ||
          AvdManagerConnection.getDefaultAvdManagerConnection().emulator?.version?.let { it >= Revision(36, 6, 4) } ?: false

    fun isApplicable(event: AnActionEvent): Boolean = getEmulatorConfig(event)?.let { isApplicable(it) } ?: false

    fun isApplicable(emulatorConfiguration: EmulatorConfiguration): Boolean =
      emulatorSupported && emulatorConfiguration.deviceType == DeviceType.AI_GLASSES

    private const val RECENT_FILES_KEY = "EmulatorEnvironmentAction.recentFiles"

    fun getRecentFiles(): List<String> {
      val properties = PropertiesComponent.getInstance()
      val value = properties.getValue(RECENT_FILES_KEY) ?: return emptyList()
      return value.split('\n').filter { it.isNotEmpty() }
    }

    fun addRecentFile(path: String) {
      val properties = PropertiesComponent.getInstance()
      val current = getRecentFiles().toMutableList()
      current.remove(path)
      current.add(0, path)
      while (current.size > 5) {
        current.removeLast()
      }
      properties.setValue(RECENT_FILES_KEY, current.joinToString("\n"))
    }
  }
}
