/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.databinding.viewbinding.gradle

import com.android.tools.idea.databinding.finders.BindingKotlinScopeEnlarger
import com.android.tools.idea.databinding.util.isViewBindingEnabled
import com.android.tools.idea.gradle.model.impl.IdeViewBindingOptionsImpl
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.LightGradleSyncTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TemplateBasedTestProject
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.ModuleModelBuilder
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class ViewBindingCompletionKotlinTest {
  private object ViewBindingCompletionTestProject : LightGradleSyncTestProject {
    override val templateProject: TemplateBasedTestProject =
      AndroidCoreTestProject.SIMPLE_APPLICATION
    override val modelBuilders: List<ModuleModelBuilder> =
      listOf(
        JavaModuleModelBuilder.rootModuleBuilder,
        AndroidModuleModelBuilder(
          gradlePath = ":app",
          selectedBuildVariant = "debug",
          projectBuilder =
            AndroidProjectBuilder(
                namespace = { "google.simpleapplication" },
                viewBindingOptions = { IdeViewBindingOptionsImpl(enabled = true) },
              )
              .build(),
        ),
      )
  }

  private val projectRule =
    AndroidProjectRule.testProject(ViewBindingCompletionTestProject)
      .named("viewBindingCompletionTest")
  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val fixture
    get() = projectRule.fixture

  private val facet
    get() = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)!!

  @Before
  fun setUp() {
    assertThat(facet.isViewBindingEnabled()).isTrue()

    fixture.addFileToProject(
      "app/src/main/res/layout/activity_main.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
        <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android">
            <TextView android:id="@+id/testId"/>
        </androidx.constraintlayout.widget.ConstraintLayout>
    """
        .trimIndent(),
    )
  }

  /** Note: This test indirectly verifies [BindingKotlinScopeEnlarger]. */
  @Test
  fun completeViewBindingField_KotlinContext() {
    val testUtilFile =
      fixture.addFileToProject(
        "app/src/main/java/google/simpleapplication/TestUtil.kt",
        // language=kotlin
        """
          package google.simpleapplication

          import google.simpleapplication.databinding.ActivityMainBinding

          fun sample() {
            lateinit var binding: ActivityMainBinding
            binding.test${caret}
          }
        """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(testUtilFile.virtualFile)

    fixture.completeBasic()

    fixture.checkResult(
      // language=kotlin
      """
              package google.simpleapplication

              import google.simpleapplication.databinding.ActivityMainBinding

              fun sample() {
                lateinit var binding: ActivityMainBinding
                binding.testId
              }
          """
        .trimIndent()
    )
  }
}
