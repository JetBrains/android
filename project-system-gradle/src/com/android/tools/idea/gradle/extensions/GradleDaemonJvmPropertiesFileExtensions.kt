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
package com.android.tools.idea.gradle.extensions

import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.properties.GRADLE_FOLDER
import org.jetbrains.plugins.gradle.properties.GRADLE_DAEMON_JVM_PROPERTIES_FILE_NAME
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path

fun GradleDaemonJvmPropertiesFile.getPropertyPath(externalProjectPath: @SystemIndependent String): Path {
  return Path(externalProjectPath).resolve(Paths.get(GRADLE_FOLDER, GRADLE_DAEMON_JVM_PROPERTIES_FILE_NAME))
    .toAbsolutePath().normalize()
}
