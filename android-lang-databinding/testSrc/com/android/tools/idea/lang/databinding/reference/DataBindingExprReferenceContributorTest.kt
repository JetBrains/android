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
package com.android.tools.idea.lang.databinding.reference

import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.ModuleDataBinding
import com.android.tools.idea.lang.databinding.getTestDataPath
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * A collection of tests that provide code coverage for [DataBindingExprReferenceContributor] logic.
 */
@RunWith(Parameterized::class)
@RunsInEdt
class DataBindingExprReferenceContributorTest(private val mode: DataBindingMode) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modes() = listOf(DataBindingMode.SUPPORT,
                         DataBindingMode.ANDROIDX)
  }

  private val projectRule = AndroidProjectRule.withSdk()

  // projectRule must NOT be created on the EDT
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val fixture: JavaCodeInsightTestFixture by lazy {
    projectRule.fixture as JavaCodeInsightTestFixture
  }

  @Before
  fun setUp() {
    fixture.testDataPath = getTestDataPath()

    fixture.addFileToProject("AndroidManifest.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.langdb">
        <application />
      </manifest>
    """.trimIndent())

    val androidFacet = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)
    ModuleDataBinding.getInstance(androidFacet!!).setMode(mode)
  }

  @Test
  fun dbIdRefererencesXmlVariable() {
    fixture.addClass("""
      package test.langdb;

      public class Model {
        public ObservableField<String> strValue = new ObservableField<String>("value");
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:text="@{mo<caret>del.strValue}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val element = fixture.elementAtCaret as XmlTag
    assertThat(element.name).isEqualTo("variable")
    assertThat(element.attributes.find { attr -> attr.name == "name" && attr.value == "model" }).isNotNull()
  }

  @Test
  fun dbFieldRefererencesClassField() {
    fixture.addClass("""
      package test.langdb;

      import ${mode.packageName}ObservableField;

      public class Model {
        public ObservableField<String> strValue = new ObservableField<String>("value");
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:text="@{model.str<caret>Value}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val element = fixture.elementAtCaret as PsiField
    assertThat(element.name).isEqualTo("strValue")
  }

  @Test
  fun dbMethodRefererencesClassMethod() {
    fixture.addClass("""
      package test.langdb;

      public class Model {
        public void doSomething() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:text="@{model.do<caret>Something()}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val element = fixture.elementAtCaret as PsiMethod
    assertThat(element.name).isEqualTo("doSomething")
  }

  @Test
  fun dbPropertyRefererencesClassMethod() {
    fixture.addClass("""
      package test.langdb;

      public class Model {
        public String getStrValue() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:text="@{mod<caret>el.str<caret>Value}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val element = fixture.elementAtCaret as PsiMethod
    assertThat(element.name).isEqualTo("getStrValue")
  }

  @Test
  fun dbIdRefererencesXmlImport() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public static void handleClick(View v) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model" />
        </data>
        <TextView android:onClick="@{Mo<caret>del::handleClick}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val element = fixture.elementAtCaret as XmlTag
    assertThat(element.name).isEqualTo("import")
    assertThat(element.attributes.find { attr -> attr.name == "type" && attr.value == "test.langdb.Model" }).isNotNull()
  }

  @Test
  fun dbStaticMethodReferenceRefererencesClassMethod() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public static void handleClick(View v) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model" />
        </data>
        <TextView android:onClick="@{Model::handle<caret>Click}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val element = fixture.elementAtCaret as PsiMethod
    assertThat(element.name).isEqualTo("handleClick")
  }

  @Test
  fun dbInstanceMethodReferenceRefererencesClassMethod() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class ClickHandler {
        public void handleClick(View v) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="clickHandler" type="test.langdb.ClickHandler" />
        </data>
        <TextView android:onClick="@{clickHandler::handle<caret>Click}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val element = fixture.elementAtCaret as PsiMethod
    assertThat(element.name).isEqualTo("handleClick")
  }

  @Test
  fun dbMethodCallRefererencesClassMethod() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public void handleClick(View v) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:onClick="@{(v) -> model.handle<caret>Click(v)}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val element = fixture.elementAtCaret as PsiMethod
    assertThat(element.name).isEqualTo("handleClick")
  }

  @Test
  fun dbFullyQualifiedIdReferencesClass() {
    fixture.addClass("""
      package test.langdb;

      import ${mode.packageName}ObservableField;

      public class Model {
        public static final ObservableField<String> NAME = new ObservableField<>("Model");
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView android:text="@{test.langdb.Mo<caret>del.NAME}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val element = fixture.elementAtCaret as PsiClass
    assertThat(element.name).isEqualTo("Model")
  }
}