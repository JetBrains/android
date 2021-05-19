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
package com.android.tools.idea.startup

import com.android.tools.idea.ApkFacetChecker
import com.android.tools.idea.apk.ApkFacet
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

/**
 * Class that initializes [ApkFacetChecker] as soon as the project is opened and stores it in the project.
 */
class ApkFacetCheckerInitializer : ProjectManagerListener {
  companion object {
    private val checker: (module: Module) -> Boolean = { ApkFacet.getInstance(it) != null }
  }

  override fun projectOpened(project: Project) {
    ApkFacetChecker(checker).storeInProject(project)
  }
}