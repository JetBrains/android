/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [AndroidTestExtraParamsDialog].
 */
class AndroidTestExtraParamsDialogTest : AndroidGradleTestCase() {

  @Test
  fun testAndroidTestExtraParamsDialog() {
    loadProject(TestProjectPaths.RUN_CONFIG_RUNNER_ARGUMENTS)

    // RUN_CONFIG_RUNNER_ARGUMENTS test project defines two extra params in its Gradle build file as follows.
    assertThat(myAndroidFacet.getAndroidTestExtraParams().toList()).containsExactly(
      AndroidTestExtraParam("size", "medium", "medium", AndroidTestExtraParamSource.GRADLE),
      AndroidTestExtraParam("foo", "bar", "bar", AndroidTestExtraParamSource.GRADLE))

    // Create dialog with includeGradleExtraParams true.
    var dialog = AndroidTestExtraParamsDialog(project, myAndroidFacet, "", true)
    assertThat(dialog.includeGradleExtraParams).isTrue()
    assertThat(dialog.instrumentationExtraParams).isEqualTo("-e size medium -e foo bar")
    assertThat(dialog.userModifiedInstrumentationExtraParams).isEqualTo("")
    dialog.close(0)

    // Create dialog with includeGradleExtraParams false.
    dialog = AndroidTestExtraParamsDialog(project, myAndroidFacet, "", false)
    assertThat(dialog.includeGradleExtraParams).isFalse()
    assertThat(dialog.instrumentationExtraParams).isEqualTo("")
    assertThat(dialog.userModifiedInstrumentationExtraParams).isEqualTo("")
    dialog.close(0)

    // Supplying instrumentation extra params but dialog.userModifiedInstrumentationExtraParams still returns empty because those
    // supplied params are identical to Gradle build file.
    dialog = AndroidTestExtraParamsDialog(project, myAndroidFacet, "-e foo bar -e size medium", true)
    assertThat(dialog.instrumentationExtraParams).isEqualTo("-e size medium -e foo bar")
    assertThat(dialog.userModifiedInstrumentationExtraParams).isEqualTo("")
    dialog.close(0)

    // Now override the value.
    dialog = AndroidTestExtraParamsDialog(project, myAndroidFacet, "-e foo new_value -e size medium", true)
    assertThat(dialog.instrumentationExtraParams).isEqualTo("-e size medium -e foo new_value")
    assertThat(dialog.userModifiedInstrumentationExtraParams).isEqualTo("-e foo new_value")
    dialog.close(0)

    // Also supply additional value with new param name.
    dialog = AndroidTestExtraParamsDialog(project, myAndroidFacet, "-e new_key and_value -e foo new_value -e size medium", true)
    assertThat(dialog.instrumentationExtraParams).isEqualTo("-e size medium -e foo new_value -e new_key and_value")
    assertThat(dialog.userModifiedInstrumentationExtraParams).isEqualTo("-e foo new_value -e new_key and_value")
    dialog.close(0)
  }
}
