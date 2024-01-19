/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.databinding.safedelete

import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegate
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import kotlin.test.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class LayoutBindingSafeDeleteProcessorTest {
  private val projectRule = AndroidProjectRule.onDisk()

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  // Legal cast because project rule is initialized with onDisk
  private val fixture by lazy { projectRule.fixture as JavaCodeInsightTestFixture }

  private val facet
    get() = projectRule.module.androidFacet!!

  @Before
  fun setUp() {
    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT + "/databinding"

    fixture.addFileToProject(
      "AndroidManifest.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.db">
        <application />
      </manifest>
    """
        .trimIndent(),
    )

    LayoutBindingModuleCache.getInstance(facet).dataBindingMode = DataBindingMode.ANDROIDX
  }

  /**
   * Checks that calling find usages of a layout element will find the equivalent DataBinding
   * classes.
   */
  @Test
  fun assertSafeDeletePreventsDeletingBindingLayoutFiles() {
    val activityWithUsagesFile =
      fixture.addFileToProject(
        "res/layout/activity_with_usages.xml",
        // language=XML
        """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:layout_width="fill_parent"
            android:layout_height="fill_parent" />
      </layout>
    """
          .trimIndent(),
      )

    val activityWithoutUsagesFile =
      fixture.addFileToProject(
        "res/layout/activity_without_usages.xml",
        // language=XML
        """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:layout_width="fill_parent"
            android:layout_height="fill_parent" />
      </layout>
    """
          .trimIndent(),
      )

    val activityWithoutBindingFile =
      fixture.addFileToProject(
        "res/layout/activity_without_binding.xml",
        // language=XML
        """
      <?xml version="1.0" encoding="utf-8"?>
      <LinearLayout
          android:layout_width="fill_parent"
          android:layout_height="fill_parent" />
    """
          .trimIndent(),
      )

    val nonLayoutResourceFile =
      fixture.addFileToProject(
        "res/values/strings.xml",
        // language=XML
        """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="hello">Hello World</string>
      </resources>
    """
          .trimIndent(),
      )

    val classFile =
      fixture.addFileToProject(
        "src/java/test/db/WithUsagesActivity.java",
        // language=JAVA
        """
      package test.db;

      import android.app.Activity;
      import android.os.Bundle;

      import test.db.databinding.ActivityWithUsagesBinding;

      public class WithUsagesActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              ActivityWithUsagesBinding binding = ActivityWithUsagesBinding.inflate(getLayoutInflater());
              setContentView(binding.getRoot());
          }
      }
    """
          .trimIndent(),
      )

    // Make sure we take precedence over the regular android resource file SafeDeleteProcessor
    val safeDeleteProcessor =
      SafeDeleteProcessorDelegate.EP_NAME.findExtensionOrFail(
        LayoutBindingSafeDeleteProcessor::class.java
      )
    assertThat(safeDeleteProcessor.handlesElement(activityWithUsagesFile)).isTrue()
    assertThat(safeDeleteProcessor.handlesElement(activityWithoutUsagesFile)).isTrue()
    assertThat(safeDeleteProcessor.handlesElement(activityWithoutBindingFile)).isFalse()
    assertThat(safeDeleteProcessor.handlesElement(nonLayoutResourceFile)).isFalse()
    assertThat(safeDeleteProcessor.handlesElement(classFile)).isFalse()

    assertThat(activityWithUsagesFile.virtualFile.exists()).isTrue()
    assertThat(activityWithoutUsagesFile.virtualFile.exists()).isTrue()

    try {
      SafeDeleteHandler.invoke(projectRule.project, arrayOf(activityWithUsagesFile), true)
      fail("Should not be able to safe delete layout file with usages from code")
    } catch (ignored: BaseRefactoringProcessor.ConflictsInTestsException) {}
    assertThat(activityWithUsagesFile.virtualFile.exists()).isTrue()

    // But we can delete everything at once
    SafeDeleteHandler.invoke(projectRule.project, arrayOf(activityWithUsagesFile, classFile), true)
    assertThat(activityWithUsagesFile.virtualFile.exists()).isFalse()

    // No issues deleting a binding layout without any usages
    SafeDeleteHandler.invoke(projectRule.project, arrayOf(activityWithoutUsagesFile), true)
    assertThat(activityWithoutUsagesFile.virtualFile.exists()).isFalse()

    // Verify we get out of the way to allow the regular android resource file SafeDeleteProcessor
    // to work
    assertThat(activityWithoutBindingFile.virtualFile.exists()).isTrue()
    assertThat(nonLayoutResourceFile.virtualFile.exists()).isTrue()
    SafeDeleteHandler.invoke(
      projectRule.project,
      arrayOf(activityWithoutBindingFile, nonLayoutResourceFile),
      true,
    )
    assertThat(activityWithoutBindingFile.virtualFile.exists()).isFalse()
    assertThat(nonLayoutResourceFile.virtualFile.exists()).isFalse()
  }
}
