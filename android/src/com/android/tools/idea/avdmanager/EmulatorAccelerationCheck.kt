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
@file:JvmName("EmulatorAccelerationCheck")

package com.android.tools.idea.avdmanager

import com.android.SdkConstants
import com.android.repository.Revision
import com.android.sdklib.devices.Storage
import com.android.sdklib.internal.avd.getEmulatorPackage
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.memorysettings.MemorySettingsUtil
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler
import com.intellij.openapi.diagnostic.logger

/**
 * Run "emulator -accel-check" to check the status for emulator acceleration on this machine. Return
 * a [AccelerationErrorCode].
 */
fun checkAcceleration(sdk: AndroidSdkHandler): AccelerationErrorCode {
  val emulator = sdk.getEmulatorPackage(progressIndicator)
  val emulatorBinary =
    emulator?.emulatorBinary ?: return AccelerationErrorCode.NO_EMULATOR_INSTALLED
  // TODO: The emulator -accel-check currently does not check for the available memory, do it here
  // instead:
  val memoryBytes = MemorySettingsUtil.getMachineMemoryBytes()
  if (memoryBytes != null && memoryBytes < Storage.Unit.GiB.numberOfBytes) {
    return AccelerationErrorCode.NOT_ENOUGH_MEMORY
  }
  if (!emulator.isQemu2) {
    return AccelerationErrorCode.TOOLS_UPDATE_REQUIRED
  }
  val commandLine = GeneralCommandLine()
  val checkBinary = emulator.emulatorCheckBinary
  if (checkBinary != null) {
    commandLine.exePath = checkBinary.toString()
    commandLine.addParameter("accel")
  } else {
    commandLine.exePath = emulatorBinary.toString()
    commandLine.addParameter("-accel-check")
  }
  try {
    val exitCode = CapturingAnsiEscapesAwareProcessHandler(commandLine).runProcess().exitCode
    if (exitCode != 0) {
      return AccelerationErrorCode.fromExitCode(exitCode)
    }
  } catch (e: ExecutionException) {
    logger<EmulatorAccelerationChecks>().warn(e)
    return AccelerationErrorCode.UNKNOWN_ERROR
  }
  if (!sdk.hasPlatformToolsForQemu2Installed()) {
    return AccelerationErrorCode.PLATFORM_TOOLS_UPDATE_ADVISED
  }
  if (!sdk.hasSystemImagesForQemu2Installed()) {
    return AccelerationErrorCode.SYSTEM_IMAGE_UPDATE_ADVISED
  }
  return AccelerationErrorCode.ALREADY_INSTALLED
}

private fun AndroidSdkHandler.hasPlatformToolsForQemu2Installed(): Boolean {
  val platformTools =
    getLocalPackage(SdkConstants.FD_PLATFORM_TOOLS, progressIndicator) ?: return false
  return platformTools.version >= PLATFORM_TOOLS_REVISION_WITH_FIRST_QEMU2
}

private fun AndroidSdkHandler.hasSystemImagesForQemu2Installed(): Boolean {
  val emulator = getEmulatorPackage(progressIndicator) ?: return false
  val images = getSystemImageManager(progressIndicator).getImages()
  return images.stream().noneMatch(emulator.getSystemImageUpdateRequiredPredicate())
}

private object EmulatorAccelerationChecks

private val progressIndicator =
  StudioLoggerProgressIndicator(EmulatorAccelerationChecks::class.java)
private val PLATFORM_TOOLS_REVISION_WITH_FIRST_QEMU2 = Revision.parseRevision("23.1.0")
