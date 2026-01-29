/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.resource

import com.android.ide.common.gradle.Module as ExternalModule
import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.openapi.module.Module
import com.intellij.pom.java.LanguageLevel

private val KOTLIN_MODULE = ExternalModule("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
private val COMPOSE_UI_MODULE = ExternalModule("androidx.compose.ui", "ui-android")
private val MIN_COMPOSE_UI_VERSION = Version.parse("1.9.0")
private val MIN_AGP_VERSION = AgpVersion(9, 0, 0)

/**
 * Support for checking the Gradle build settings to determine if a lambda location was not found due to missing support in earlier versions
 * of AGP.
 *
 * In kotlin 2.0 the compiler is generating "invokedynamic" instructions for lambda invocations. Since this instruction doesn't exist in
 * ART, D8 will generate a bridge method that the compose layout inspector agent needs hint for to determine where the code resides. The
 * hints that D8 generates (annotations on the bridge method) are on by default starting with AGP 9.0.0. D8 is using global synthetics to
 * implement this which is only supported by AGP for JDK17 and above. See b/417709154 and b/423826264.
 */
class LambdaResolutionGradleToken : LambdaResolutionToken<GradleProjectSystem>, GradleToken {

  /**
   * Return a problem that explains missing lambda source locations for the situation described above. Or null if no problem can be
   * detected.
   */
  override fun findCauseOfMissingSourceLocation(projectSystem: GradleProjectSystem, module: Module): VersionProblem? {
    val moduleSystem = projectSystem.getModuleSystem(module)
    val scope = DependencyScopeType.MAIN
    val kotlinVersion = moduleSystem.getResolvedDependency(KOTLIN_MODULE, scope)
    val kotlinMajorVersion = kotlinVersion?.version?.major ?: return null
    if (kotlinMajorVersion < 2) {
      // If the app is not built with kotlin 2.0, then this check doesn't apply.
      return null
    }
    // The support for reading annotations was added in the compose layout inspector agent version 1.9.0.
    val composeVersion = moduleSystem.getResolvedDependency(COMPOSE_UI_MODULE, scope)?.version ?: return null
    if (composeVersion < MIN_COMPOSE_UI_VERSION) {
      return VersionProblem(ProblemType.COMPOSE_UI, MIN_COMPOSE_UI_VERSION.toString(), composeVersion.toString())
    }
    val androidModel = GradleAndroidModel.get(module) ?: return null
    val agp = androidModel.agpVersion
    if (agp.compareIgnoringQualifiers(MIN_AGP_VERSION) < 0) {
      return VersionProblem(ProblemType.D8, MIN_AGP_VERSION.toString(), agp.toString())
    }
    val level = androidModel.getJavaSourceLanguageLevel()
    if (level?.isLessThan(LanguageLevel.JDK_17) == true) {
      return VersionProblem(ProblemType.JDK, LanguageLevel.JDK_17.shortText, level.shortText)
    }
    return null
  }
}
