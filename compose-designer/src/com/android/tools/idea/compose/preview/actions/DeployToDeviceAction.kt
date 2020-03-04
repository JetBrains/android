/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_ELEMENT
import com.android.tools.idea.compose.preview.runconfiguration.isNonLibraryAndroidModule
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import icons.StudioIcons.Compose.RUN_ON_DEVICE
import org.jetbrains.kotlin.idea.util.module

/**
 * Action to deploy a @Composable to the device.
 *
 * @param dataContextProvider returns the [DataContext] containing the Compose Preview associated information.
 */
internal class DeployToDeviceAction(private val dataContextProvider: () -> DataContext) : AnAction(null, null, RUN_ON_DEVICE) {
  override fun actionPerformed(e: AnActionEvent) {
    // TODO(b/150391302)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled =
      dataContextProvider().getData(COMPOSE_PREVIEW_ELEMENT)?.previewBodyPsi?.element?.module?.isNonLibraryAndroidModule() == true
  }
}