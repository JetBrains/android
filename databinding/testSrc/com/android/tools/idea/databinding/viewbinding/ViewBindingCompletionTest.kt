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
package com.android.tools.idea.databinding.viewbinding

import com.android.flags.junit.RestoreFlagRule
import com.android.ide.common.gradle.model.stubs.ViewBindingOptionsStub
import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.databinding.finders.BindingKotlinScopeEnlarger
import com.android.tools.idea.databinding.finders.BindingScopeEnlarger
import com.android.tools.idea.databinding.isViewBindingEnabled
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.createAndroidProjectBuilder
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class ViewBindingCompletionTest {
  private val projectRule =
    AndroidProjectRule.withAndroidModel(createAndroidProjectBuilder(viewBindingOptions = { ViewBindingOptionsStub(true) }))

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @get:Rule
  val viewBindingFlagRule = RestoreFlagRule(StudioFlags.VIEW_BINDING_ENABLED)

  /**
   * Expose the underlying project rule fixture directly.
   *
   * We know that the underlying fixture is a [JavaCodeInsightTestFixture] because our
   * [AndroidProjectRule] is initialized to use the disk.
   */
  private val fixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

  private val facet
    get() = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)!!

  @Before
  fun setUp() {
    StudioFlags.VIEW_BINDING_ENABLED.override(true)
    assertThat(facet.isViewBindingEnabled()).isTrue()
    fixture.addFileToProject("src/main/AndroidManifest.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.vb">
        <application />
      </manifest>
    """.trimIndent())

    fixture.addFileToProject("src/main/res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
        <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android">
            <TextView android:id="@+id/testId"/>
        </androidx.constraintlayout.widget.ConstraintLayout>
    """.trimIndent())

  }

  @Test
  fun completeViewBindingClass() {
    val modelFile = fixture.addFileToProject(
      "src/main/java/test/vb/Model.java",
      // language=JAVA
      """
          package test.vb;

          import test.vb.databinding.ActivityMainBinding;

          public class Model {
            ActivityMainB<caret>
          }
        """.trimIndent())

    fixture.configureFromExistingVirtualFile(modelFile.virtualFile)

    fixture.completeBasic()

    fixture.checkResult(
      // language=JAVA
      """
        package test.vb;

        import test.vb.databinding.ActivityMainBinding;

        public class Model {
          ActivityMainBinding
        }
      """.trimIndent())
  }

  /**
   * Note: The Java version of this test indirectly verifies [BindingScopeEnlarger].
   */
  @Test
  fun completeViewBindingField_JavaContext() {
    val activityFile = fixture.addFileToProject(
      "src/main/java/test/vb/MainActivity.java",
      // language=JAVA
      """
          package test.vb;

          import android.app.Activity;
          import android.os.Bundle;
          import test.vb.databinding.ActivityMainBinding;

          public class MainActivity extends Activity {
              @Override
              protected void onCreate(Bundle savedInstanceState) {
                  super.onCreate(savedInstanceState);
                  ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
                  binding.test<caret>
              }
          }
        """.trimIndent())

    fixture.configureFromExistingVirtualFile(activityFile.virtualFile)

    fixture.completeBasic()

    fixture.checkResult(
      // language=JAVA
      """
        package test.vb;

        import android.app.Activity;
        import android.os.Bundle;
        import test.vb.databinding.ActivityMainBinding;

        public class MainActivity extends Activity {
            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
                binding.testId
            }
        }
      """.trimIndent())
  }

  /**
   * Note: The Kotlin version of this test indirectly verifies [BindingKotlinScopeEnlarger].
   */
  @Test
  fun completeViewBindingField_KotlinContext() {
    val testUtilFile = fixture.addFileToProject(
      "src/main/java/test/vb/TestUtil.kt",
      // language=kotlin
      """
          package test.vb

          import test.vb.databinding.ActivityMainBinding

          fun dummy() {
            lateinit var binding: ActivityMainBinding
            binding.test<caret>
          }
        """.trimIndent())

    fixture.configureFromExistingVirtualFile(testUtilFile.virtualFile)

    fixture.completeBasic()

    fixture.checkResult(
      // language=kotlin
      """
          package test.vb

          import test.vb.databinding.ActivityMainBinding

          fun dummy() {
            lateinit var binding: ActivityMainBinding
            binding.testId
          }
      """.trimIndent())
  }
}