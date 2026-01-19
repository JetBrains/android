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
package com.android.tools.idea.npw.module.recipes.androidProject

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.wizard.template.withoutSkipLines
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AndroidProjectGradlePropertiesTest {

  @Test
  fun checkWithoutAndroidXDefault() {
    assertThat(
        androidProjectGradleProperties(
            // the highest version before the androidx default change in AGP 9.0
            agpVersion = AgpVersion.parse("8.13.2"),
            generateKotlin = true,
            overridePathCheck = false,
          )
          .withoutSkipLines()
      )
      .isEqualTo(
        """

        # Project-wide Gradle settings.

        # IDE (e.g. Android Studio) users:
        # Gradle settings configured through the IDE *will override*
        # any settings specified in this file.

        # For more details on how to configure your build environment visit
        # http://www.gradle.org/docs/current/userguide/build_environment.html

        # Specifies the JVM arguments used for the daemon process.
        # The setting is particularly useful for tweaking memory settings.
        org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8

        # When configured, Gradle will run in incubating parallel mode.
        # This option should only be used with decoupled projects. For more details, visit
        # https://developer.android.com/r/tools/gradle-multi-project-decoupled-projects
        # org.gradle.parallel=true

        # AndroidX package structure to make it clearer which packages are bundled with the
        # Android operating system, and which are packaged with your app's APK
        # https://developer.android.com/topic/libraries/support-library/androidx-rn
        android.useAndroidX=true
        # Kotlin code style for this project: "official" or "obsolete":
        kotlin.code.style=official
        # Allow non-ASCII characters in project path on Windows
        android.overridePathCheck=false

        """
          .trimIndent()
      )
  }

  @Test
  fun checkWithAndroidXDefault() {
    assertThat(
        androidProjectGradleProperties(
            // the lowest version after the androidx default change in AGP 9.0
            agpVersion = AgpVersion.parse("9.0.0-alpha01"),
            generateKotlin = true,
            overridePathCheck = false,
          )
          .withoutSkipLines()
      )
      .isEqualTo(
        """

        # Project-wide Gradle settings.

        # IDE (e.g. Android Studio) users:
        # Gradle settings configured through the IDE *will override*
        # any settings specified in this file.

        # For more details on how to configure your build environment visit
        # http://www.gradle.org/docs/current/userguide/build_environment.html

        # Specifies the JVM arguments used for the daemon process.
        # The setting is particularly useful for tweaking memory settings.
        org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8

        # When configured, Gradle will run in incubating parallel mode.
        # This option should only be used with decoupled projects. For more details, visit
        # https://developer.android.com/r/tools/gradle-multi-project-decoupled-projects
        # org.gradle.parallel=true

        # Kotlin code style for this project: "official" or "obsolete":
        kotlin.code.style=official
        # Allow non-ASCII characters in project path on Windows
        android.overridePathCheck=false

        """
          .trimIndent()
      )
  }
}
