/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.editor

import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.gradle.getAndroidTestModule
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.openapi.module.Module

class TestRunParametersGradleToken : TestRunParameters.TestRunParametersToken<GradleProjectSystem>, GradleToken {
  override fun canSelectInstrumentationRunnerClass(projectSystem: GradleProjectSystem) = false

  override fun getModuleForPackageChooser(projectSystem: GradleProjectSystem, module: Module) = module.getAndroidTestModule() ?: module
}