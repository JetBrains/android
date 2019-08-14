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
import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.databinding.finders.BindingKotlinScopeEnlarger
import com.android.tools.idea.databinding.finders.BindingScopeEnlarger
import com.android.tools.idea.databinding.isViewBindingEnabled
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class ViewBindingCompletionTest {
  private val projectRule = AndroidGradleProjectRule()

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
    get() = projectRule.androidFacet

  @Before
  fun setUp() {
    StudioFlags.VIEW_BINDING_ENABLED.override(true)

    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
  }

  @Test
  fun completeViewBindingClass() {
    lateinit var modelFile: VirtualFile
    projectRule.load(TestDataPaths.PROJECT_FOR_VIEWBINDING) {
      modelFile = addOrOverwriteFile(
        "app/src/main/java/com/android/example/viewbinding/Model.java",
        // language=JAVA
        """
          package com.android.example.viewbinding;

          import com.android.example.viewbinding.databinding.ActivityMainBinding;

          public class Model {
            ActivityMainB<caret>
          }
        """.trimIndent())
    }
    assertThat(facet.isViewBindingEnabled()).isTrue()

    fixture.configureFromExistingVirtualFile(modelFile)

    fixture.completeBasic()

    fixture.checkResult(
      // language=JAVA
      """
        package com.android.example.viewbinding;

        import com.android.example.viewbinding.databinding.ActivityMainBinding;

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
    lateinit var activityFile: VirtualFile
    projectRule.load(TestDataPaths.PROJECT_FOR_VIEWBINDING) {
      activityFile = addOrOverwriteFile(
        "app/src/main/java/com/android/example/viewbinding/MainActivity.java",
        // language=JAVA
        """
          package com.android.example.viewbinding;

          import android.app.Activity;
          import android.os.Bundle;
          import com.android.example.viewbinding.databinding.ActivityMainBinding;

          public class MainActivity extends Activity {
              @Override
              protected void onCreate(Bundle savedInstanceState) {
                  super.onCreate(savedInstanceState);
                  ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
                  binding.test<caret>
              }
          }
        """.trimIndent())
    }

    assertThat(facet.isViewBindingEnabled()).isTrue()

    fixture.configureFromExistingVirtualFile(activityFile)

    fixture.completeBasic()

    fixture.checkResult(
      // language=JAVA
      """
        package com.android.example.viewbinding;

        import android.app.Activity;
        import android.os.Bundle;
        import com.android.example.viewbinding.databinding.ActivityMainBinding;

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
    lateinit var testFile: VirtualFile
    projectRule.load(TestDataPaths.PROJECT_FOR_VIEWBINDING) {
      testFile = addOrOverwriteFile(
        "app/src/main/java/com/android/example/viewbinding/TestUtil.kt",
        // language=kotlin
        """
          package com.android.example.viewbinding

          import com.android.example.viewbinding.databinding.ActivityMainBinding

          fun dummy() {
            lateinit var binding: ActivityMainBinding
            binding.test<caret>
          }
        """.trimIndent())
    }
    assertThat(facet.isViewBindingEnabled()).isTrue()

    fixture.configureFromExistingVirtualFile(testFile)

    fixture.completeBasic()

    fixture.checkResult(
      // language=kotlin
      """
          package com.android.example.viewbinding

          import com.android.example.viewbinding.databinding.ActivityMainBinding

          fun dummy() {
            lateinit var binding: ActivityMainBinding
            binding.testId
          }
      """.trimIndent())
  }
}