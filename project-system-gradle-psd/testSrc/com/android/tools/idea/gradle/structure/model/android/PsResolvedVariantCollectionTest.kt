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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.intellij.testFramework.RunsInEdt
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Assert.assertThat
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class PsResolvedVariantCollectionTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testVariants() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {
      val appModule = project.findModuleByGradlePath(":app") as PsAndroidModule

      assertThat(
        appModule.resolvedVariants.map { it.key }.toSet(), equalTo(
          setOf(
            PsVariantKey("debug", listOf("paid", "bar"), "paidBarDebug"),
            PsVariantKey("release", listOf("paid", "bar"), "paidBarRelease"),
            PsVariantKey("specialRelease", listOf("paid", "bar"), "paidBarSpecialRelease"),
            PsVariantKey("debug", listOf("paid", "otherBar"), "paidOtherBarDebug"),
            PsVariantKey("release", listOf("paid", "otherBar"), "paidOtherBarRelease"),
            PsVariantKey("specialRelease", listOf("paid", "otherBar"), "paidOtherBarSpecialRelease"),
            PsVariantKey("debug", listOf("basic", "bar"), "basicBarDebug"),
            PsVariantKey("release", listOf("basic", "bar"), "basicBarRelease"),
            PsVariantKey("specialRelease", listOf("basic", "bar"), "basicBarSpecialRelease"),
            PsVariantKey("debug", listOf("basic", "otherBar"), "basicOtherBarDebug"),
            PsVariantKey("release", listOf("basic", "otherBar"), "basicOtherBarRelease"),
            PsVariantKey("specialRelease", listOf("basic", "otherBar"), "basicOtherBarSpecialRelease")
          )
        )
      )
    }
  }
}