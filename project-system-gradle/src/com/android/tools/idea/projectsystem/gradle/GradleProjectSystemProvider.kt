/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.Info
import com.android.tools.idea.projectsystem.AndroidProjectSystemProvider
import com.intellij.openapi.project.Project

/**
 * A [GradleProjectSystemProvider] determines whether or not a [GradleProjectSystem] would be applicable for a given project.
 * If so, the provider is responsible for creating the instance of [GradleProjectSystem] that should be associated with the project.
 *
 * We provide this functionality in [GradleProjectSystemProvider] instead of having [GradleProjectSystem] implement
 * [AndroidProjectSystemProvider] itself because there are times when we want to instantiate the provider extension without creating
 * a new instance of the project system. In particular, the provider extension may be instantiated after the disposal of the project, in
 * which case we can't create the project system because it interacts with the project during instantiation.
 */
class GradleProjectSystemProvider : AndroidProjectSystemProvider {
  override val id = "com.android.tools.idea.GradleProjectSystem"
  override fun isApplicable(project: Project) = Info.getInstance(project).isBuildWithGradle
  override fun projectSystemFactory(project: Project) = GradleProjectSystem(project)
}
