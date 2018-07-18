/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.helpers

import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat

class PropertyKnownValuesKtTest : AndroidGradleTestCase() {

  fun testBuildTypeMatchingFallbackValuesCore() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)

    assertThat(buildTypeMatchingFallbackValuesCore(project),
               equalTo(listOf(ValueDescriptor("debug"), ValueDescriptor("release"), ValueDescriptor("specialRelease"))))
  }

  fun testProductFlavorMatchingFallbackValuesCore() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)

    assertThat(productFlavorMatchingFallbackValuesCore(project, "foo"),
               equalTo(listOf(ValueDescriptor("basic"), ValueDescriptor("paid"))))
    assertThat(productFlavorMatchingFallbackValuesCore(project, "bar"),
               equalTo(listOf(ValueDescriptor("bar"), ValueDescriptor("otherBar"))))
  }
}
