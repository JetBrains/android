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
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.UsageTargetUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Checking that DataBinding elements appear in Android Resource Usages.
 *
 * Currently we only support Android Resources usages including DataBinding elements, and not the other way around.
 */
@RunsInEdt
class DataBindingFindUsagesTest() {

  private val projectRule = AndroidProjectRule.withSdk()

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  // Legal cast because project rule is initialized with onDisk
  private val fixture by lazy { projectRule.fixture as JavaCodeInsightTestFixture }

  private val facet
    get() = projectRule.module.androidFacet!!

  @Before
  fun setUp() {
    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT + "/databinding"

    fixture.addFileToProject("AndroidManifest.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.db">
        <application />
      </manifest>
    """.trimIndent())

    LayoutBindingModuleCache.getInstance(facet).dataBindingMode = DataBindingMode.ANDROIDX
  }

  /**
   * Checks that calling find usages of a layout element will find the equivalent DataBinding classes.
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
    """.trimIndent())

    val classFile = fixture.addFileToProject(
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
    """.trimIndent()
    )

    fixture.configureFromExistingVirtualFile(classFile.virtualFile)
    val targets = UsageTargetUtil.findUsageTargets {
      dataId: String? -> (fixture.editor as EditorEx).dataContext.getData(dataId!!)
    }
    val presentation = fixture.getUsageViewTreeTextRepresentation((targets.first() as PsiElementUsageTarget).element)

    assertThat(presentation).isEqualTo("""
      <root> (5)
       Layout Resource
        @layout/activity_main
       Found usages (5)
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

    """.trimIndent())
  }

  /**
   * Checks calling find usages on an ID resource will find any relevant DataBinding fields.
   */
  @Test
  fun assertRenameFieldDerivedFromResourceId() {
    val layoutFile = fixture.addFileToProject(
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
    """.trimIndent())

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
    """.trimIndent()
    )

    fixture.configureFromExistingVirtualFile(layoutFile.virtualFile)
    val targets = UsageTargetUtil.findUsageTargets {
      dataId: String? -> (fixture.editor as EditorEx).dataContext.getData(dataId!!)
    }
    val presentation = fixture.getUsageViewTreeTextRepresentation((targets.first() as PsiElementUsageTarget).element)

    assertThat(presentation).isEqualTo("""
      <root> (2)
       ID Resource
        @id/button
       Found usages (2)
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

    """.trimIndent())
  }
}
