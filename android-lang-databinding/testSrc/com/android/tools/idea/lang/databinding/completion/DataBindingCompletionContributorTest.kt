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
import com.android.tools.idea.databinding.ModuleDataBinding
import com.android.tools.idea.lang.databinding.getTestDataPath
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.JavaLookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementBuilder
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
class DataBindingCodeCompletionTest(private val dataBindingMode: DataBindingMode) {
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
    ModuleDataBinding.getInstance(androidFacet!!).setMode(dataBindingMode)
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
            android:onClick="@{model::do<caret>}"/>
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
            android:onClick="@{Model::d<caret>}"/>
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
            android:onClick="@{Model::d<caret>}"/>
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
            android:onClick="@{Model::<caret>}"/>
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
            android:onClick="@{Model::d<caret>}"/>
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
            android:onClick="@{Model::d<caret>}"/>
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
            android:onClick="@{model::d<caret>}"/>
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
            android:onClick="@{model::d<caret>}"/>
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
            android:onClick="@{() -> model.do<caret>}"/>
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
            android:onClick="@{() -> model.doSomething(<caret>)}"/>
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
            android:onClick="@{() -> member.do<caret>}"/>
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
            android:onClick="@{() -> member.doSomethingNoParameters()<caret>}"/>
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
            android:onClick="@{() -> model.fu<caret>}"/>
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
            android:onClick="@{model::do<caret>}"/>
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
            android:onClick="@{model::m<caret>}"/>
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
            android:onClick="@{model.fi<caret>}"/>
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
            android:onClick="@{model::do<caret>}"/>
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
            android:onClick="@{model::do<caret>}"/>
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
            android:text="@{model.fi<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()
    fixture.assertPreferredCompletionItems(0, "field", "fieldBase")
  }

  @Test
  @RunsInEdt
  fun testDataBindingCompletion_fieldsAreSuggestedWithType() {
    val psiClass = fixture.addClass("""
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
            android:text="@{model.fi<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val lookupElements = fixture.completeBasic()

    assertThat(lookupElements.size).isEqualTo(2)
    assertThat(lookupElements[0]).isInstanceOf(LookupElementBuilder::class.java)

    // Create expected suggestion
    val lookupElementBuilder = lookupElements[0] as LookupElementBuilder
    val psiField = psiClass.findFieldByName("field1", false)!!
    val expectedLookupElement = JavaLookupElementBuilder.forField(psiField).withTypeText("Int")

    assertThat(lookupElementBuilder.lookupString).isEqualTo(expectedLookupElement.lookupString)
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
            android:text="@{st<caret>}"/>
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
            android:text="@{Model::st<caret>}"/>
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
            android:text="@{By<caret>}"/>
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
        public String getValue() { return "dummy"; }
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
            android:onClick="@{model.<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()
    assertThat(fixture.lookupElementStrings).apply {
      contains("value")
      doesNotContain("getValue()")
    }
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
            android:onClick="@{model.<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()
    assertThat(fixture.lookupElementStrings).apply {
      contains("good")
      doesNotContain("isGood()")
    }
    val psiField = (fixture.lookupElements!!.first { it.lookupString == "good" }.psiElement) as PsiField
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
            android:onClick="@{model.<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()
    assertThat(fixture.lookupElementStrings).apply {
      contains("setName")
      doesNotContain("name")
    }
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
            android:onClick="@{model.<caret>}"/>
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
            android:onClick="@{Mod<caret>}"/>
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
            android:onClick="@{model.data.<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()
    val getDataLookupElement = fixture.lookupElements!!.first { it.lookupString == "setData" }
    val presentation = LookupElementPresentation()
    getDataLookupElement.renderElement(presentation)
    // presentation.typeText represents string for the return type, which should contain "Integer" instead of "R".
    assertThat(presentation.typeText).contains("Integer")
    // presentation.tailText represents string for parameters, which should contain "String" instead of "T".
    assertThat(presentation.tailText).contains("String")
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
            android:onClick="@{list::clea<caret>}"/>
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
}
