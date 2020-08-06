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
package com.android.tools.idea.lang.databinding.completion

import com.android.SdkConstants
import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.lang.databinding.getTestDataPath
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.facet.FacetManager
import com.intellij.psi.PsiField
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * A collection of various code completion tests that verify data binding completions work as
 * expected.
 */
@RunWith(Parameterized::class)
class DataBindingCompletionContributorTest(private val dataBindingMode: DataBindingMode) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modes() = listOf(DataBindingMode.SUPPORT,
                         DataBindingMode.ANDROIDX)
  }

  private val projectRule = AndroidProjectRule.withSdk()

  @get:Rule
  val chain: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  private val fixture: JavaCodeInsightTestFixture by lazy {
    projectRule.fixture as JavaCodeInsightTestFixture
  }

  @Before
  fun setUp() {
    fixture.testDataPath = "${getTestDataPath()}/projects/common"
    fixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML)
    val androidFacet = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)
    LayoutBindingModuleCache.getInstance(androidFacet!!).dataBindingMode = dataBindingMode
  }

  @Test
  fun testDataBindingCompletion_caretInMethodReference() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public void doSomething(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model::do${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model::doSomething}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_caretInStaticMethodReference() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public static void doSomethingStatic(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{Model::d${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{Model::doSomethingStatic}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_excludeNonPublicMethods() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        private static void doSomethingStatic(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{Model::d${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{Model::d}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_excludeConstructors() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {}
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{Model::${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{Model::}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_undefinedType() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{Model::d${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{Model::d}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_completeInstanceMethodInStaticContext() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public void doSomethingStatic(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{Model::d${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{Model::d}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_completeStaticMethodInNonStaticContext() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public static void doSomethingStatic(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model::d${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model::d}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_repeatedInvocationsIncludeAllSuggestions() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public static void doSomethingStatic(View view) {}
        private static void doPrivateStatic(View view) {}
        private void doPrivate(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model::d${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.complete(CompletionType.BASIC, 2)
    fixture.assertPreferredCompletionItems(0, "doPrivate", "doPrivateStatic", "doSomethingStatic")
  }

  @Test
  fun testDataBindingCompletion_onMethodWithParameters_caretMovesInsideParens() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public void doSomething(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{() -> model.do${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{() -> model.doSomething(${caret})}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_onMethodWithNoParameters_caretMovesAfterParens() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public void doSomethingNoParameters() {}
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
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{() -> member.do${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="member" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{() -> member.doSomethingNoParameters()${caret}}"/>
      </layout>
    """.trimIndent())
  }

  @RunsInEdt
  @Test
  fun testDataBindingCompletion_multipleLookupItems() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public static void function_a(View view) {}
        public static void function_b(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{() -> model.fu${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.complete(CompletionType.BASIC, 2)
    fixture.assertPreferredCompletionItems(0, "function_a", "function_b")
  }

  @Test
  fun testDataBindingCompletion_methodsWithSameName() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public void doSomething(View view) {}
        public void doSomething(View view, int a) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model::do${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model::doSomething}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_methodsFromBaseClass() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Base {
        public void methodFromBaseClass(View view) {}
      }
    """.trimIndent())

    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model extends Base {
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model::m${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model::methodFromBaseClass}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_fieldsFromBaseClass() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Base {
        public int fieldFromBaseClass = 0;
      }
    """.trimIndent())

    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model extends Base {
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model.fi${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model.fieldFromBaseClass}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  @RunsInEdt
  fun testDataBindingCompletion_methodPresentation() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public void doSomething(View view) {}
        public void doSomething2(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model::do${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()
    fixture.assertPreferredCompletionItems(0, "doSomething", "doSomething2")
  }

  @Test
  @RunsInEdt
  fun testDataBindingCompletion_methodFromBaseClass() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class ModelWithBindableMethods extends Base{
        public void doSomething(View view) {}
      }

      class Base {
        public void doSomethingBase(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethods"/>
          <variable name="model" type="ModelWithBindableMethods" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model::do${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()
    fixture.assertPreferredCompletionItems(0, "doSomething", "doSomethingBase")
  }

  @Test
  @RunsInEdt
  fun testDataBindingCompletion_fieldFromBaseClass() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model extends Base{
        public int field;
      }

      class Base {
        public int fieldBase;
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{model.fi${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()
    fixture.assertPreferredCompletionItems(0, "field", "fieldBase")
  }

  @Test
  @RunsInEdt
  fun testDataBindingCompletion_fieldsAreSuggestedWithType() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public int field1;
        public String field2;
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{model.fi${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val lookupElements = fixture.completeBasic()

    assertThat(lookupElements.size).isEqualTo(2)
    assertThat(lookupElements.map { it.renderedText }).containsExactly("int field1", "String field2")
  }

  @Test
  fun testDataBindingCompletion_testCompleteVariableOutsideReferenceContext() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="str" type="String" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{st${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="str" type="String" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{str}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_testCompleteStaticFunctionOutsideReferenceContext() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public static String strUpper(View view) {
          return "a";
        }
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{Model::st${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{Model::strUpper}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_testCompleteJavaLangClasses() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{By${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{Byte}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_getterMethodConvertedToField() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public String getValue() { return "unused"; }
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model.${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).doesNotContain("getValue")
    val lookupElement = fixture.lookupElements!!.first { it.lookupString == "value" }
    assertThat(lookupElement.renderedText).isEqualTo("String value (from getValue())")
  }

  @Test
  fun testDataBindingCompletion_booleanGetterMethodConvertedToField() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public boolean isGood() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model.${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).doesNotContain("isGood")
    val lookupElement = fixture.lookupElements!!.first { it.lookupString == "good" }
    assertThat(lookupElement.renderedText).isEqualTo("boolean good (from isGood())")

    val psiField = lookupElement.psiElement as PsiField
    assertThat(psiField.modifierList!!.hasModifierProperty("public")).isTrue()
  }

  @Test
  fun testDataBindingCompletion_setterMethodNotConvertedToField() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public void setName(String name) { }
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model.${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()
    assertThat(fixture.lookupElementStrings).doesNotContain("name")

    val lookupElement = fixture.lookupElements!!.first { it.lookupString == "setName" }
    assertThat(lookupElement.renderedText).isEqualTo("void setName(String name)")
  }

  @Test
  fun testDataBindingCompletion_overriddenMethodsAreSuppressed() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        @Override
        public String toString() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model.${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    // We should only find one method with name "toString()", The "toString()" declared in [java.lang.Object] should be removed.
    fixture.completeBasic()
    assertThat(fixture.lookupElementStrings!!.filter { it == "toString" }).hasSize(1)
  }

  @Test
  fun testDataBindingCompletion_suggestImportedType() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        @Override
        public String toString() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{Mod${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{Model}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  @RunsInEdt
  fun testDataBindingCompletion_classWithSubstitutor() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public Data<String, Integer> data;
      }

      class Data<T, R> {
        public R setData(T data) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model.data.${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()
    val lookupElement = fixture.lookupElements!!.first { it.lookupString == "setData" }
    // Rendered version should have substituted both the return type and arg type
    assertThat(lookupElement.renderedText).isEqualTo("Integer setData(String data)")
  }

  @Test
  fun testDataBindingCompletion_genericType() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="list" type="java.util.List&lt;java.lang.String>" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:onClick="@{list::clea${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="list" type="java.util.List&lt;java.lang.String>" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:onClick="@{list::clear}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  @RunsInEdt
  fun testDataBindingCompletion_classWithBaseSubstitutor() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      class Data<X> {
        public X getData() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Data"/>
          <variable name="model" type="Data<Data<java.lang.String>>" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model.data.${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()
    val lookupElement = fixture.lookupElements!!.first { it.lookupString == "data" }
    // The generic "X" type should have been substituted with the proper type
    assertThat(lookupElement.renderedText).startsWith("String")
  }

  @Test
  @RunsInEdt
  fun testDataBindingCompletion_innerClass() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Data {
        public class InnerData {
          public static int x = 1;
        }
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Data"/>
          <variable name="model" type="Data" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{Data.${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    assertThat(fixture.completeBasic().any { it.lookupString == "InnerData" }).isTrue()
  }

  @Test
  @RunsInEdt
  fun testDataBindingCompletion_methodFromInnerClass() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Data {
        static public class Inner {
          public static void doNothing() {}
        }
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.Data"/>
          <variable name="model" type="Data" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{Data.Inner.${caret}}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    assertThat(fixture.completeBasic().any { it.lookupString == "doNothing" }).isTrue()
  }

  /**
   * Returns a lookup element's full rendered text, with a "$type $value$tail" format,
   * e.g. "String name (from getName())"
   *
   * The default lookup element string provided by the test fixture only includes the lookup name,
   * but in many tests we want to ensure that the additional details are correct as well
   */
  private val LookupElement.renderedText: String
    get() {
      val presentation = LookupElementPresentation()
      renderElement(presentation)

      return "${presentation.typeText} ${presentation.itemText}${presentation.tailText.orEmpty()}"
    }
}
