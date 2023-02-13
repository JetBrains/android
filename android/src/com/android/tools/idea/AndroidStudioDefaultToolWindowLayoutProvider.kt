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
import com.intellij.toolWindow.IntellijPlatformDefaultToolWindowLayoutProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal

@Suppress("UnstableApiUsage")
@Internal
class AndroidStudioDefaultToolWindowLayoutProvider : IntellijPlatformDefaultToolWindowLayoutProvider() {

  override fun configureLeftVisibleOnLargeStripe(builder: DefaultToolWindowLayoutBuilder) {
    builder.add("Commit", weight = 0.25f)
    builder.add("Resources Explorer", weight = 0.25f)
  }

  override fun configureRightVisibleOnLargeStripe(builder: DefaultToolWindowLayoutBuilder) {
    builder.add("Gradle", weight = 0.25f)
    builder.add("Device Manager", weight = 0.25f)
    builder.add("Running Devices", weight = 0.25f)
  }
}