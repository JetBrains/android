/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.psi

import com.android.tools.idea.nav.safeargs.SafeArgsRule
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.testing.findClass
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.jvm.types.JvmPrimitiveType
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.impl.source.PsiImmediateClassType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class LightArgsBuilderClassTest {
  @get:Rule
  val safeArgsRule = SafeArgsRule()

  @Test
  fun canFindArgsBuilderClasses() {
    safeArgsRule.fixture.addClass("package test.safeargs; public class CustomClass {}")
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment1"
              android:name="test.safeargs.Fragment1"
              android:label="Fragment1">
            <argument
                android:name="arg"
                app:argType="string" />
          </fragment>
          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2">
            <argument
                android:name="arg"
                app:argType="string" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment1 {}")

    // Classes can be found with context
    assertThat(safeArgsRule.fixture.findClass("test.safeargs.Fragment1Args.Builder", context)).isInstanceOf(LightArgsBuilderClass::class.java)
    assertThat(safeArgsRule.fixture.findClass("test.safeargs.Fragment2Args.Builder", context)).isInstanceOf(LightArgsBuilderClass::class.java)

    // ... but cannot be found without context
    val psiFacade = JavaPsiFacade.getInstance(safeArgsRule.project)
    assertThat(psiFacade.findClass("test.safeargs.Fragment1Args.Builder", GlobalSearchScope.allScope(safeArgsRule.project))).isNull()
  }

  @Test
  fun expectedBuilderConstructorsAreCreated() {
    safeArgsRule.fixture.addClass("package test.safeargs; public class CustomClass {}")
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment"
              android:name="test.safeargs.Fragment"
              android:label="Fragment">
            <argument
                android:name="arg1"
                app:argType="string" />
            <argument
                android:name="arg2"
                app:argType="integer" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val builderClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs.Builder", context) as LightArgsBuilderClass
    val parentArgsClass = builderClass.containingClass

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    // class) and an arg constructor (which takes all arguments specified by <argument> tags)
    builderClass.constructors.let { constructors ->
      assertThat(constructors.size).isEqualTo(2)
      constructors[0].parameters.let { copyConstructorParams ->
        assertThat(copyConstructorParams.size).isEqualTo(1)
        assertThat((copyConstructorParams[0].type as PsiImmediateClassType).resolve()).isEqualTo(parentArgsClass)
        assertThat(copyConstructorParams[0].name).isEqualTo("original")
      }
      constructors[1].parameters.let { argConstructorParams ->
        assertThat(argConstructorParams.size).isEqualTo(2)
        assertThat((argConstructorParams[0].type as PsiClassReferenceType).className).isEqualTo("String")
        assertThat(argConstructorParams[0].name).isEqualTo("arg1")
        assertThat((argConstructorParams[1].type as JvmPrimitiveType).kind.name).isEqualTo("int")
        assertThat(argConstructorParams[1].name).isEqualTo("arg2")
      }
    }
  }

  @Test
  fun expectedBuilderMethodsAreCreated() {
    safeArgsRule.fixture.addClass("package test.safeargs; public class CustomClass {}")
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment"
              android:name="test.safeargs.Fragment"
              android:label="Fragment">
            <argument
                android:name="arg1"
                app:argType="string" />
            <argument
                android:name="arg2"
                app:argType="integer" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val builderClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs.Builder", context) as LightArgsBuilderClass
    val parentArgsClass = builderClass.containingClass

    // For the above xml, we expect a getter and setter for each <argument> tag as well as a final
    // `build()` method that generates its parent args class.
    builderClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(5)
      methods[0].let { arg1setter ->
        assertThat(arg1setter.name).isEqualTo("setArg1")
        assertThat(arg1setter.parameters.size).isEqualTo(1)
        assertThat((arg1setter.parameters[0].type as PsiClassReferenceType).className).isEqualTo("String")
        assertThat(arg1setter.parameters[0].name).isEqualTo("arg1")
      }
      methods[1].let { arg1getter ->
        assertThat(arg1getter.name).isEqualTo("getArg1")
        assertThat(arg1getter.parameters).isEmpty()
        assertThat((arg1getter.returnType as PsiClassReferenceType).className).isEqualTo("String")
      }
      methods[2].let { arg2setter ->
        assertThat(arg2setter.name).isEqualTo("setArg2")
        assertThat(arg2setter.parameters.size).isEqualTo(1)
        assertThat((arg2setter.parameters[0].type as JvmPrimitiveType).kind.name).isEqualTo("int")
        assertThat(arg2setter.parameters[0].name).isEqualTo("arg2")
      }
      methods[3].let { arg2getter ->
        assertThat(arg2getter.name).isEqualTo("getArg2")
        assertThat(arg2getter.parameters).isEmpty()
        assertThat((arg2getter.returnType as JvmPrimitiveType).kind.name).isEqualTo("int")
      }
      methods[4].let { build ->
        assertThat(build.name).isEqualTo("build")
        assertThat(build.parameters).isEmpty()
        assertThat((build.returnType as PsiImmediateClassType).resolve()).isEqualTo(parentArgsClass)
      }
    }
  }
}