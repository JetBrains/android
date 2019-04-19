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
import com.intellij.facet.FacetManager
import com.intellij.psi.PsiSubstitutor
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

      public class ModelWithBindableMethodsJava {
        public void doSomething(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
          <variable name="member" type="ModelWithBindableMethodsJava" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{member::do<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
          <variable name="member" type="ModelWithBindableMethodsJava" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{member::doSomething}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_caretInStaticMethodReference() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class ModelWithBindableMethodsJava {
        public static void doSomethingStatic(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{ModelWithBindableMethodsJava::d<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{ModelWithBindableMethodsJava::doSomethingStatic}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_excludeNonPublicMethods() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class ModelWithBindableMethodsJava {
        private static void doSomethingStatic(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{ModelWithBindableMethodsJava::d<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{ModelWithBindableMethodsJava::d}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_excludeConstructors() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class ModelWithBindableMethodsJava {}
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{ModelWithBindableMethodsJava::<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{ModelWithBindableMethodsJava::}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_undefinedType() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{ModelWithBindableMethodsJava::d<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{ModelWithBindableMethodsJava::d}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_completeInstanceMethodInStaticContext() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class ModelWithBindableMethodsJava {
        public void doSomethingStatic(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{ModelWithBindableMethodsJava::d<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{ModelWithBindableMethodsJava::d}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_completeStaticMethodInNonStaticContext() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class ModelWithBindableMethodsJava {
        public static void doSomethingStatic(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
          <variable name="member" type="ModelWithBindableMethodsJava" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{member::d<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
          <variable name="member" type="ModelWithBindableMethodsJava" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{member::d}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_repeatedInvocationsIncludeAllSuggestions() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class ModelWithBindableMethodsJava {
        public static void doSomethingStatic(View view) {}
        private static void doPrivateStatic(View view) {}
        private void doPrivate(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
          <variable name="member" type="ModelWithBindableMethodsJava" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{member::d<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.complete(CompletionType.BASIC, 2)
    fixture.assertPreferredCompletionItems(0, "doPrivate", "doPrivateStatic", "doSomethingStatic")
  }

  @Test
  fun testDataBindingCompletion_testListenerBindingExpression() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class ModelWithBindableMethodsJava {
        public void doSomething(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
          <variable name="member" type="ModelWithBindableMethodsJava" />
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
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
          <variable name="member" type="ModelWithBindableMethodsJava" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{() -> member.doSomething()}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_multipleLookupItems() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class ModelWithBindableMethodsJava {
        public static void function_a(View view) {}
        public static void function_b(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
          <variable name="member" type="ModelWithBindableMethodsJava" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{() -> member.fu<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.complete(CompletionType.BASIC, 2)
    fixture.assertPreferredCompletionItems(0, "function_a()", "function_b()")
  }

  @Test
  fun testDataBindingCompletion_methodsWithSameName() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class ModelWithBindableMethodsJava {
        public void doSomething(View view) {}
        public void doSomething(View view, int a) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
          <variable name="member" type="ModelWithBindableMethodsJava" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{member::do<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
          <variable name="member" type="ModelWithBindableMethodsJava" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{member::doSomething}"/>
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
          <variable name="member" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{member::m<caret>}"/>
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
            android:onClick="@{member::methodFromBaseClass}"/>
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
          <variable name="member" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{member.fi<caret>}"/>
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
            android:onClick="@{member.fieldFromBaseClass}"/>
      </layout>
    """.trimIndent())
  }

  @Test
  @RunsInEdt
  fun testDataBindingCompletion_methodPresentation() {
    val psiClass = fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class ModelWithBindableMethodsJava {
        public void doSomething(View view) {}
        public void doSomething2(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
          <variable name="member" type="ModelWithBindableMethodsJava" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{member::do<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val lookupElements = fixture.completeBasic()

    assertThat(lookupElements.size).isEqualTo(2)
    assertThat(lookupElements[0]).isInstanceOf(LookupElementBuilder::class.java)

    // create expected suggestion
    val lookupElementBuilder = lookupElements[0] as LookupElementBuilder
    val psiMethod = psiClass.findMethodsByName("doSomething", false)[0]
    val expectedLookupElement = JavaLookupElementBuilder.forMethod(
      psiMethod, "doSomething", PsiSubstitutor.EMPTY, psiClass)

    assertThat(lookupElementBuilder).isEqualTo(expectedLookupElement)
  }

  @Test
  @RunsInEdt
  fun testDataBindingCompletion_methodFromBaseClass() {
    val psiClass = fixture.addClass("""
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
          <variable name="member" type="ModelWithBindableMethods" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{member::do<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val lookupElements = fixture.completeBasic()

    assertThat(lookupElements.size).isEqualTo(2)

    assertThat(lookupElements[0]).isInstanceOf(LookupElementBuilder::class.java)
    val lookupElementBuilder = lookupElements[0] as LookupElementBuilder
    val psiMethod = psiClass.findMethodsByName("doSomething", false)[0]
    val expectedLookupElement = JavaLookupElementBuilder.forMethod(
      psiMethod, "doSomething", PsiSubstitutor.EMPTY, psiClass)
    assertThat(lookupElementBuilder).isEqualTo(expectedLookupElement)

    assertThat(lookupElements[1]).isInstanceOf(LookupElementBuilder::class.java)
    val baseLookupElementBuilder = lookupElements[1] as LookupElementBuilder
    val basePsiMethod = psiClass.findMethodsByName("doSomethingBase", true)[0]
    val baseExpectedLookupElement = JavaLookupElementBuilder.forMethod(
      basePsiMethod, "doSomethingBase", PsiSubstitutor.EMPTY, null)
    assertThat(baseLookupElementBuilder).isEqualTo(baseExpectedLookupElement)
  }

  @Test
  @RunsInEdt
  fun testDataBindingCompletion_fieldFromBaseClass() {
    val psiClass = fixture.addClass("""
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
          <variable name="member" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{member.fi<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val lookupElements = fixture.completeBasic()

    assertThat(lookupElements.size).isEqualTo(2)

    assertThat(lookupElements[0]).isInstanceOf(LookupElementBuilder::class.java)
    val lookupElementBuilder = lookupElements[0] as LookupElementBuilder
    val psiField = psiClass.findFieldByName("field", false)!!
    val expectedLookupElement = JavaLookupElementBuilder.forField(psiField).withTypeText("Int")
    assertThat(lookupElementBuilder).isEqualTo(expectedLookupElement)

    assertThat(lookupElements[1]).isInstanceOf(LookupElementBuilder::class.java)
    val baseLookupElementBuilder = lookupElements[1] as LookupElementBuilder
    val basePsiField = psiClass.findFieldByName("fieldBase", true)!!
    val baseExpectedLookupElement =
      JavaLookupElementBuilder.forField(basePsiField, "fieldBase", null).withTypeText("Int")
    assertThat(baseLookupElementBuilder).isEqualTo(baseExpectedLookupElement)
  }

  @Test
  @RunsInEdt
  fun testDataBindingCompletion_fieldPresentation() {
    val psiClass = fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class ModelWithBindableMethodsJava {
        public int field1;
        public String field2;
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
          <variable name="member" type="ModelWithBindableMethodsJava" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{member.fi<caret>}"/>
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

    assertThat(lookupElementBuilder).isEqualTo(expectedLookupElement)
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

      public class ModelWithBindableMethodsJava {
        public static String strUpper(View view) {
          return "a";
        }
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{ModelWithBindableMethodsJava::st<caret>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{ModelWithBindableMethodsJava::strUpper}"/>
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
}
