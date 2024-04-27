/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.invoker

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages

enum class TerminateDebuggerChoice {
  TERMINATE_DEBUGGER, DO_NOT_TERMINATE_DEBUGGER, CANCEL_BUILD;

  companion object {
    fun promptUserToStopNativeDebugSession(project: Project): TerminateDebuggerChoice {
      // If we have a native debugging process running, we need to kill it to release the files from being held open by the OS.
      val propKey = "gradle.project.build.invoker.clean-terminates-debugger"
      // We set the property globally, rather than on a per-project basis since the debugger either keeps files open on the OS or not.
      // If the user sets the property, it is stored in <config-dir>/config/options/options.xml
      val value: String? = PropertiesComponent.getInstance().getValue(propKey)

      if (value == null) {
        var choice: TerminateDebuggerChoice? = null
        ApplicationManager.getApplication().invokeAndWait(
          {
            val message: String = "Cleaning or rebuilding your project while debugging can lead to unexpected " +
                                  "behavior.\n" +
                                  "You can choose to either terminate the debugger before cleaning your project " +
                                  "or keep debugging while cleaning.\n" +
                                  "Clicking \"Cancel\" stops Gradle from cleaning or rebuilding your project, " +
                                  "and preserves your debug process."
            val dialogBuilder: MessageDialogBuilder.YesNoCancel = MessageDialogBuilder.yesNoCancel(
              "Terminate debugging", message
            )
            val answer: Int = dialogBuilder
              .yesText("Terminate")
              .noText("Do not terminate")
              .cancelText("Cancel")
              .doNotAsk(
                object : DoNotAskOption.Adapter() {
                  override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
                    if (isSelected) {
                      PropertiesComponent.getInstance().setValue(propKey, (exitCode == Messages.YES).toString())
                    }
                  }
                })
              .show(project)
            choice = when (answer) {
              Messages.YES -> TerminateDebuggerChoice.TERMINATE_DEBUGGER
              Messages.NO -> TerminateDebuggerChoice.DO_NOT_TERMINATE_DEBUGGER
              Messages.CANCEL -> TerminateDebuggerChoice.CANCEL_BUILD
              else -> error("Unknown selection: $answer")
            }
          }, ModalityState.nonModal()
        )
        return choice!!
      }
      getLogger().debug(propKey + ": " + value)
      return if (value == "true") TerminateDebuggerChoice.TERMINATE_DEBUGGER else TerminateDebuggerChoice.DO_NOT_TERMINATE_DEBUGGER
    }

    private fun getLogger(): Logger {
      return Logger.getInstance(TerminateDebuggerChoice::class.java)
    }
  }
}