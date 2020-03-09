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
import com.android.tools.idea.nav.safeargs.extensions.Parameter
import com.android.tools.idea.nav.safeargs.extensions.checkSignaturesAndReturnType
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.testing.findClass
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class LightArgsClassTest {
  @get:Rule
  val safeArgsRule = SafeArgsRule()

  @Test
  fun canFindArgsClasses() {
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
              android:label="Fragment2" />
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment1 {}")

    // Classes can be found with context
    assertThat(safeArgsRule.fixture.findClass("test.safeargs.Fragment1Args", context)).isInstanceOf(LightArgsClass::class.java)

    // ... but not generated if no arguments
    assertThat(safeArgsRule.fixture.findClass("test.safeargs.Fragment2Args", context)).isNull()

    // ... but cannot be found without context
    val psiFacade = JavaPsiFacade.getInstance(safeArgsRule.project)
    assertThat(psiFacade.findClass("test.safeargs.Fragment1Args", GlobalSearchScope.allScope(safeArgsRule.project))).isNull()
  }

  @Test
  fun expectedGettersAndFromBundleMethodsAreCreated() {
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
                app:argType="integer" />
            <argument
                android:name="arg2"
                app:argType="float" />
            <argument
                android:name="arg3"
                app:argType="long" />
            <argument
                android:name="arg4"
                app:argType="boolean" />
            <argument
                android:name="arg5"
                app:argType="string" />
            <argument
                android:name="arg6"
                app:argType="reference" />
            <argument
                android:name="arg7"
                app:argType="test.safeargs.MyParcelable"/>
            <argument
                android:name="arg8"
                app:argType="test.safeargs.MySerializable"/>
            <argument
                android:name="arg9"
                app:argType="test.safeargs.MyEnum"/>

          </fragment>
        </navigation>
        """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val argClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs", context) as LightArgsClass

    argClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(10)
      methods[0].checkSignaturesAndReturnType(
        name = "getArg1",
        returnType = PsiType.INT.name
      )

      methods[1].checkSignaturesAndReturnType(
        name = "getArg2",
        returnType = PsiType.FLOAT.name
      )

      methods[2].checkSignaturesAndReturnType(
        name = "getArg3",
        returnType = PsiType.LONG.name
      )

      methods[3].checkSignaturesAndReturnType(
        name = "getArg4",
        returnType = PsiType.BOOLEAN.name
      )

      methods[4].checkSignaturesAndReturnType(
        name = "getArg5",
        returnType = "String"
      )

      methods[5].checkSignaturesAndReturnType(
        name = "getArg6",
        returnType = PsiType.INT.name
      )

      methods[6].checkSignaturesAndReturnType(
        name = "getArg7",
        returnType = "MyParcelable"
      )

      methods[7].checkSignaturesAndReturnType(
        name = "getArg8",
        returnType = "MySerializable"
      )

      methods[8].checkSignaturesAndReturnType(
        name = "getArg9",
        returnType = "MyEnum"
      )

      methods[9].checkSignaturesAndReturnType(
        name = "fromBundle",
        returnType = "FragmentArgs",
        parameters = listOf(
          Parameter("bundle", "Bundle")
        )
      )
    }
  }
}