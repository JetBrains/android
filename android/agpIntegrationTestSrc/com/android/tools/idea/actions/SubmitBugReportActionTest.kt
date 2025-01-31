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
package com.android.tools.idea.actions

import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.JdkUtils.createNewGradleJvmProjectJdk
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.junit.Test

/**
 * Tests for [SubmitBugReportAction]
 */
class SubmitBugReportActionTest: AndroidGradleTestCase() {

  /**
   * Verify that Gradle JDK information is used.
   */
  @Test
  fun testDescriptionContainsGradleJdk() {
    loadSimpleApplication()
    val description = SubmitBugReportAction.getDescription(project)
    val jdk = createNewGradleJvmProjectJdk(project, testRootDisposable)
    assertThat(jdk).isNotNull()
    assertThat(description).contains("Gradle JDK: ${jdk.versionString}")
    assertThat(description).doesNotContain("Gradle JDK: (default)")
    if (jdk is Disposable) {
      Disposer.dispose(jdk)
    }
  }

  /**
   * Verify that the default Gradle JDK is used when project is null
   */
  @Test
  fun testDescriptionContainsDefaultGradleJdk() {
    val description = SubmitBugReportAction.getDescription(null)
    val jdk = IdeSdks.getInstance().jdk
    assertThat(jdk).isNotNull()
    assertThat(description).contains("Gradle JDK: (default) ${jdk!!.versionString}")
  }
}