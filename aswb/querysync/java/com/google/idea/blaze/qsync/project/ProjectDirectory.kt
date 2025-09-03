/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync.project

import java.nio.file.Path

interface ProjectDirectory {
  val directoryName: String
  val containsSources: Boolean
}

enum class QuerySyncProjectDirectory(
  override val directoryName: String,
  override val containsSources: Boolean
) : ProjectDirectory {
  BAZEL_ARTIFACTS(".bazel", true),
  BAZEL_SYSTEM(".blaze", true),
  BUILD_CACHE(".buildcache", true)
}

interface ProjectDirectoryConfigurator {
  fun configureDirectory(directory: ProjectDirectory): Path
}
