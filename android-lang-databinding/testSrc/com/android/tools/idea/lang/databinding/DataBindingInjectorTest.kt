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
package com.android.tools.idea.lang.databinding

import com.android.SdkConstants
import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * A collection of various code injection tests that verify DbLanguageInjector works as expected.
 */
@RunsInEdt
@RunWith(Parameterized::class)
class DataBindingInjectorTest(private val mode: DataBindingMode) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}")
    val modes = listOf(DataBindingMode.ANDROIDX, DataBindingMode.SUPPORT)
  }

  private val projectRule = AndroidProjectRule.withSdk()

  // We want to run tests on the EDT thread, but we also need to make sure the project rule is not
  // initialized on the EDT.
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  /**
   * Expose the underlying project rule fixture directly.
   *
   * We know that the underlying fixture is a [JavaCodeInsightTestFixture] because our
   * [AndroidProjectRule] is initialized to use the disk.
   *
   * In some cases, using the specific subclass provides us with additional methods we can
   * use to inspect the state of our parsed files. In other cases, it's just fewer characters
   * to type.
   */
  private val fixture: JavaCodeInsightTestFixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

  private val androidFacet
    get() = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)!!

  @Before
  fun setUp() {
    fixture.testDataPath = "${getTestDataPath()}/projects/common"
    fixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML)
    LayoutBindingModuleCache.getInstance(androidFacet).dataBindingMode = mode
  }

  @Test
  fun testDataBindingInjectionWithCustomAttribute() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;
      import ${mode.bindingAdapter};

      public class ModelWithBindingAdapters {
        public String doSomething(View view) {
          return "string";
        }
        @BindingAdapter("print")
        public static void bindAuthor(View v, String text) {
          System.out.println(v.getClass() + ": " + text);
        }
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindingAdapters"/>
          <variable name="member" type="ModelWithBindingAdapters" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            app:print="@{${caret}member.doSomething()}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    assertThat(fixture.elementAtCaret.text).isEqualTo("<variable name=\"member\" type=\"ModelWithBindingAdapters\" />")
  }


  @Test
  fun testDataBindingInjectionWithAndroidAttribute() {
    fixture.addClass("""
      package test.langdb;
      import android.view.View;
      public class Model {
        public String doSomething(View view) {
          return "string";
        }
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="member" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:text="@{${caret}member.doSomething()}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    assertThat(fixture.elementAtCaret.text).isEqualTo("<variable name=\"member\" type=\"Model\" />")
  }

  /**
   * Ideally, we should not inject DbLanguage with unknown attribute. However, the checking process is too slow and we decide to remove it
   * for now.
   */
  @Test
  fun testDataBindingInjectionWithUnknownAttribute() {
    fixture.addClass("""
      package test.langdb;
      import android.view.View;
      public class Model {
        public String doSomething(View view) {
          return "string";
        }
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="member" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            app:print="@{${caret}member.doSomething()}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    assertThat(fixture.elementAtCaret.text).isEqualTo("<variable name=\"member\" type=\"Model\" />")
  }

  @Test
  fun testDataBindingInjectionWithInvalidPrefix() {
    fixture.addClass("""
      package test.langdb;
      import android.view.View;
      public class Model {
        public String doSomething(View view) {
          return "string";
        }
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="member" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            app:print="@DataBinding{${caret}member.doSomething()}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    // TODO: use assertThrows instead after JUnit 4.13 is released
    try {
      fixture.elementAtCaret
      fail("fixture.elementAtCaret did not throw expected AssertionError")
    }
    catch (e: AssertionError) {
      assertThat(e.message!!).contains("element not found in file")
    }
  }
}
