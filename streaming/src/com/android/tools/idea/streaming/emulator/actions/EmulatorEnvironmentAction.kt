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
import com.android.tools.idea.avd.EnvironmentImageScanner.is360ImageCandidate
import com.android.tools.idea.avd.EnvironmentsUpdater
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.emulator.EmulatorConfiguration
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooser.chooseFile
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName
import java.nio.file.Files
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
    try {
      emulator.setEnvironment(environment)
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

  protected suspend fun createEnvironmentMessage(file: Path, project: Project? = null): Environment {
    val pathStr = toSystemIndependentName(file.toString())
    val mode =
      when {
        file.fileName.toString().endsWith(".obj", ignoreCase = true) -> "mesh3d:$pathStr"
        StudioFlags.EMBEDDED_EMULATOR_VIDEO_ENVIRONMENT.get() && isVideoFile(file) -> "videofile:$pathStr"
        StudioFlags.EMBEDDED_EMULATOR_360_IMAGE_ENVIRONMENT.get() && is360ImageWithConfirmation(file, project) -> "image360:$pathStr"
        else -> "imagefile:$pathStr"
      }
    return Environment.newBuilder().putEnvironment("scene.mode", mode).build()
  }

  private suspend fun is360ImageWithConfirmation(file: Path, project: Project?): Boolean {
    val isXmp360 = withContext(Dispatchers.IO) { is360Image(file) }
    if (isXmp360) return true

    val isCandidate = withContext(Dispatchers.IO) { is360ImageCandidate(file) }
    if (!isCandidate) return false

    return withContext(Dispatchers.EDT) {
      val response =
        Messages.showYesNoDialog(
          project,
          "The selected image appears to be a 360-degree image. Would you like to use it as a 360-degree environment?",
          "360-Degree Image",
          Messages.getQuestionIcon(),
        )
      response == Messages.YES
    }
  }

  class Darkness : EmulatorEnvironmentAction() {
    override suspend fun prepareEnvironment(project: Project?): Environment =
      Environment.newBuilder().putEnvironment("scene.mode", "color:#000000").build()

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
          val is3dEnabled = StudioFlags.EMBEDDED_EMULATOR_3D_SCENE_ENVIRONMENT.get()
          val isVideoEnabled = StudioFlags.EMBEDDED_EMULATOR_VIDEO_ENVIRONMENT.get()
          val extensions = mutableListOf("png", "jpg", "jpeg")
          if (is3dEnabled) extensions.add("obj")
          if (isVideoEnabled) {
            extensions.add("mp4")
            extensions.add("webm")
          }
          val filterTitle = if (is3dEnabled || isVideoEnabled) "Custom environment files" else "Image files"
          val title = if (is3dEnabled || isVideoEnabled) "Select an Environment File" else "Select an Image File"
          val description =
            when {
              is3dEnabled && isVideoEnabled -> "Select an image, video (.mp4, .webm), or 3D scene (.obj) file to be used for environment"
              isVideoEnabled -> "Select an image or video (.mp4, .webm) file to be used for environment"
              is3dEnabled -> "Select an image or 3D scene (.obj) file to be used for environment"
              else -> "Select an image file to be used for environment"
            }
          val descriptor =
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
              .withExtensionFilter(filterTitle, *extensions.toTypedArray())
              .withTitle(title)
              .withDescription(description)
          chooseFile(descriptor, project, null)
        } ?: return null

      val path = Path.of(virtualFile.path)
      if (path.fileName.toString().endsWith(".obj", ignoreCase = true)) {
        val isValid = withContext(Dispatchers.IO) { isWavefrontObjFile(path) }
        if (!isValid) {
          withContext(Dispatchers.EDT) {
            Messages.showErrorDialog(project, "The selected file is not a valid Wavefront 3D scene file.", "Invalid File Format")
          }
          return null
        }
      }

      filePath = toSystemIndependentName(virtualFile.path)
      return createEnvironmentMessage(path, project)
    }

    override fun onEnvironmentSet(emulator: EmulatorController, environment: Environment) {
      super.onEnvironmentSet(emulator, environment)
      filePath?.let { addRecentFile(it) }
    }

    override fun doesMatchEnvironment(environment: Environment): Boolean {
      val path = environment.getEnvironmentFile()?.toAbsolutePath()?.normalize() ?: return false
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

    override suspend fun prepareEnvironment(project: Project?): Environment = createEnvironmentMessage(filePath, project)

    override fun onEnvironmentSet(emulator: EmulatorController, environment: Environment) {
      super.onEnvironmentSet(emulator, environment)
      addRecentFile(filePath.toString())
    }

    override fun doesMatchEnvironment(environment: Environment): Boolean {
      val mode = environment.environmentMap["scene.mode"] ?: return false
      val pathStr = toSystemIndependentName(filePath.toString())
      return mode == "imagefile:$pathStr" || mode == "image360:$pathStr" || mode == "mesh3d:$pathStr" || mode == "videofile:$pathStr"
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

    override suspend fun prepareEnvironment(project: Project?): Environment = createEnvironmentMessage(environmentPath)

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

internal fun isVideoFile(file: Path): Boolean {
  val name = file.fileName.toString()
  return name.endsWith(".mp4", ignoreCase = true) || name.endsWith(".webm", ignoreCase = true)
}

private fun isWavefrontObjFile(path: Path): Boolean {
  return try {
    val maxBytes = 4096
    val bytes =
      Files.newInputStream(path).use { stream ->
        val buffer = ByteArray(maxBytes)
        val read = stream.read(buffer)
        if (read <= 0) return false
        buffer.copyOf(read)
      }
    if (bytes.contains(0.toByte())) {
      return false
    }
    val text = String(bytes, Charsets.UTF_8)
    val lines = text.lines()
    val linesToCheck = if (bytes.size == maxBytes) lines.dropLast(1) else lines

    var hasVertices = false
    var hasFaces = false
    var hasComments = false
    var hasOtherKeywords = false

    val knownKeywords =
      setOf(
        "v",
        "vt",
        "vn",
        "vp",
        "f",
        "g",
        "o",
        "s",
        "usemtl",
        "mtllib",
        "l",
        "p",
        "deg",
        "bmt",
        "step",
        "cstype",
        "parm",
        "trim",
        "hole",
        "scrv",
        "sp",
        "end",
        "con",
        "bevel",
        "c_tech",
        "d_tech",
        "lod",
        "shadow_obj",
        "trace_obj",
        "ctech",
        "dtech",
      )

    for (line in linesToCheck) {
      val trimmed = line.trim()
      if (trimmed.isEmpty()) continue
      if (trimmed.startsWith("#")) {
        hasComments = true
        continue
      }
      val parts = trimmed.split(Regex("\\s+"), 2)
      val keyword = parts[0]
      if (keyword in knownKeywords) {
        when (keyword) {
          "v" -> hasVertices = true
          "f" -> hasFaces = true
          else -> hasOtherKeywords = true
        }
      } else {
        if (!hasVertices && !hasFaces && !hasComments && !hasOtherKeywords) {
          return false
        }
      }
    }
    hasVertices || hasFaces || hasComments
  } catch (_: Exception) {
    false
  }
}
