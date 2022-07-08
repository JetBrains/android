/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.model

interface IdeCompositeBuildMap {
  val builds: List<IdeBuild>

  /**
   * Returns `true` if the version of Gradle used by this project supports direct invocation of from included builds. I.e. tasks can be
   * referred to as `:included_build_name:project:path:task_name`.
   */
  val gradleSupportsDirectTaskInvocation: Boolean

  object EMPTY: IdeCompositeBuildMap {
    override val builds: List<IdeBuild>
      get() = emptyList()
    override val gradleSupportsDirectTaskInvocation: Boolean
      get() = false
  }
}