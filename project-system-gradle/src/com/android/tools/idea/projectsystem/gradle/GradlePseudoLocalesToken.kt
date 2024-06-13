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

import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.project.FacetBasedApplicationProjectContext
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.PseudoLocalesToken
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

class GradlePseudoLocalesToken(val project: Project): PseudoLocalesToken, GradleToken {
  override fun isPseudoLocalesEnabled(applicationProjectContext: ApplicationProjectContext): PseudoLocalesToken.PseudoLocalesState {
    val applicationId = applicationProjectContext.applicationId
    val context = (applicationProjectContext as? FacetBasedApplicationProjectContext) ?: return PseudoLocalesToken.PseudoLocalesState.UNKNOWN.also { Logger.getInstance(GradlePseudoLocalesToken::class.java).debug("Expected FacetBasedApplicationProjectContext, but got ", applicationProjectContext) }
    var enabled = false
    var disabled = false
    val model = GradleAndroidModel.get(context.facet) ?: return PseudoLocalesToken.PseudoLocalesState.UNKNOWN.also { Logger.getInstance(GradlePseudoLocalesToken::class.java).warn("Failed to find GradleAndroidModel for ${context.facet.module.name}") }
    for (variant in model.androidProject.basicVariants) {
      if (variant.applicationId != applicationId && variant.testApplicationId != applicationId) continue
      if (model.getBuildType(variant).buildType.isPseudoLocalesEnabled) {
        enabled = true
      } else {
        disabled = true
      }
    }
    return when {
      enabled && disabled -> PseudoLocalesToken.PseudoLocalesState.BOTH
      enabled ->  PseudoLocalesToken.PseudoLocalesState.ENABLED
      disabled -> PseudoLocalesToken.PseudoLocalesState.DISABLED
      else -> PseudoLocalesToken.PseudoLocalesState.UNKNOWN.also { Logger.getInstance(GradlePseudoLocalesToken::class.java).warn("Model is inconsistent, could not find variant for $applicationId in ${applicationProjectContext.facet.module.name} ") }
    }
  }


}