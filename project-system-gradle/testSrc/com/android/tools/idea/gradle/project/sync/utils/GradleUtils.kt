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
package com.android.tools.idea.gradle.project.sync.utils

import com.android.SdkConstants
import com.google.common.base.Strings
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

object GradleUtils {

  /**
   * Calculates location of user gradle.properties based on system properties and environment variables. See
   * [gradle properties](https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties)
   * section in gradle documentation for the context.
   * @return file pointing to gradle.properties in gradle user home.
   */
  fun getUserGradlePropertiesFile(): File {
    var gradleUserHome = System.getProperty("gradle.user.home")
    if (Strings.isNullOrEmpty(gradleUserHome)) {
      gradleUserHome = System.getenv(GradleConstants.SYSTEM_DIRECTORY_PATH_KEY)
    }
    if (Strings.isNullOrEmpty(gradleUserHome)) {
      gradleUserHome = FileUtil.join(System.getProperty("user.home"), SdkConstants.DOT_GRADLE)
    }
    return File(gradleUserHome, SdkConstants.FN_GRADLE_PROPERTIES)
  }
}