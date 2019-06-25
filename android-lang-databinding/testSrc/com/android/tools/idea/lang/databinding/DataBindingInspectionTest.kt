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
package com.android.tools.idea.lang.databinding

import com.android.SdkConstants
import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.ModuleDataBinding
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.facet.FacetManager
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests for inspections in data binding expressions.
 */
@RunWith(Parameterized::class)
class DataBindingInspectionTest(private val dataBindingMode: DataBindingMode) {
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
  fun testDataBindingInspection_unresolvedIdentifier() {
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
            android:onClick="@{<error descr="Cannot find identifier 'mosdel'">mosdel</error>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_resolvedVariable() {
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
            android:onClick="@{model}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_resolvedImportedClass() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        static final public String TEST = "test";
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
            android:onClick="@{Model.TEST}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_safeUnbox() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="boxedBoolean" type="Boolean"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{safeUnbox(boxedBoolean)}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_validPackageName() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{java.lang}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_invalidPackageName() {
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
            android:onClick="@{java.<error descr="Cannot find identifier 'name'">name</error>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_validField() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public int number;
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
            android:onClick="@{model.number}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_invalidField() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public int number;
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
            android:onClick="@{model.<error descr="Cannot find identifier 'numberhaha'">numberhaha</error>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_validMethod() {
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
            android:onClick="@{model.doSomething()}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_validMethodReference() {
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
            android:onClick="@{model::doSomething}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_invalidMethodReference() {
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
            android:onClick="@{model::<error descr="Cannot find identifier 'doBadthing'">doBadthing</error>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_invalidIdAsMethodParameter() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public void test(Model model) {}
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
            android:onClick="@{model.test(<error descr="Cannot find identifier 'modelY'">modelY</error>)}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_validIdAsMethodParameter() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public void test(Model model) {}
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
            android:onClick="@{model.test(model)}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_validLambdaParameterUsage() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public void test(View view) {}
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
            android:onClick="@{(view) -> model.test(view)}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_duplicateLambdaParameterNames() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public void test(View a, View b) {}
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
            android:onClick="@{(<error descr="Callback parameter 'a' is not unique">a</error>,
            <error descr="Callback parameter 'a' is not unique">a</error>,
            <error descr="Callback parameter 'b' is not unique">b</error>,
            <error descr="Callback parameter 'b' is not unique">b</error>, c) -> model.test(a, b)}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }
}
