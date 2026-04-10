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
    emulator.createCoroutineScope().launch { prepareEnvironment(project)?.let { emulator.setEnvironment(it) } }
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    if (!emulatorSupported) {
      event.presentation.isEnabledAndVisible = false
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  protected abstract suspend fun prepareEnvironment(project: Project?): Environment?

  protected fun Path.toSystemIndependentString(): String = toSystemIndependentName(this.toString())

  class Empty : EmulatorEnvironmentAction() {
    override suspend fun prepareEnvironment(project: Project?): Environment = Environment.newBuilder().build()
  }

  class IndoorStudyDarkImage : BuiltInImage("indoor-study-dark.jpg")

  class OutdoorCityBrightImage : BuiltInImage("outdoor-city-bright.jpg")

  class OutdoorNatureBrightImage : BuiltInImage("outdoor-nature-bright.jpg")

  open class Custom : EmulatorEnvironmentAction() {
    override suspend fun prepareEnvironment(project: Project?): Environment? {
      return withContext(Dispatchers.EDT) {
        val descriptor =
          FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
            .withFileFilter { it.extension in listOf("png", "jpg", "jpeg", "gif", "webp") }
            .withTitle("Select an Image File")
            .withDescription("Select an image file to be used for environment")
        val virtualFile = chooseFile(descriptor, project, null)
        virtualFile?.let { Environment.newBuilder().putEnvironment("scene.mode", "imagefile:${toSystemIndependentName(it.path)}").build() }
      }
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
    val emulatorSupported =
      ApplicationManager.getApplication().isUnitTestMode ||
        AvdManagerConnection.getDefaultAvdManagerConnection().emulator?.version?.let { it >= Revision(36, 6, 4) } ?: false
  }
}
