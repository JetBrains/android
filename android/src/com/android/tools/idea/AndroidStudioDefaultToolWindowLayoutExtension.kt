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
package com.android.tools.idea

import com.intellij.toolWindow.DefaultToolWindowLayoutBuilder
import com.intellij.toolWindow.DefaultToolWindowLayoutExtension
import com.intellij.toolWindow.ToolWindowDescriptor

internal class AndroidStudioDefaultToolWindowLayoutExtension : DefaultToolWindowLayoutExtension {
  override fun buildV1Layout(builder: DefaultToolWindowLayoutBuilder) {
    // We just keep the default behavior on the old UI
  }

  // Derived from DefaultToolWindowStripeBuilderImpl.addPlatformDefaultsV2(),
  // with tweaks for our user base. We start with removeAll() to have a clean
  // slate (this EP is anchored last to have the final say), then add in what
  // we need.
  override fun buildV2Layout(builder: DefaultToolWindowLayoutBuilder) {
    builder.removeAll()

    builder.left.apply {
      addOrUpdate("Project") {
        weight = 0.25f
        contentUiType = ToolWindowDescriptor.ToolWindowContentUiType.COMBO
      }
      addOrUpdate("Commit") { weight = 0.25f }
      addOrUpdate("Resources Explorer") {
        weight = 0.25f
      }
    }

    builder.right.apply {
      addOrUpdate("Notifications") {
        weight = 0.25f
        contentUiType = ToolWindowDescriptor.ToolWindowContentUiType.COMBO
      }
      addOrUpdate("Gradle") { weight = 0.25f }
      addOrUpdate("Device Manager") { weight = 0.25f }
      addOrUpdate("Device Manager 2") { weight = 0.25f }
      addOrUpdate("Running Devices") { weight = 0.25f }
      addOrUpdate("StudioBot") { weight = 0.25f }
    }

    builder.bottom.apply {
      addOrUpdate("Version Control")
      addOrUpdate("Terminal")
      // Auto-Build
      addOrUpdate("Problems")
      // Problems
      addOrUpdate("Problems View")
      addOrUpdate("Logcat")
      addOrUpdate("App Quality Insights")
      addOrUpdate("Build")
    }
  }
}