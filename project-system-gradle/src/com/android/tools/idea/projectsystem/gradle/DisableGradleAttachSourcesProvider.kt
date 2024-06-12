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
package com.android.tools.idea.projectsystem.gradle

import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gradle.action.GradleAttachSourcesProvider

class DisableGradleAttachSourcesProvider : ApplicationInitializedListener {
  // Unregister the extension to make "Download Source" button disappear
  override suspend fun execute(asyncScope: CoroutineScope) {
    ExtensionPointName<AttachSourcesProvider>("com.intellij.attachSourcesProvider")
      .point.unregisterExtension(GradleAttachSourcesProvider::class.java)
  }
}