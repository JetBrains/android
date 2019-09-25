/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.module

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * This interface provides extension point to customize the list of new Modules that can be created.
 * Each provider can return a list of Modules it knows how how to create, and a UI Step to implement its creation.
 */
interface ModuleDescriptionProvider {
  fun getDescriptions(project: Project): Collection<ModuleGalleryEntry>

  companion object {
    val EP_NAME = ExtensionPointName.create<ModuleDescriptionProvider>("com.android.moduleDescriptionProvider")
  }
}
