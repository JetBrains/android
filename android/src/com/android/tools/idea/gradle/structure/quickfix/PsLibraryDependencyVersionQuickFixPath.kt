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
package com.android.tools.idea.gradle.structure.quickfix

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.issues.QUICK_FIX_PATH_TYPE
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsQuickFix
import java.io.Serializable

const val DEFAULT_QUICK_FIX_TEXT = "[Fix]"

data class PsLibraryDependencyVersionQuickFixPath(
  val moduleName: String,
  val dependency: String,
  val configurationName: String,
  val version: String,
  override val text: String
) : PsQuickFix, Serializable {

  constructor(
    dependency: PsLibraryDependency,
    version: String,
    quickFixText: String = DEFAULT_QUICK_FIX_TEXT
  ) : this(
    dependency.parent.name, dependency.spec.compactNotation(), dependency.joinedConfigurationNames, version, quickFixText)

  override fun execute(context: PsContext) {
    val module = context.project.findModuleByName(moduleName)
    val spec = PsArtifactDependencySpec.create(dependency)
    if (module != null && spec != null) {
      module.setLibraryDependencyVersion(spec, configurationName, version)
    }
  }

  override fun toString(): String = "$dependency ($configurationName)"
}
