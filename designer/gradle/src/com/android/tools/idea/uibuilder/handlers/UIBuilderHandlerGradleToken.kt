/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers

import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler.AddElementType
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler.AddElementType.CONSTRAINT_SET
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler.AddElementType.FLOW
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler.AddElementType.GROUP
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler.AddElementType.HORIZONTAL_BARRIER
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler.AddElementType.HORIZONTAL_GUIDELINE
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler.AddElementType.LAYER
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler.AddElementType.VERTICAL_BARRIER
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler.AddElementType.VERTICAL_GUIDELINE

class UIBuilderHandlerGradleToken : UIBuilderHandlerToken<GradleProjectSystem>, GradleToken {
  private fun hasMaterial3Dependency(moduleSystem: GradleModuleSystem) =
    moduleSystem
      .getResolvedDependency(GoogleMavenArtifactId.MATERIAL.getModule(), DependencyScopeType.MAIN)
      ?.let { it.version > Version.prefixInfimum("1.5.0") } ?: false

  override fun getBottomAppBarStyle(
    projectSystem: GradleProjectSystem,
    newChild: NlComponent,
  ): String? {
    val module = newChild.model.module
    val moduleSystem = projectSystem.getModuleSystem(module)
    return when {
      hasMaterial3Dependency(moduleSystem) -> null
      else -> "Widget.MaterialComponents.BottomAppBar.Colored"
    }
  }

  private fun constraintLayoutVersion(moduleSystem: GradleModuleSystem): Version? =
    moduleSystem
      .getResolvedDependency(
        GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getModule(),
        DependencyScopeType.MAIN,
      )
      ?.version

  override fun showConvertToMotionLayoutComponentsAction(
    projectSystem: GradleProjectSystem,
    viewEditor: ViewEditor,
  ): Boolean {
    val version =
      constraintLayoutVersion(projectSystem.getModuleSystem(viewEditor.model.module)) ?: return true
    return version > Version.parse("2.0.0-beta02")
  }

  override fun showAddElementsAction(
    projectSystem: GradleProjectSystem,
    viewEditor: ViewEditor,
    type: AddElementType,
  ): Boolean {
    val version =
      constraintLayoutVersion(projectSystem.getModuleSystem(viewEditor.model.module)) ?: return true
    return when (type) {
      HORIZONTAL_GUIDELINE,
      VERTICAL_GUIDELINE -> true
      HORIZONTAL_BARRIER,
      VERTICAL_BARRIER,
      GROUP -> version > Version.parse("1.0")
      LAYER,
      CONSTRAINT_SET,
      FLOW -> version > Version.parse("1.9")
    }
  }
}
