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
package com.android.tools.idea.databinding.findusages

import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.UsageTargetUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Checking that DataBinding elements appear in Android Resource Usages.
 *
 * Currently we only support Android Resources usages including DataBinding elements, and not the
 * other way around.
 */
class DataBindingFindUsagesTest() {

  @get:Rule val projectRule = AndroidProjectRule.withSdk()

  private val fixture by lazy { projectRule.fixture }

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
  fun assertDataBindingAppearsInLayoutResourceUsages() {
    fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
            <Button
                android:id="@+id/button"
                android:layout_width="fill_parent"
                android:layout_height="fill_parent" />
        </LinearLayout>
      </layout>
    """
        .trimIndent(),
    )

    val classFile =
      fixture.addFileToProject(
        "src/java/test/db/MainActivity.java",
        // language=JAVA
        """
      package test.db;

      import android.app.Activity;
      import android.os.Bundle;

      import test.db.databinding.ActivityMainBinding;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
              System.out.println(binding.button.getId());
              setContentView(binding.getRoot());
              int value = R.layout.activity_m${caret}ain;
          }
      }
    """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(classFile.virtualFile)
    val presentation = getUsagePresentationAtCursor()

    assertThat(presentation)
      .isEqualTo(
        """
      <root> (5)
       Layout Resource
        @layout/activity_main
       Usages in Project Files (5)
        Class static member access (1)
         app (1)
          java.test.db (1)
           MainActivity (1)
            onCreate(Bundle) (1)
             12ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        Local variable declaration (1)
         app (1)
          java.test.db (1)
           MainActivity (1)
            onCreate(Bundle) (1)
             12ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        Resource declaration in Android resources XML (1)
         app (1)
          res/layout (1)
           activity_main.xml (1)
            1<?xml version="1.0" encoding="utf-8"?>
        Resource reference in code (1)
         app (1)
          java.test.db (1)
           MainActivity (1)
            onCreate(Bundle) (1)
             15int value = R.layout.activity_main;
        Usage in import (1)
         app (1)
          java.test.db (1)
           MainActivity (1)
            6import test.db.databinding.ActivityMainBinding;

    """
          .trimIndent()
      )
  }

  /** Checks calling find usages on an ID resource will find any relevant DataBinding fields. */
  @Test
  fun assertRenameFieldDerivedFromResourceId() {
    val layoutFile =
      fixture.addFileToProject(
        "res/layout/activity_main.xml",
        // language=XML
        """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
            <Button
                android:id="@+id/but${caret}ton"
                android:layout_width="fill_parent"
                android:layout_height="fill_parent" />
        </LinearLayout>
      </layout>
    """
          .trimIndent(),
      )

    fixture.addFileToProject(
      "src/java/test/db/MainActivity.java",
      // language=JAVA
      """
      package test.db;

      import android.app.Activity;
      import android.os.Bundle;

      import test.db.databinding.ActivityMainBinding;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
              System.out.println(binding.button.getId());
              setContentView(binding.getRoot());
          }
      }
    """
        .trimIndent(),
    )

    fixture.configureFromExistingVirtualFile(layoutFile.virtualFile)
    val presentation = getUsagePresentationAtCursor()

    assertThat(presentation)
      .isEqualTo(
        """
      <root> (2)
       ID Resource
        @id/button
       Usages in Project Files (2)
        Resource declaration in Android resources XML (1)
         app (1)
          res/layout (1)
           activity_main.xml (1)
            7android:id="@+id/button"
        Unclassified (1)
         app (1)
          java.test.db (1)
           MainActivity (1)
            onCreate(Bundle) (1)
             13System.out.println(binding.button.getId());

    """
          .trimIndent()
      )
  }

  @Test
  fun duplicateIdInTwoLayoutFiles() {
    val layoutFile1 =
      fixture.addFileToProject(
        "res/layout/activity_main.xml",
        // language=XML
        """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
            <Button
                android:id="@+id/button"
                android:layout_width="fill_parent"
                android:layout_height="fill_parent" />
        </LinearLayout>
      </layout>
    """
          .trimIndent(),
      )

    val layoutFile2 =
      fixture.addFileToProject(
        "res/layout/activity_main2.xml",
        // language=XML
        """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
            <Button
                android:id="@+id/button"
                android:layout_width="fill_parent"
                android:layout_height="fill_parent" />
        </LinearLayout>
      </layout>
    """
          .trimIndent(),
      )

    fixture.addFileToProject(
      "src/java/test/db/MainActivity.java",
      // language=JAVA
      """
      package test.db;

      import android.app.Activity;
      import android.os.Bundle;

      import test.db.databinding.ActivityMainBinding;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
              System.out.println(binding.button.getId());
              setContentView(binding.getRoot());
          }
      }
    """
        .trimIndent(),
    )

    // Find usages for layout 1 should have three; both xml files (because they both reference the
    // same id), and the binding class usage.
    fixture.configureFromExistingVirtualFile(layoutFile1.virtualFile)
    ApplicationManager.getApplication().invokeAndWait { fixture.moveCaret("@+id/but|ton") }
    val presentationLayout1 = getUsagePresentationAtCursor()

    assertThat(presentationLayout1).contains("<root> (3)")
    assertThat(presentationLayout1)
      .contains(
        """
      |  Resource declaration in Android resources XML (2)
      |   app (2)
      |    res/layout (2)
      |     activity_main.xml (1)
      |      7android:id="@+id/button"
      |     activity_main2.xml (1)
      |      7android:id="@+id/button"
      """
          .trimMargin()
      )
    assertThat(presentationLayout1)
      .contains(
        """
      |  Unclassified (1)
      |   app (1)
      |    java.test.db (1)
      |     MainActivity (1)
      |      onCreate(Bundle) (1)
      |       13System.out.println(binding.button.getId());
      """
          .trimMargin()
      )

    // Find usages for layout 2 should not include the binding class reference
    fixture.configureFromExistingVirtualFile(layoutFile2.virtualFile)
    ApplicationManager.getApplication().invokeAndWait { fixture.moveCaret("@+id/but|ton") }
    val presentationLayout2 = getUsagePresentationAtCursor()

    assertThat(presentationLayout2).contains("<root> (2)")
    assertThat(presentationLayout2)
      .contains(
        """
      |  Resource declaration in Android resources XML (2)
      |   app (2)
      |    res/layout (2)
      |     activity_main.xml (1)
      |      7android:id="@+id/button"
      |     activity_main2.xml (1)
      |      7android:id="@+id/button"
      """
          .trimMargin()
      )
    assertThat(presentationLayout2).doesNotContain("binding.button")
  }

  private fun getUsagePresentationAtCursor(): String {
    val targets = runReadAction {
      UsageTargetUtil.findUsageTargets { dataId ->
        (fixture.editor as EditorEx).dataContext.getData(dataId)
      }
    }

    var presentation: String? = null
    ApplicationManager.getApplication().invokeAndWait {
      presentation =
        fixture.getUsageViewTreeTextRepresentation(
          (targets.first() as PsiElementUsageTarget).element
        )
    }

    return presentation!!
  }
}
