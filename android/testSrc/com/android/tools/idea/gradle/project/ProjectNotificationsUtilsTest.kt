/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project

import com.android.tools.idea.gradle.project.sync.hyperlink.UseEmbeddedJdkHyperlink
import com.android.tools.idea.sdk.IdeSdks
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.HeavyPlatformTestCase

class ProjectNotificationsUtilsTest: HeavyPlatformTestCase() {
  fun testInvalidJdkErrorMessageNullPath() {
    val expectedMessage = "Could not determine Gradle JDK\n" +
                          "Having an incorrect Gradle JDK may result in unresolved symbols and problems when running Gradle tasks."
    val actualMessage = invalidJdkErrorMessage(null)
    assertThat(actualMessage).isEqualTo(expectedMessage)
  }

  fun testInvalidJdkErrorMessageInvalidPath() {
    val expectedMessage = "Could not find a valid JDK at /path/to/invalid/jdk/\n" +
                          "Having an incorrect Gradle JDK may result in unresolved symbols and problems when running Gradle tasks."
    val actualMessage = invalidJdkErrorMessage("/path/to/invalid/jdk/")
    assertThat(actualMessage).isEqualTo(expectedMessage)
  }

  fun testInvalidJdkErrorMessageValidPath() {
    val jdk = IdeSdks.getInstance().jdk
    assertThat(jdk).isNotNull()
    val jdkPath = jdk!!.homePath
    assertThat(jdkPath).isNotEmpty()
    val actualMessage = invalidJdkErrorMessage(jdkPath)
    assertThat(actualMessage).isNull()
  }

  fun testInvalidGradleJdkLinks() {
    val links = generateInvalidGradleJdkLinks(project)
    assertThat(links).hasSize(1)
    assertThat(links[0]).isInstanceOf(UseEmbeddedJdkHyperlink::class.java)
  }
}