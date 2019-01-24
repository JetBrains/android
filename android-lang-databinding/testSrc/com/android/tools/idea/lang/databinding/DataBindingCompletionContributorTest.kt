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
import com.android.tools.idea.databinding.ModuleDataBinding
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.facet.FacetManager
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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

  @get:Rule
  val projectRule = AndroidProjectRule.withSdk()

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
    fixture.assertPreferredCompletionItems(0, "doPrivate",  "doPrivateStatic", "doSomethingStatic")
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
            android:onClick="@{() -> member.d<caret>}"/>
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
}