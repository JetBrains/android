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
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.lang.databinding.getTestDataPath
import com.android.tools.idea.lang.databinding.model.ModelClassResolvable
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
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

    // Add a fake "BindingAdapter" to this project so the tests resolve the dependency; this is
    // easier than finding a way to add a real dependency on the data binding library, which
    // usually requires Gradle plugin support.
    val databindingPackage = mode.packageName.removeSuffix(".") // Without trailing '.'
    val databindingSrcPath = "src/${databindingPackage.replace('.', '/')}"

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
      "$databindingSrcPath/InverseBindingAdapter.java",
      // language=java
      """
        package $databindingPackage;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;

        @Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
        public @interface InverseBindingAdapter {
            String attribute();
            String event() default "";
        }
      """.trimIndent())) {
      // The following line is needed or else we get an error for referencing a file out of bounds
      fixture.allowTreeAccessForFile(this.virtualFile)
    }

    with(fixture.addFileToProject(
      "$databindingSrcPath/InverseBindingMethods.java",
      // language=java
      """
        package $databindingPackage;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;

        @Target(ElementType.TYPE)
        public @interface InverseBindingMethods {
            InverseBindingMethod[] value();
        }
      """.trimIndent())) {
      // The following line is needed or else we get an error for referencing a file out of bounds
      fixture.allowTreeAccessForFile(this.virtualFile)
    }

    with(fixture.addFileToProject(
      "src/${databindingPackage.replace('.', '/')}/InverseBindingMethod.java",
      // language=java
      """
        package $databindingPackage;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;

        @Target(ElementType.ANNOTATION_TYPE)
        public @interface InverseBindingMethod {
            Class type();
            String attribute();
            String event() default "";
            String method() default "";
        }
      """.trimIndent())) {
      // The following line is needed or else we get an error for referencing a file out of bounds
      fixture.allowTreeAccessForFile(this.virtualFile)
    }

    with(fixture.addFileToProject(
      "src/${databindingPackage.replace('.', '/')}/ObservableField.java",
      // language=java
      """
        package $databindingPackage;

        public class ObservableField<T> {
            public T get() {}
        }
      """.trimIndent())) {
      // The following line is needed or else we get an error for referencing a file out of bounds
      fixture.allowTreeAccessForFile(this.virtualFile)
    }

    val androidFacet = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)
    LayoutBindingModuleCache.getInstance(androidFacet!!).dataBindingMode = mode
  }

  @Test
  fun dbIdReferencesXmlVariable() {
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
        <TextView android:text="@{mo${caret}del.strValue}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val element = fixture.elementAtCaret as XmlTag
    assertThat(element.name).isEqualTo("variable")
    assertThat(element.attributes.find { attr -> attr.name == "name" && attr.value == "model" }).isNotNull()
  }

  @Test
  fun dbIdReferencesXmlVariableFromOtherLayoutFiles() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;
      public class Model {
        String getStrValue() {}
      }
    """.trimIndent())

    fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:text="text"/>
      </layout>
    """.trimIndent())

    val landFile = fixture.addFileToProject("res/layout-land/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView android:text="@{mo${caret}del.strValue}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(landFile.virtualFile)

    val element = fixture.elementAtCaret as XmlTag
    assertThat(element.name).isEqualTo("variable")
    assertThat(element.attributes.find { attr -> attr.name == "name" && attr.value == "model" }).isNotNull()
  }

  @Test
  fun dbFieldReferencesClassField() {
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
        <TextView android:text="@{model.str${caret}Value}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val javaStrValue = fixture.findClass("test.langdb.Model").findFieldByName("strValue", false)!!
    val xmlStrValue = fixture.getReferenceAtCaretPosition()!!

    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(xmlStrValue.isReferenceTo(javaStrValue)).isTrue()
    assertThat(xmlStrValue.resolve()).isEqualTo(javaStrValue)
  }

  @Test
  fun dbFieldReferencesMapValue() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="map" type="java.util.Map<Integer, String>" />
        </data>
        <TextView android:text="@{map.str${caret}Value}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val methodInSourceCode = fixture.findClass("java.util.Map").findMethodsByName("get", false)[0]
    val referencedMethod = fixture.getReferenceAtCaretPosition()!!

    // Generic types in "java.util.Map" should be resolved correctly.
    assertThat((referencedMethod as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("java.lang.String")
    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(referencedMethod.isReferenceTo(methodInSourceCode)).isTrue()
    assertThat(referencedMethod.resolve()).isEqualTo(methodInSourceCode)
  }

  @Test
  fun dbFieldReferencesHashMapValue() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="map" type="java.util.HashMap<Integer, String>" />
        </data>
        <TextView android:text="@{map.str${caret}Value}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val methodInSourceCode = fixture.findClass("java.util.HashMap").findMethodsByName("get", false)[0]
    val referencedMethod = fixture.getReferenceAtCaretPosition()!!

    // Generic types in "java.util.HashMap" should be resolved correctly.
    assertThat((referencedMethod as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("java.lang.String")
    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(referencedMethod.isReferenceTo(methodInSourceCode)).isTrue()
    assertThat(referencedMethod.resolve()).isEqualTo(methodInSourceCode)
  }

  @Test
  fun dbFieldReferencesMapField() {
    fixture.addClass("""
      package test.langdb;

      public class MyMap extends HashMap<String, String>{
        public String myField
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="map" type="test.langdb.MyMap" />
        </data>
        <TextView android:text="@{map.myFiel${caret}d"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val methodInSourceCode = fixture.findClass("test.langdb.MyMap").findFieldByName("myField", false)!!
    val referencedMethod = fixture.getReferenceAtCaretPosition()!!

    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(referencedMethod.isReferenceTo(methodInSourceCode)).isTrue()
    assertThat(referencedMethod.resolve()).isEqualTo(methodInSourceCode)
  }

  @Test
  fun dbMethodReferencesClassMethod() {
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
        <TextView android:text="@{model.do${caret}Something()}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val javaDoSomething = fixture.findClass("test.langdb.Model").findMethodsByName("doSomething")[0].sourceElement!!
    val xmlDoSomething = fixture.getReferenceAtCaretPosition()!!

    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(xmlDoSomething.isReferenceTo(javaDoSomething)).isTrue()
    assertThat(xmlDoSomething.resolve()).isEqualTo(javaDoSomething)
  }

  @Test
  fun dbMethodReferencesClassMethodWithVarArgs() {
    fixture.addClass("""
      package test.langdb;

      public class Model {
        public void doSomething(Object... args) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:text="@{model.do${caret}Something(model, model, model)}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val javaDoSomething = fixture.findClass("test.langdb.Model").findMethodsByName("doSomething")[0].sourceElement!!
    val xmlDoSomething = fixture.getReferenceAtCaretPosition()!!

    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(xmlDoSomething.isReferenceTo(javaDoSomething)).isTrue()
    assertThat(xmlDoSomething.resolve()).isEqualTo(javaDoSomething)
  }

  @Test
  fun dbPropertyReferencesClassMethod() {
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
        <TextView android:text="@{mod${caret}el.str${caret}Value}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val javaStrValue = fixture.findClass("test.langdb.Model").findMethodsByName("getStrValue")[0].sourceElement!!
    val xmlStrValue = fixture.getReferenceAtCaretPosition()!!

    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(xmlStrValue.isReferenceTo(javaStrValue)).isTrue()
    assertThat(xmlStrValue.resolve()).isEqualTo(javaStrValue)
  }

  @Test
  fun dbFieldCanNotReferenceStaticGetter() {
    fixture.addClass("""
      package test.langdb;

      public class Model {
        public static String getStrValue() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:text="@{model.str${caret}Value}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    // A static method can not be the getter for a data binding field.
    assertThat(fixture.getReferenceAtCaretPosition()).isNull()
  }

  @Test
  fun dbFieldCanNotReferenceStaticSetter() {
    fixture.addClass("""
      package test.langdb;

      public class Model {
        public String getStrValue() {}
        static public void setStrValue(String value) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:text="@={model.str${caret}Value}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    // A static method can not be the setter for a data binding field.
    assertThat((fixture.getReferenceAtCaretPosition()!!.resolve() as PsiMethod).name).isNotEqualTo("setStrValue")
  }

  @Test
  fun dbIdReferencesXmlImport() {
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
        <TextView android:onClick="@{Mo${caret}del::handleClick}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val element = fixture.elementAtCaret as XmlTag
    assertThat(element.name).isEqualTo("import")
    assertThat(element.attributes.find { attr -> attr.name == "type" && attr.value == "test.langdb.Model" }).isNotNull()
  }

  @Test
  fun dbStaticMethodReferenceReferencesClassMethod() {
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
        <TextView android:onClick="@{Model::handle${caret}Click}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val javaHandleClick = fixture.findClass("test.langdb.Model").findMethodsByName("handleClick")[0].sourceElement!!
    val xmlHandleClick = fixture.getReferenceAtCaretPosition()!!

    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(xmlHandleClick.isReferenceTo(javaHandleClick)).isTrue()
    assertThat(xmlHandleClick.resolve()).isEqualTo(javaHandleClick)
  }

  @Test
  fun dbClassCanNotReferenceInstanceMethodWithDoubleColon() {
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
          <import type="test.langdb.Model" />
        </data>
        <TextView android:onClick="@{Model::handle${caret}Click}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    assertThat(fixture.getReferenceAtCaretPosition()).isNull()
  }

  @Test
  fun dbClassCanNotReferenceInstanceMethodWithDot() {
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
          <import type="test.langdb.Model" />
        </data>
        <TextView android:onClick="@{Model.handle${caret}Click}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    assertThat(fixture.getReferenceAtCaretPosition()).isNull()
  }

  @Test
  fun dbInstanceMethodReferenceReferencesClassMethod() {
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
        <TextView android:onClick="@{clickHandler::handle${caret}Click}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val javaHandleClick = fixture.findClass("test.langdb.ClickHandler").findMethodsByName("handleClick")[0].sourceElement!!
    val xmlHandleClick = fixture.getReferenceAtCaretPosition()!!

    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(xmlHandleClick.isReferenceTo(javaHandleClick)).isTrue()
    assertThat(xmlHandleClick.resolve()).isEqualTo(javaHandleClick)
  }

  @Test
  fun dbMethodDotReferenceReferencesClassMethod() {
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
        <TextView android:onClick="@{clickHandler.handle${caret}Click}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val javaHandleClick = fixture.findClass("test.langdb.ClickHandler").findMethodsByName("handleClick")[0].sourceElement!!
    val xmlHandleClick = fixture.getReferenceAtCaretPosition()!!

    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(xmlHandleClick.isReferenceTo(javaHandleClick)).isTrue()
    assertThat(xmlHandleClick.resolve()).isEqualTo(javaHandleClick)
  }

  @Test
  fun dbMethodDotReferenceReferencesClassMethodWithoutArgument() {
    fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class ClickHandler {
        public void handleClick() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="clickHandler" type="test.langdb.ClickHandler" />
        </data>
        <TextView android:onClick="@{clickHandler.handle${caret}Click}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val javaHandleClick = fixture.findClass("test.langdb.ClickHandler").findMethodsByName("handleClick")[0].sourceElement!!
    val xmlHandleClick = fixture.getReferenceAtCaretPosition()!!

    // Make sure xmlHandleClick references a method reference instead of a method call.
    assertThat((xmlHandleClick as ModelClassResolvable).resolvedType).isNull()
    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(xmlHandleClick.isReferenceTo(javaHandleClick)).isTrue()
    assertThat(xmlHandleClick.resolve()).isEqualTo(javaHandleClick)
  }

  @Test
  fun dbMethodCallReferencesClassMethod() {
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
        <TextView android:onClick="@{(v) -> model.handle${caret}Click(v)}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val javaHandleClick = fixture.findClass("test.langdb.Model").findMethodsByName("handleClick")[0].sourceElement!!
    val xmlHandleClick = fixture.getReferenceAtCaretPosition()!!

    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(xmlHandleClick.isReferenceTo(javaHandleClick)).isTrue()
    assertThat(xmlHandleClick.resolve()).isEqualTo(javaHandleClick)
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
        <TextView android:text="@{test.langdb.Mo${caret}del.NAME}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val javaModel = fixture.findClass("test.langdb.Model")
    val xmlModel = fixture.getReferenceAtCaretPosition()!!

    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(xmlModel.isReferenceTo(javaModel)).isTrue()
    assertThat(xmlModel.resolve()).isEqualTo(javaModel)
  }

  @Test
  fun dbIdReferenceInLambdaExpression() {
    fixture.addClass("""
      package test.langdb;

      public class Model {
        public String doSomething() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:onClick="@{() -> mode${caret}l.doSomething()}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val element = fixture.elementAtCaret as XmlTag
    assertThat(element.name).isEqualTo("variable")
    assertThat(element.attributes.find { attr -> attr.name == "name" && attr.value == "model" }).isNotNull()
  }

  @Test
  fun dbMethodReferenceInLambdaExpression() {
    fixture.addClass("""
      package test.langdb;

      public class Model {
        public String doSomething() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:onClick="@{() -> model.doSomethin${caret}g}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val javaDoSomething = fixture.findClass("test.langdb.Model").findMethodsByName("doSomething")[0].sourceElement!!
    val xmlDoSomething = fixture.getReferenceAtCaretPosition()!!

    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(xmlDoSomething.isReferenceTo(javaDoSomething)).isTrue()
    assertThat(xmlDoSomething.resolve()).isEqualTo(javaDoSomething)
  }

  @Test
  fun dbIdReferenceAsMethodParameter() {
    fixture.addClass("""
      package test.langdb;

      public class Model {
        public String same(String str) {}
        public String getValue() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:text="@{model.same(mo${caret}del.getValue())}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val element = fixture.elementAtCaret as XmlTag
    assertThat(element.name).isEqualTo("variable")
    assertThat(element.attributes.find { attr -> attr.name == "name" && attr.value == "model" }).isNotNull()
  }

  @Test
  fun dbMethodReferenceAsMethodParameter() {
    fixture.addClass("""
      package test.langdb;

      public class Model {
        public String same(String str) {}
        public String getValue() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:text="@{model.same(model.get${caret}Value())}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val javaDoSomething = fixture.findClass("test.langdb.Model").findMethodsByName("getValue")[0].sourceElement!!
    val xmlDoSomething = fixture.getReferenceAtCaretPosition()!!

    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(xmlDoSomething.isReferenceTo(javaDoSomething)).isTrue()
    assertThat(xmlDoSomething.resolve()).isEqualTo(javaDoSomething)
  }

  @Test
  fun dbLiteralReferencesPrimitiveType() {
    fixture.addClass("""
      package test.langdb;

      public class Model {
        public String calculate(String value) {}
        public int calculate(int value) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:text="@{model.calcula${caret}te(15)}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("int")
  }

  @Test
  fun dbLiteralReferencesStringType() {
    fixture.addClass("""
      package test.langdb;

      public class Model {
        public String calculate(String value) {}
        public int calculate(int value) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:text="@{model.calcula${caret}te(`15`)}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("java.lang.String")
  }

  @Test
  fun dbMethodReferencesFromStringLiteral() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView android:text="@{`string`.subst${caret}ring(5)"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("java.lang.String")
  }

  @Test
  fun dbLambdaParametersReferencesFunctionalInterface() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;

      import android.view.View;
      import ${mode.bindingAdapter};

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
            android:onClick2="@{(${caret}) -> model.getString()}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference.resolve() as PsiMethod).name).isEqualTo("onClick")
  }

  @Test
  fun dbLambdaExprShouldNotReferenceFunctionalInterface() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;

      import android.view.View;
      import ${mode.bindingAdapter};

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
            android:onClick2="@{() -> unresolvable_${caret}_code}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    assertThat(fixture.getReferenceAtCaretPosition()).isNull()
  }

  @Test
  fun dbAttributeReferencesInverseBindingAdapter() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;

      import android.view.View;
      import ${mode.bindingAdapter};
      import ${mode.inverseBindingAdapter};

      public class Model {
        @BindingAdapter("text2")
        public void bindSampleValue(View view, String s) {}
        @InverseBindingAdapter(attribute = "text2")
        public static String getSampleValue(View view) {}
        public String getString()
        public void setString(String s)
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
            app:t${caret}ext2="@={model.string}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as PsiMultiReference).references.any { (it.resolve() as? PsiMethod)?.name == "getSampleValue" }).isTrue()
  }

  @Test
  fun dbAttributeReferencesInverseBindingMethodWithDefaultGetter() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;

      import android.view.View;
      import android.widget.TextView;
      import ${mode.bindingAdapter};
      import ${mode.inverseBindingMethod};
      import ${mode.inverseBindingMethods};

      @InverseBindingMethods({@InverseBindingMethod(
        type = android.widget.TextView.class,
        attribute = "android:text")})
      public class Model {
        @BindingAdapter("android:text")
        public void bindSampleValue(TextView view, String s) {}
        public String setString(String s) {}
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
            android:tex${caret}t="@={model.string}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as PsiMultiReference).references.any { (it.resolve() as? PsiMethod)?.name == "getText" }).isTrue()
  }

  @Test
  fun dbAttributeReferencesInverseBindingMethodWithDefaultBooleanGetter() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;

      import android.view.View;
      import android.widget.ToggleButton;
      import ${mode.bindingAdapter};
      import ${mode.inverseBindingMethod};
      import ${mode.inverseBindingMethods};

      @InverseBindingMethods({
              @InverseBindingMethod(type = ToggleButton.class, attribute = "android:checked"),
      })
      public class Model {
        @BindingAdapter("android:checked")
        public void bindSampleValue(View view, boolean s) {}
        public String setBoolean(String s) {}
        public String getBoolean() {}
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
        <ToggleButton
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:checke${caret}d="@={model.boolean}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as PsiMultiReference).references.any { (it.resolve() as? PsiMethod)?.name == "isChecked" }).isTrue()
  }

  @Test
  fun dbAttributeReferencesInverseBindingMethodWithCustomGetter() {
    fixture.addClass(
      // language=java
      """
      package test.langdb;

      import android.view.View;
      import android.widget.TextView;
      import ${mode.bindingAdapter};
      import ${mode.inverseBindingMethod};
      import ${mode.inverseBindingMethods};

      @InverseBindingMethods({@InverseBindingMethod(
        type = android.widget.TextView.class,
        attribute = "android:text2",
        method = "getText")})
      public class Model {
        @BindingAdapter("android:text2")
        public void bindSampleValue(TextView view, String s) {}
        public String setString(String s) {}
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
            android:tex${caret}t2="@={model.string}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as PsiMultiReference).references.any { (it.resolve() as? PsiMethod)?.name == "getText" }).isTrue()
  }

  @Test
  fun dbAttributeReferencesViewGetter() {
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
            app:t${caret}ext="@={model.string}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as PsiMultiReference).references.any { (it.resolve() as? PsiMethod)?.name == "getText" }).isTrue()
  }

  @Test
  fun dbSimpleNameReferencesViewId() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <TextView
            android:id="@+id/view_id"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick2="@{vie${caret}wId.getText()}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    val reference = fixture.getReferenceAtCaretPosition()!!
    val xmlAttribute = reference.resolve() as XmlAttribute
    assertThat(xmlAttribute.name).isEqualTo("android:id")
    assertThat(xmlAttribute.value).isEqualTo("@+id/view_id")
  }

  @Test
  fun dbResolveMethodsFromInterfaceReturnType() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <EditText
            android:id="@+id/view_id"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{viewId.getText().le${caret}ngth()}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    val reference = fixture.getReferenceAtCaretPosition()!!
    // view_id.getText() should return interface Editable that extends CharSequence and inherits its method length().
    assertThat((reference.resolve() as PsiMethod).name).isEqualTo("length")
  }

  @Test
  fun dbResolveContextSimpleNameToContextInstance() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <EditText
            android:id="@+id/view_id"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{con${caret}text.getText()}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("android.content.Context")
  }

  @Test
  fun dbResolveContextSimpleNameToVariable() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <data>
          <variable name="context" type="String" />
        </data>
        <EditText
            android:id="@+id/view_id"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{con${caret}text}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("java.lang.String")
  }

  @Test
  fun dbStaticFieldReferencesInstanceMethod() {
    fixture.addClass("""
      package test.langdb;

      public class Model {
        public static Model staticModel;
        public Model model;
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:text="@{model.staticModel.mod${caret}el}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val javaStrValue = fixture.findClass("test.langdb.Model").findFieldByName("model", false)!!
    val xmlStrValue = fixture.getReferenceAtCaretPosition()!!

    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(xmlStrValue.isReferenceTo(javaStrValue)).isTrue()
    assertThat(xmlStrValue.resolve()).isEqualTo(javaStrValue)
  }

  @Test
  fun dbFieldReferencesCustomMapValue() {
    fixture.addClass("""
      package test.langdb;
      import java.util.Map;

      public class MyMap<K, V> extends Map<K, V> {
      }
    """.trimIndent())
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="map" type="test.langdb.MyMap<Integer, String>" />
        </data>
        <TextView android:text="@{map.str${caret}Value}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val methodInSourceCode = fixture.findClass("java.util.Map").findMethodsByName("get", false)[0]
    val referencedMethod = fixture.getReferenceAtCaretPosition()!!

    // Generic types in "java.util.MyMap" should be resolved correctly.
    assertThat((referencedMethod as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("java.lang.String")
    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(referencedMethod.isReferenceTo(methodInSourceCode)).isTrue()
    assertThat(referencedMethod.resolve()).isEqualTo(methodInSourceCode)
  }

  @Test
  fun dbReferencesMethodBestMatchedWithArguments() {
    fixture.addClass("""
      package test.langdb;

      public class Model {
        public Object confusingFunction(Object object) {}
        public String confusingFunction(String str) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:text="@{model.confu${caret}singFunction(`string`)}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("java.lang.String")
  }

  @Test
  fun dbReferencesPluralsResourceWithCountParameter() {
    fixture.addFileToProject("res/values/strings.xml", """
      <resources>
        <plurals name="orange">
            <item quantity="one">orange</item>
            <item quantity="other">oranges</item>
        </plurals>
      </resources>
    """.trimIndent())
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView android:text="@{@plurals/oran${caret}ge(1)}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("java.lang.String")
  }

  @Test
  fun dbReferencesStringPluralsResourceWithoutParameter() {
    fixture.addFileToProject("res/values/strings.xml", """
      <resources>
        <plurals name="orange">
            <item quantity="one">orange</item>
            <item quantity="other">oranges</item>
        </plurals>
      </resources>
    """.trimIndent())
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView android:text="@{@plurals/oran${caret}ge}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("int")
  }

  @Test
  fun dbReferencesStringResource() {
    fixture.addFileToProject("res/values/strings.xml", """
      <resources>
        <string name="nameWithTitle">title</string>
      </resources>
    """.trimIndent())
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView android:text="@{@string/nameWi${caret}thTitle}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("java.lang.String")
  }

  @Test
  fun dbReferencesFractionResource() {
    fixture.addFileToProject("res/values/fractions.xml", """
      <resources>
        <fraction name="myFraction">150%</fraction>
      </resources>
    """.trimIndent())
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView android:text="@{@fraction/myFrac${caret}tion}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("float")
  }

  @Test
  fun dbReferencesTextResource() {
    fixture.addFileToProject("res/values/strings.xml", """
      <resources>
        <string name="zero">there are <b>zero</b></string>
      </resources>
    """.trimIndent())
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView android:text="@{@text/ze${caret}ro}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("java.lang.CharSequence")
  }

  @Test
  fun dbReferencesIdResource() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <TextView
            android:id="@+id/view_id"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{@id/view${caret}_id}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("int")
  }

  @Test
  fun dbReferencesDrawableResource() {
    fixture.addFileToProject("res/drawable/pic.png", "0000000")
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
        <ImageView
        android:layout_height="wrap_content"
        android:layout_width="wrap_content"
        android:src="@{@drawable/p${caret}ic}" />
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("android.graphics.drawable.Drawable")
  }

  @Test
  fun dbFieldReferencesGetterAndSetter() {
    fixture.addClass("""
      package test.langdb;

      public class Model {
        public String getValue() {}
        public void setValue(String value) {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:text="@={model.val${caret}ue}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val references = (fixture.getReferenceAtCaretPosition() as PsiMultiReference).references
    assertThat(references.any { (it.resolve() as PsiMethod).name == "getValue" }).isTrue()
    assertThat(references.any {
      (it.resolve() as PsiMethod).name == "setValue" && (it as ModelClassResolvable).resolvedType == null
    }).isTrue()
  }

  @Test
  fun dbReferencesMethodBestMatchedWithObservableArguments() {
    fixture.addClass("""
      package test.langdb;
      import ${mode.packageName}ObservableField;
      public class Model {
        public Object overloadedFunction(Object object) {}
        public String overloadedFunction(String str) {}
        public ObservableField<String> getObservableString() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:text="@{model.overlo${caret}adedFunction(model.observableString)}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("java.lang.String")
  }

  @Test
  fun dbReferencesMethodWithObservableArguments() {
    fixture.addClass("""
      package test.langdb;
      import ${mode.packageName}ObservableField;
      public class Model {
        public Object overloadedFunction(int value) {}
        public String overloadedFunction(String str) {}
        public ObservableField<String> getObservableString() {}
      }
    """.trimIndent())

    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="model" type="test.langdb.Model" />
        </data>
        <TextView android:text="@{model.overloadedF${caret}unction(model.observableString)}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("java.lang.String")
  }

  @Test
  fun dbReferencesTextResourceFromRClass() {
    fixture.addFileToProject("res/values/strings.xml", """
      <resources>
        <string name="zero">there are <b>zero</b></string>
      </resources>
    """.trimIndent())
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
      <data>
        <import type="test.langdb.R" />
      </data>
        <TextView android:text="@{R.string.ze${caret}ro}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("int")
  }

  @Test
  fun dbReferencesIdResourceFromRClass() {
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
      <data>
        <import type="test.langdb.R" />
      </data>
        <TextView
            android:id="@+id/view_id"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:text="@{R.id.view${caret}_id}"/>
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("int")
  }

  @Test
  fun dbReferencesDrawableResourceFromRClass() {
    fixture.addFileToProject("res/drawable/pic.png", "0000000")
    val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto">
      <data>
        <import type="test.langdb.R" />
      </data>
        <ImageView
        android:layout_height="wrap_content"
        android:layout_width="wrap_content"
        android:src="@{R.drawable.p${caret}ic}" />
      </layout>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    val reference = fixture.getReferenceAtCaretPosition()!!
    assertThat((reference as ModelClassResolvable).resolvedType!!.type.canonicalText).isEqualTo("int")
  }
}
