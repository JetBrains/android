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
package com.android.tools.idea.execution.common

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApkProvisionException
import com.intellij.execution.ExecutionException


@Throws(ExecutionException::class)
fun shouldDebugSandboxSdk(apkProvider: ApkProvider, device: IDevice, state: AndroidDebuggerState): Boolean {
  return hasDebugSandboxSdkEnabled(state) &&
    device.version.isGreaterOrEqualThan(AndroidVersion.VersionCodes.TIRAMISU) &&
    hasPrivacySandboxSdk(apkProvider, device)
}

private fun hasDebugSandboxSdkEnabled(state: AndroidDebuggerState): Boolean {
  return StudioFlags.LAUNCH_SANDBOX_SDK_PROCESS_WITH_DEBUGGER_ATTACHED_ON_DEBUG.get() && state != null && state.DEBUG_SANDBOX_SDK
}

private fun hasPrivacySandboxSdk(apkProvider: ApkProvider, device: IDevice): Boolean {
  return try {
    val apkList = apkProvider.getApks(device)
    for (apk in apkList) {
      if (apk.isSandboxApk) {
        return true
      }
    }
    false
  } catch (e: ApkProvisionException) {
    throw ExecutionException(e)
  }
}