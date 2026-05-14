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
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.avdmanager.EnvironmentsUpdater
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.protobuf.Empty
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.SuspendingStreamObserver
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooser.chooseFile
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Changes environment of AI Glasses AVD. */
internal sealed class EmulatorEnvironmentAction : AbstractEmulatorAction(configFilter = { it.deviceType == DeviceType.AI_GLASSES }) {

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
      onEnvironmentSet(environment)
    } catch (_: Exception) {
      // Error is already logged.
    }
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    if (!emulatorSupported) {
      event.presentation.isEnabledAndVisible = false
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  protected abstract suspend fun prepareEnvironment(project: Project?): Environment?

  protected open fun onEnvironmentSet(environment: Environment) {}

  protected fun Path.toSystemIndependentString(): String = toSystemIndependentName(this.toString())

  class None : EmulatorEnvironmentAction() {
    override suspend fun prepareEnvironment(project: Project?): Environment = Environment.newBuilder().build()
  }

  class IndoorStudyDarkImage : BuiltInImage("indoor-study-dark.jpg")

  class OutdoorCityBrightImage : BuiltInImage("outdoor-city-bright.jpg")

  class OutdoorNatureBrightImage : BuiltInImage("outdoor-nature-bright.jpg")

  open class Custom : EmulatorEnvironmentAction() {

    private var filePath: String? = null

    override suspend fun prepareEnvironment(project: Project?): Environment? {
      return withContext(Dispatchers.EDT) {
        val descriptor =
          FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
            .withExtensionFilter("Image files", "png", "jpg", "jpeg")
            .withTitle("Select an Image File")
            .withDescription("Select an image file to be used for environment")
        val virtualFile = chooseFile(descriptor, project, null)
        virtualFile?.let {
          filePath = toSystemIndependentName(it.path)
          Environment.newBuilder().putEnvironment("scene.mode", "imagefile:$filePath").build()
        }
      }
    }

    override fun onEnvironmentSet(environment: Environment) {
      filePath?.let { addRecentFile(it) }
    }
  }

  class RecentCustom(val filePath: Path) : EmulatorEnvironmentAction() {

    init {
      templatePresentation.text = "    ${filePath.fileName}"
      templatePresentation.description = filePath.toString()
    }

    override suspend fun prepareEnvironment(project: Project?): Environment? {
      return Environment.newBuilder().putEnvironment("scene.mode", "imagefile:${toSystemIndependentName(filePath.toString())}").build()
    }

    override fun onEnvironmentSet(environment: Environment) {
      addRecentFile(filePath.toString())
    }
  }

  abstract class BuiltInImage(val environmentFileName: String) : EmulatorEnvironmentAction() {
    override suspend fun prepareEnvironment(project: Project?): Environment {
      val imageFile = EnvironmentsUpdater.getInstance().getUpdatedFile(environmentFileName)
      return Environment.newBuilder().putEnvironment("scene.mode", "imagefile:${imageFile.toSystemIndependentString()}").build()
    }
  }

  companion object {
    // TODO: Remove emulator version check after 2026-09-01.
    val emulatorSupported
      get() =
        ApplicationManager.getApplication().isUnitTestMode ||
          AvdManagerConnection.getDefaultAvdManagerConnection().emulator?.version?.let { it >= Revision(36, 6, 4) } ?: false

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
