/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.intellij.android.safemode

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.ApplicationLoadListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.impl.TitleInfoProvider
import com.intellij.openapi.wm.impl.simpleTitleParts.SimpleTitleInfoProvider
import com.intellij.openapi.wm.impl.simpleTitleParts.TitleInfoOption
import icons.StudioIllustrations
import java.awt.ComponentOrientation
import java.io.File
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.JOptionPane
import kotlin.system.exitProcess

class SafeMode : ApplicationLoadListener {
  companion object {
    private val LOG = Logger.getInstance(SafeMode::class.java)
  }

  internal var safeModeEnabled = false
    set(value) {
      field = value
      TitleInfoProvider.fireConfigurationChanged()
    }

  private val crashDetectionFile: String
    get() = "android.studio.safe.mode." + ApplicationInfo.getInstance().build + ".sentinel"

  override suspend fun beforeApplicationLoaded(application: Application, configPath: Path) {
    checkSafeMode()
  }

  private fun checkSafeMode() {
    if (safeModeDisabled()) {
      return
    }
    val studioCrashFiles = getFiles(System.getProperty("java.io.tmpdir"), crashDetectionFile)
    if (System.getProperty("studio.safe.mode") != null) {
      // If we entered safe mode, register a cleanup handler and return.
      registerCleanupHandler()
      safeModeEnabled = true
      return
    }
    if (studioCrashFiles.isEmpty()) {
      // If there are no crash files, it means we're either starting for the first time or after a clean shutdown.
      // Create crash files to detect if we've crashed for next time.
      try {
        File.createTempFile(crashDetectionFile, "")
      } catch (e: Exception) {
        LOG.error("Unexpected error while creating safe mode crash detection file ", e)
      }
      // Register a cleanup handler and return.
      registerCleanupHandler()
      return
    }

    // Otherwise we've found leftover crash files. Ask the user if they wish to start in safe mode.
    val safeModeScripts = getFiles(PathManager.getBinPath(), "studio_safe")
    if (safeModeScripts.isEmpty()) {
      return
    }

    if (!shouldStartInSafeMode()) {
      return
    }

    try {
      val p = ProcessBuilder(*command(safeModeScripts[0].toString())).inheritIO().start()
      // This is needed to clear out the heap so the safe mode script can run.
      val inputStreamThread = Thread {
        while (p.isAlive) {
          try {
            p.inputStream.readAllBytes()
            p.errorStream.readAllBytes()
          } catch (ignored: Exception) {
          }
        }
      }
      inputStreamThread.start()
    } catch (e: Exception) {
      LOG.error("Unexpected error while running safe mode ", e)
    }
    exitProcess(0)
  }

  private fun getFiles(root: String, filter: String): Array<File?> {
    val files = File(root).listFiles { file: File -> file.isFile() && file.getName().contains(filter) }
    return files ?: arrayOfNulls(0)
  }

  private fun safeModeDisabled(): Boolean {
    if (SystemInfo.isMac) {
      return true
    }
    if (System.getProperty("disable.safe.mode") != null) {
      return true
    }
    if (System.getenv("DISABLE_SAFE_MODE") != null) {
      return true
    }
    if (ApplicationManager.getApplication().isInternal) {
      return true
    }
    val files = getFiles(PathManager.getBinPath(), "disable_safe_mode")
    return files.isNotEmpty()
  }

  private fun shouldStartInSafeMode(): Boolean {
    // Creating this frame so the safe mode dialog is not hidden behind the Android Studio Icon screen.
    val frame = JFrame("Safe Mode Frame")
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE)
    frame.isVisible = true
    frame.isAlwaysOnTop = true

    val options = arrayOf("OK", "Cancel")
    val optionIndex = JOptionPane(
      "Android Studio did not properly close. Would you like to restart the IDE in Safe Mode?",
      JOptionPane.INFORMATION_MESSAGE,
      JOptionPane.DEFAULT_OPTION,
      StudioIllustrations.Common.PRODUCT_ICON,
      options,
      options[0]
    ).let {
      it.initialValue = options[0]
      it.componentOrientation = ComponentOrientation.LEFT_TO_RIGHT

      val dialog = it.createDialog(frame, "Android Studio")
      it.updateUI()
      it.selectInitialValue()
      dialog.isVisible = true

      dialog.dispose()
      val index = options.indexOf(it.value)
      if (index < 0) JOptionPane.CLOSED_OPTION else index
    }

    frame.dispose()
    if (optionIndex != 0) {
      // Register a cleanup handler and return.
      registerCleanupHandler()
      return false
    }
    return true
  }

  private fun registerCleanupHandler() {
    ApplicationManager.getApplication().messageBus.connect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
      override fun appWillBeClosed(isRestart: Boolean) {
        //  SafeMode crash detection clean up
        val studioCrashFiles = getFiles(System.getProperty("java.io.tmpdir"), crashDetectionFile)
        for (f in studioCrashFiles) {
          f!!.delete()
        }
      }
    })
  }

  private fun command(safeModeScript: String): Array<String> {
    return if (SystemInfo.isWindows) arrayOf(
      "C:\\Windows\\System32\\cmd.exe",
      "/c",
      safeModeScript
    ) else arrayOf(
      "/bin/sh",
      "-x",
      safeModeScript
    )
  }

  class TitleProvider : SimpleTitleInfoProvider(object: TitleInfoOption() {
    init {
      isActive = ApplicationLoadListener.EP_NAME.extensions.find { it is SafeMode }?.let { it as SafeMode }?.safeModeEnabled ?: false
    }
  }) {
    override fun getValue(project: Project): String = "(Safe Mode)"
    override fun isEnabled(): Boolean = option.isActive
  }
}
