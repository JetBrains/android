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

import com.android.tools.idea.databinding.finders.BindingScopeEnlarger
import com.android.tools.idea.databinding.util.isViewBindingEnabled
import com.android.tools.idea.gradle.model.impl.IdeViewBindingOptionsImpl
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
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
    AndroidProjectRule.withAndroidModel(
      AndroidProjectBuilder(
        namespace = { "test.vb" },
        viewBindingOptions = { IdeViewBindingOptionsImpl(enabled = true) },
      )
    )

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

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
    assertThat(facet.isViewBindingEnabled()).isTrue()

    fixture.addFileToProject(
      "src/main/res/layout/activity_main.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
        <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android">
            <TextView android:id="@+id/testId"/>
        </androidx.constraintlayout.widget.ConstraintLayout>
    """
        .trimIndent(),
    )
  }

  @Test
  fun completeViewBindingIgnoreAttribute() {
    val layoutFile =
      fixture.addFileToProject(
        "src/main/res/layout/activity_ignored.xml",
        // language=XML
        """
        <?xml version="1.0" encoding="utf-8"?>
        <FrameLayout
          xmlns:tools="http://schemas.android.com/tools"
          tools:viewBindingI<caret>>
        </FrameLayout>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(layoutFile.virtualFile)

    fixture.completeBasic()

    fixture.checkResult(
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <FrameLayout
        xmlns:tools="http://schemas.android.com/tools"
        tools:viewBindingIgnore="">
      </FrameLayout>
      """
        .trimIndent()
    )
  }

  @Test
  fun completeViewBindingIgnoreAttribute_BooleanValue() {
    val layoutFile =
      fixture.addFileToProject(
        "src/main/res/layout/activity_ignored.xml",
        // language=XML
        """
        <?xml version="1.0" encoding="utf-8"?>
        <FrameLayout
          xmlns:tools="http://schemas.android.com/tools"
          tools:viewBindingIgnore="<caret>">
        </FrameLayout>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(layoutFile.virtualFile)

    fixture.completeBasic()
    assertThat(fixture.lookupElementStrings).containsAllIn(listOf("true", "false"))
  }

  @Test
  fun completeViewBindingClass() {
    val modelFile =
      fixture.addFileToProject(
        "src/main/java/test/vb/Model.java",
        // language=JAVA
        """
          package test.vb;

          import test.vb.databinding.ActivityMainBinding;

          public class Model {
            ActivityMainB${caret}
          }
        """
          .trimIndent(),
      )

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
      """
        .trimIndent()
    )
  }

  /** Note: This test indirectly verifies [BindingScopeEnlarger]. */
  @Test
  fun completeViewBindingField_JavaContext() {
    val activityFile =
      fixture.addFileToProject(
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
                  binding.test${caret}
              }
          }
        """
          .trimIndent(),
      )

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
      """
        .trimIndent()
    )
  }
}
