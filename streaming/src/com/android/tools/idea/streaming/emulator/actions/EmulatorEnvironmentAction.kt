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
import com.android.tools.idea.streaming.core.htmlEscaped
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
        is3dSceneFile(file) -> "mesh3d:$pathStr"
        StudioFlags.EMBEDDED_EMULATOR_VIDEO_ENVIRONMENT.get() && isVideoFile(file) -> "videofile:$pathStr"
        StudioFlags.EMBEDDED_EMULATOR_360_IMAGE_ENVIRONMENT.get() && is360ImageWithConfirmation(file, project) -> "image360:$pathStr"
        else -> "imagefile:$pathStr"
      }
    return Environment.newBuilder().putEnvironment("scene.mode", mode).build()
  }

  private suspend fun is360ImageWithConfirmation(file: Path, project: Project?): Boolean {
    val isXmp360 = withContext(Dispatchers.IO) { is360Image(file) }
    if (isXmp360) return true

    val recentFile = getRecentFile(file)

    if (recentFile != null && recentFile.mode.isNotEmpty()) {
      val currentTimestamp =
        try {
          withContext(Dispatchers.IO) { Files.getLastModifiedTime(file).toMillis() }
        } catch (_: Exception) {
          -1L
        }
      val currentSize =
        try {
          withContext(Dispatchers.IO) { Files.size(file) }
        } catch (_: Exception) {
          -1L
        }

      if (currentTimestamp != -1L && currentSize != -1L && currentTimestamp == recentFile.timestamp && currentSize == recentFile.size) {
        return recentFile.mode == "image360"
      }
    }

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
      if (is3dSceneFile(path)) {
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
      filePath?.let { addRecentFile(Path.of(it), environment) }
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
      val label = getEnvironmentTypeLabel(filePath)
      templatePresentation.setText("<html>${filePath.fileName.toString().htmlEscaped()} <font color=\"gray\">$label</font></html>", false)
      templatePresentation.description = filePath.toString()
    }

    override suspend fun prepareEnvironment(project: Project?): Environment = createEnvironmentMessage(filePath, project)

    override fun onEnvironmentSet(emulator: EmulatorController, environment: Environment) {
      super.onEnvironmentSet(emulator, environment)
      addRecentFile(filePath, environment)
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

  class BuiltInImage(val environmentPath: Path, val title: String) : EmulatorEnvironmentAction() {

    init {
      val label = getEnvironmentTypeLabel(environmentPath)
      templatePresentation.setText("<html>${title.htmlEscaped()} <font color=\"gray\">$label</font></html>", false)
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

    fun getRecentFiles(): List<RecentFile> {
      val properties = PropertiesComponent.getInstance()
      val value = properties.getValue(RECENT_FILES_KEY) ?: return emptyList()
      return value.split('\n').filter { it.isNotEmpty() }.map { RecentFile.deserialize(it) }
    }

    fun getRecentFile(file: Path): RecentFile? {
      val pathStr = toSystemIndependentName(file.toString())
      return getRecentFiles().firstOrNull {
        toSystemIndependentName(it.path) == pathStr ||
          try {
            Path.of(it.path).toAbsolutePath().normalize() == file.toAbsolutePath().normalize()
          } catch (_: Exception) {
            false
          }
      }
    }

    fun addRecentFile(path: String) {
      addRecentFile(Path.of(path), null)
    }

    fun addRecentFile(file: Path, environment: Environment? = null) {
      val pathStr = toSystemIndependentName(file.toString())
      val existing = getRecentFile(file)
      val extractedMode = environment?.environmentMap?.get("scene.mode")?.substringBefore(':')
      val mode =
        when {
          !extractedMode.isNullOrEmpty() -> extractedMode
          is3dSceneFile(file) -> "mesh3d"
          isVideoFile(file) -> "videofile"
          existing != null && existing.mode.isNotEmpty() -> existing.mode
          else -> ""
        }
      val timestamp =
        try {
          Files.getLastModifiedTime(file).toMillis()
        } catch (_: Exception) {
          0L
        }
      val size =
        try {
          Files.size(file)
        } catch (_: Exception) {
          0L
        }

      addRecentFile(RecentFile(pathStr, timestamp, size, mode))
    }

    fun addRecentFile(recentFile: RecentFile) {
      val properties = PropertiesComponent.getInstance()
      val current = getRecentFiles().toMutableList()
      val targetPathStr = toSystemIndependentName(recentFile.path)
      current.removeAll {
        toSystemIndependentName(it.path) == targetPathStr ||
          try {
            Path.of(it.path).toAbsolutePath().normalize() == Path.of(recentFile.path).toAbsolutePath().normalize()
          } catch (_: Exception) {
            false
          }
      }
      current.add(0, recentFile)
      while (current.size > 5) {
        current.removeLast()
      }
      properties.setValue(RECENT_FILES_KEY, current.joinToString("\n") { it.serialize() })
    }
  }
}

internal data class RecentFile(val path: String, val timestamp: Long = 0L, val size: Long = 0L, val mode: String = "") {
  fun serialize(): String = "$path\t$timestamp\t$size\t$mode"

  companion object {
    fun deserialize(line: String): RecentFile {
      val parts = line.split('\t')
      if (parts.size >= 4) {
        return RecentFile(path = parts[0], timestamp = parts[1].toLongOrNull() ?: 0L, size = parts[2].toLongOrNull() ?: 0L, mode = parts[3])
      }
      return RecentFile(path = line)
    }
  }
}

internal fun is3dSceneFile(file: Path): Boolean = file.fileName?.toString()?.endsWith(".obj", ignoreCase = true) ?: false

internal fun isVideoFile(file: Path): Boolean {
  val name = file.fileName.toString()
  return name.endsWith(".mp4", ignoreCase = true) || name.endsWith(".webm", ignoreCase = true)
}

private fun getEnvironmentMode(path: Path): String {
  return when {
    is3dSceneFile(path) -> "mesh3d"
    isVideoFile(path) -> "videofile"
    is360Image(path) -> "image360"
    else -> "imagefile"
  }
}

private fun getEnvironmentTypeLabel(path: Path): String {
  val mode = EmulatorEnvironmentAction.getRecentFile(path)?.mode ?: getEnvironmentMode(path)

  return when (mode) {
    "mesh3d" -> "3D scene"
    "videofile" -> "video"
    "image360" -> "360° photo"
    else -> "photo"
  }
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
