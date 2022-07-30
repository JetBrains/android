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
package com.android.tools.idea.logcat

import com.android.tools.idea.model.AndroidModuleInfo
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

class ProjectPackageNamesProvider(project: Project) : PackageNamesProvider {
  private val moduleManager = ModuleManager.getInstance(project)

  // TODO(b/206675088): Maybe get package names from run configurations too?
  // TODO(b/206675088): Maybe get notified when the set of package names might change?
  override fun getPackageNames(): Set<String> =
    moduleManager.modules.mapNotNullTo(mutableSetOf()) { AndroidModuleInfo.getInstance(it)?.`package` }
}