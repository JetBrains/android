/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.welcome.install

import com.android.SdkConstants.FD_EMULATOR
import com.android.annotations.concurrency.Slow
import com.android.tools.idea.avdmanager.HardwareAccelerationCheck
import com.google.common.base.Joiner
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.system.CpuArch

import java.io.File

/**
 * Checks SDK install to ensure install may proceed.
 *
 * This is done by trying to run the mksdcard executable from the SDK emulator directory (falling back to the older tools directory).
 * This ensures that the tools are installed and that necessary shared libraries are present.
 */
class CheckSdkOperation(context: InstallContext) : InstallOperation<File, File>(context, 0.0) {
  @Throws(WizardException::class, InstallationCancelledException::class)
  override fun perform(indicator: ProgressIndicator, file: File): File {
    if (HardwareAccelerationCheck.isChromeOSAndIsNotHWAccelerated()) {
      return file
    }

    val tool = File(file, FD_EMULATOR + File.separator + TOOL_NAME)
    if (!tool.isFile) {
      throw WizardException(ERROR_NO_EMULATOR_DIR)
    }
    if (!checkExecutePermission(tool)) {
      throw WizardException("${tool.absolutePath} file is not a valid executable")
    }
    if (!checkRuns(tool)) {
      throw WizardException(ERROR_CANT_RUN_TOOL)
    }
    return file
  }

  override fun cleanup(result: File) {}
}

private val MESSAGE_CANT_RUN_TOOL = "<html><p>" + Joiner.on("</p><p>").join(unableToRunMessage) + "</p></html>"
private val TOOL_NAME = "mksdcard" + (".exe".takeIf { SystemInfo.isWindows } ?: "")
private val ERROR_CANT_RUN_TOOL = "Unable to run $TOOL_NAME SDK tool." +
                                  ("\nTry installing the latest Visual C++ Runtime from Microsoft.".takeIf { SystemInfo.isWindows } ?: "")
private const val URL_MISSING_LIBRARIES = "https://developer.android.com/studio/troubleshoot.html#linux-libraries"
private const val LINK_MISSING_LIBRARIES = "Show Android SDK web page"
private const val ERROR_NO_EMULATOR_DIR = "SDK emulator directory is missing"

private val unableToRunMessage: Collection<String>
  get() = sequence {
    val isLinux64 = SystemInfo.isLinux && !CpuArch.is32Bit()
    val missingLibrariesDescription = if (isLinux64) "32-bit compatibility" else "required"

    yield("Unable to run <strong>$TOOL_NAME</strong> SDK tool.")
    yield( "One common reason for this failure is missing $missingLibrariesDescription libraries.")
    yield("Please fix the underlying issue and retry.")
    if (isLinux64) {
      yield("<a href=\"$URL_MISSING_LIBRARIES\">$LINK_MISSING_LIBRARIES</a>")
    }
  }.toList()

private fun checkCanRunSdkTool(executable: File): Boolean {
  val commandLine = GeneralCommandLine(executable.absolutePath)
  val handler = CapturingAnsiEscapesAwareProcessHandler(commandLine)
  return handler.runProcess().exitCode == 1 // 1 means help was printed
}

private fun checkExecutePermission(executable: File) = executable.canExecute() || (SystemInfo.isUnix && executable.setExecutable(true))

private fun retryPrompt(): Boolean {
  val button = Messages.showOkCancelDialog(MESSAGE_CANT_RUN_TOOL, "Android Studio", "Retry", "Cancel", Messages.getErrorIcon())
  return button == Messages.OK
}

@Slow
private fun checkRuns(executable: File): Boolean {
  try {
    while (!checkCanRunSdkTool(executable)) {
      val shouldRetry = invokeAndWaitIfNeeded { retryPrompt() }
      if (!shouldRetry) {
        return false
      }
    }
  }
  catch (e: ExecutionException) {
    return false
  }
  return true
}
