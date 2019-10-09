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
package com.android.tools.idea.gradle.structure.configurables

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.setup.post.project.GradleKtsBuildFilesWarningStep
import com.android.tools.idea.gradle.structure.IdeSdksConfigurable
import com.android.tools.idea.gradle.structure.configurables.suggestions.SuggestionsPerspectiveConfigurable
import com.android.tools.idea.gradle.structure.configurables.variables.VariablesConfigurable
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.structure.dialog.AndroidConfigurableContributor
import com.android.tools.idea.structure.dialog.ProjectStructureItemGroup
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

class GradleAndroidConfigurableContributor : AndroidConfigurableContributor() {

  override fun getConfigurables(project: Project, parentDisposable: Disposable): List<ProjectStructureItemGroup> {
    val repositorySearchFactory = CachingRepositorySearchFactory()
    val context = PsContextImpl(PsProjectImpl(project, repositorySearchFactory), parentDisposable, false, false, repositorySearchFactory)

    if (GradleKtsBuildFilesWarningStep.HAS_KTS_BUILD_FILES.get(project, false)) {
      if (!StudioFlags.KOTLIN_DSL_PARSING.get()) {
        return ktsRestrictedProjectStructure(project, context)
      }
    }

    return listOf(
      ProjectStructureItemGroup("main",
                                ProjectPerspectiveConfigurable(context),
                                IdeSdksConfigurable(null, project),
                                VariablesConfigurable(project, context)),
      ProjectStructureItemGroup("modules",
                                ModulesPerspectiveConfigurable(context),
                                DependenciesPerspectiveConfigurable(context),
                                BuildVariantsPerspectiveConfigurable(context)
      ),
      ProjectStructureItemGroup("additional", SuggestionsPerspectiveConfigurable(context))
    )
  }

  private fun ktsRestrictedProjectStructure(project: Project, context: PsContextImpl): List<ProjectStructureItemGroup> {
    return listOf(
      ProjectStructureItemGroup("main",
                                KtsProjectPerspectiveConfigurable(),
                                IdeSdksConfigurable(null, project))
    )
  }
}
