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

import java.io.File
import java.io.Serializable

/**
 * A project path together with the name and location of the build containing it.
 */
interface IdeProjectPath: Serializable {
  /**
   * The build ID (directory containing the settings file) of the root build of this project.
   *
   * Note, this directory might be different from the root directory of the root project of the root build if the root project directory is
   * relocated.
   */
  val rootBuildId: File

  /**
   * The build ID (directory containing the settings file) of the (included) build containing this project.
   *
   * Note, this directory might be different from the root directory of the root project of the root build if the root project directory is
   * relocated.
   */
  val buildId: File

  /**
   * The name of the included build containing this project or ":" if this project belongs to the root build.
   */
  val buildName: String

  /**
   * Returns the Gradle project path of the module (excluding the build name, if in an included build).
   */
  val projectPath: String
}