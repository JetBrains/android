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

  private val databindingPackage = dataBindingMode.packageName.removeSuffix(".") // Without trailing '.'
  private val databindingSrcPath = "src/${databindingPackage.replace('.', '/')}"

  @Before
  fun setUp() {
    fixture.testDataPath = "${getTestDataPath()}/projects/common"
    fixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML)

    // Add a fake "BindingAdapter" to this project so the tests resolve the dependency; this is
    // easier than finding a way to add a real dependency on the data binding library, which
    // usually requires Gradle plugin support.
    with(fixture.addFileToProject(
      "$databindingSrcPath/BindingAdapter.java",
      // language=java
      """
        package $databindingPackage;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;

        @Target(ElementType.METHOD)
        public @interface BindingAdapter {
          String[] value();
        }
      """.trimIndent())) {
      // The following line is needed or else we get an error for referencing a file out of bounds
      fixture.allowTreeAccessForFile(this.virtualFile)
    }

    with(fixture.addFileToProject(
      "$databindingSrcPath/BindingConversion.java",
      // language=java
      """
        package $databindingPackage;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;

        @Target({ElementType.METHOD})
        public @interface BindingConversion {
        }

      """.trimIndent())) {
      fixture.allowTreeAccessForFile(this.virtualFile)
    }

    with(fixture.addFileToProject(
      "$databindingSrcPath/InverseBindingAdapter.java",
      // language=java
      """
        package $databindingPackage;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;

        @Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
        public @interface InverseBindingAdapter {
            String attribute();
        }
      """.trimIndent())) {
      // The following line is needed or else we get an error for referencing a file out of bounds
      fixture.allowTreeAccessForFile(this.virtualFile)
    }

    with(fixture.addFileToProject(
      "$databindingSrcPath/InverseMethod.java",
      // language=java
      """
        package $databindingPackage;

        @Target(ElementType.METHOD)
        @Retention(RetentionPolicy.RUNTIME)
        public @interface InverseMethod {
          String value();
        }
      """.trimIndent())) {
      fixture.allowTreeAccessForFile(this.virtualFile)
    }

    val androidFacet = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)
    LayoutBindingModuleCache.getInstance(androidFacet!!).dataBindingMode = dataBindingMode
  }

  @Test
  fun testDataBindingInspection_resolvedToViewId() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <TextView
            android:id="@+id/view_id"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick2="@{viewId.getText()}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
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
  fun testDataBindingInspection_validMethodReferenceWithDot() {
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
            android:onClick="@{model.doSomething}"/>
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

  @Test
  fun testDataBindingInspection_attributeTypeMatched() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;

      import android.view.View;
      import ${dataBindingMode.bindingAdapter};

      public class Model {
        @BindingAdapter("sampleValue")
        public void bindSampleValue(View view, String s) {}
        public String getString() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            app:sampleValue="@{model.getString()}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_attributeTypeNotMatched() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;

      import android.view.View;
      import ${dataBindingMode.bindingAdapter};

      public class Model {
        @BindingAdapter("sampleValue")
        public void bindSampleValue(View view, String s) {}
        public int getString() {}
      }
      """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            app:sampleValue="@{<error descr="Cannot find a setter for <TextView app:sampleValue> that accepts parameter type 'int'">model.getString()</error>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_listenerAttributeTypeMatched() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;

      import android.view.View;
      import ${dataBindingMode.bindingAdapter};

      public class Model {
        @BindingAdapter("android:onClick2")
        public void bindSampleValue(View view, View.OnClickListener s) {}
        public String getString() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick2="@{() -> model.getString()}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_lambdaExpressionParameterCountNotMatched() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;

      import android.view.View;
      import ${dataBindingMode.bindingAdapter};

      public class Model {
        @BindingAdapter("android:onClick2")
        public void bindSampleValue(View view, View.OnClickListener s) {}
        public String getString() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick2="@{(<error descr="Unexpected parameter count. Expected 1, found 2.">v1, v2</error>) -> model.getString()}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_listenerAttributeTypeNotMatched() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;

      import android.view.View;
      import ${dataBindingMode.bindingAdapter};

      public class Model {
        @BindingAdapter("android:onClick2")
        public void bindSampleValue(View view, View.OnClickListener s) {}
        public String getString() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick2="@{<error descr="Cannot find a setter for <TextView android:onClick2> that accepts parameter type 'java.lang.String'">model.getString()</error>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_callExpressionWithinBracket() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;

      import android.view.View;

      public class Model {
        public String[] getStrings() {}
        public int calcIndex() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick2="@{model.strings[model.calcIndex()]"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_functionExpressionMatched() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;

      import android.view.View;
      import ${dataBindingMode.bindingAdapter};

      public class Model {
        @BindingAdapter("android:onClick2")
        public void bindSampleValue(View view, View.OnClickListener s) {}
        public void testClick(View view) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick2="@{model::testClick}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_functionExpressionMisMatched() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;

      import android.view.View;
      import ${dataBindingMode.bindingAdapter};

      public class Model {
        @BindingAdapter("android:onClick2")
        public void bindSampleValue(View view, View.OnClickListener s) {}
        public void testClick(String str) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick2="@{<error descr="Listener class 'android.view.View.OnClickListener' with method 'onClick' did not match signature of any method 'android:onClick2'">model::testClick</error>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_functionExpressionMatchedWithBindingConversion() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;

      import android.view.View;
      import ${dataBindingMode.bindingAdapter};
      import ${dataBindingMode.bindingConversion};

      public class Model {
        @BindingAdapter("android:onClick2")
        public void bindSampleValue(View view, View.OnClickListener s) {}
        @BindingConversion
        public static View.OnClickListener convertColorToOnClickListener(int num) {}
        public int getNumber() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick2="@{model.number}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_multipleSettersWithDifferentParameterType() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;

      import android.view.View;
      import ${dataBindingMode.bindingAdapter};

      public class Model {
        @BindingAdapter("android:text")
        public void bindSampleText(View view, String s) {}
        public int getNumber() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{model.number}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_genericClassMatchedWithBindingConversion() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;

      import android.view.View;
      import ${dataBindingMode.bindingAdapter};
      import ${dataBindingMode.bindingConversion};
      import java.util.ArrayList;

      public class GenericConverter {
        @BindingAdapter("android:text")
        public void bindSampleValue(View view, String str) {}
        @BindingConversion
        public static <T> String convertArrayList(ArrayList<T> values) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <variable name="list" type="java.util.ArrayList&lt;Integer>"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{list}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_resolvedArrayField() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class Model {
        public int[] array;
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{model.array.length}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_escapeCharactersWithinDoubleQuoteBindingExpression() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{`\`  &quot;World&quot; \u123f \215`}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_escapeCharactersWithinSingleQuoteBindingExpression() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text='@{"\`  \&quot;World\&quot; \u123f \215"}'/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_resourcesWithPackageNameAfterSlash() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{@id/android:list}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_resourcesWithTextType() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{@text/list}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_chainedTernaryOperation() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
    <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
      <data>
        <variable name="cond1" type="boolean"/>
        <variable name="cond2" type="boolean"/>
      </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{cond1 ? cond2 ? `1` : `2` : `3`}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_fieldWithoutSetterNotInvertible() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;
      import android.view.View;

      public class Model {
        public int getNumber() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@={<error descr="The expression 'model.number' cannot be inverted, so it cannot be used in a two-way binding">model.number</error>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_methodCallNotInvertible() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;
      import android.view.View;

      public class Model {
        public int calculateString() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@={<error descr="The expression 'model.calculateString()' cannot be inverted, so it cannot be used in a two-way binding">model.calculateString()</error>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_methodCallNotInvertibleWithoutInverseMethod() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;
      import android.view.View;

      public class Model {
        public String calculateString(int v) {}
        public int getValue() {}
        public void setValue(int v) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@={<error descr="The expression 'model.calculateString(model.value)' cannot be inverted, so it cannot be used in a two-way binding">model.calculateString(model.value)</error>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_methodCallInvertibleWhenAnnotatingWithInverseMethod() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;
      import android.view.View;
      import ${dataBindingMode.inverseMethod};

      public class Model {
        public CharSequence calculateString(int v) {}
        public int getValue() {}
        public void setValue(int v) {}
        @InverseMethod("calculateString")
        public static int calculateInt(CharSequence s) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@={model.calculateString(model.value)}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_methodCallInvertibleWhenAnnotatedByInverseMethod() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;
      import android.view.View;
      import ${dataBindingMode.inverseMethod};

      public class Model {
        @InverseMethod("calculateInt")
        public CharSequence calculateString(int v) {}
        public int getValue() {}
        public void setValue(int v) {}
        public static int calculateInt(CharSequence s) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@={model.calculateString(model.value)}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_methodCallWithMultipleParametersInvertibleWhenAnnotatedByInverseMethod() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;
      import android.view.View;
      import ${dataBindingMode.inverseMethod};

      public class Model {
        @InverseMethod("calculateInt")
        public CharSequence calculateString(Model model, int v) {}
        public int getValue() {}
        public void setValue(int v) {}
        public static int calculateInt(Model model, CharSequence s) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/viewId"
            android:text="@={model.calculateString(model, model.value)}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_bindingExpressionGetterNotMatched() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;
      import android.view.View;

      public class Model {
        public int getNumber() {}
        public void setNumber(int x) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@={<error descr="Cannot find a getter for <TextView android:text> that accepts parameter type 'int'">model.number</error>}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspectionInKotlin_bindingExpressionMatchedWithInverseBindingAdapter() {
    fixture.addFileToProject("$databindingSrcPath/Model.kt",
      // language=kotlin
      """
      package test.langdb
      import android.view.View
      import ${dataBindingMode.bindingAdapter}
      import ${dataBindingMode.inverseBindingAdapter}

      class Model {
        @BindingAdapter("app:text")
        fun setViewText(editText: View, str: String) {}
        @InverseBindingAdapter(attribute = "app:text")
        fun getViewText(editText: View): String {}
        var value: String
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <import type="test.langdb.Model"/>
          <variable name="model" type="Model" />
        </data>
        <TextView
            android:id="@+id/viewId"
            app:text="@={model.value}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }
}
