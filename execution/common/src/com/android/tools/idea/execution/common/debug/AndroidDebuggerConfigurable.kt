/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.execution.common.debug

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.ui.VerticalFlowLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

open class AndroidDebuggerConfigurable<in S : AndroidDebuggerState> {
  private val attachOnWaitForDebugger = JCheckBox("Automatically attach on Debug.waitForDebugger()")
  private val debugSandboxSdk = JCheckBox("Launch and attach debugger to Sandbox SDK process on start-up")
  open val component: JComponent?
    get() =
      if (StudioFlags.ATTACH_ON_WAIT_FOR_DEBUGGER.get() || StudioFlags.LAUNCH_SANDBOX_SDK_PROCESS_WITH_DEBUGGER_ATTACHED_ON_DEBUG.get()) {
        val mainPanel = JPanel(VerticalFlowLayout()).apply {
          if (StudioFlags.ATTACH_ON_WAIT_FOR_DEBUGGER.get()) { add(attachOnWaitForDebugger) }
          if (StudioFlags.LAUNCH_SANDBOX_SDK_PROCESS_WITH_DEBUGGER_ATTACHED_ON_DEBUG.get()) { add(debugSandboxSdk) }
        }
        mainPanel
      } else {
        null
      }

  open fun resetFrom(state: S) {
    if (StudioFlags.ATTACH_ON_WAIT_FOR_DEBUGGER.get()) attachOnWaitForDebugger.isSelected = state.attachOnWaitForDebugger()
    if (StudioFlags.LAUNCH_SANDBOX_SDK_PROCESS_WITH_DEBUGGER_ATTACHED_ON_DEBUG.get()) debugSandboxSdk.isSelected = state.debugSandboxSdk()
  }
  open fun applyTo(state: S) {
    if (StudioFlags.ATTACH_ON_WAIT_FOR_DEBUGGER.get()) state.setAttachOnWaitForDebugger(attachOnWaitForDebugger.isSelected)
    if (StudioFlags.LAUNCH_SANDBOX_SDK_PROCESS_WITH_DEBUGGER_ATTACHED_ON_DEBUG.get()) state.setDebugSandboxSdk(debugSandboxSdk.isSelected)
  }
}