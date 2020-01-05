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
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class LightDirectionsClassTest {
  @get:Rule
  val safeArgsRule = SafeArgsRule()

  @Test
  fun canFindDirectionsClasses() {
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
              android:label="Fragment1" />
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
    assertThat(safeArgsRule.fixture.findClass("test.safeargs.Fragment1Directions", context)).isInstanceOf(LightDirectionsClass::class.java)
    assertThat(safeArgsRule.fixture.findClass("test.safeargs.Fragment2Directions", context)).isInstanceOf(LightDirectionsClass::class.java)
    assertThat(safeArgsRule.fixture.findClass("test.safeargs.MainDirections", context)).isInstanceOf(LightDirectionsClass::class.java)

    // ... but cannot be found without context
    val psiFacade = JavaPsiFacade.getInstance(safeArgsRule.project)
    assertThat(psiFacade.findClass("test.safeargs.Fragment1Directions", GlobalSearchScope.allScope(safeArgsRule.project))).isNull()
  }

  @Test
  fun actionMethodsGeneratedWithArgs() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <action
            android:id="@+id/action_to_nested"
            app:destination="@id/nested"/>

          <fragment
              android:id="@+id/fragment1"
              android:name="test.safeargs.Fragment1"
              android:label="Fragment1" >
            <action
              android:id="@+id/action_fragment1_to_fragment2"
              app:destination="@id/fragment2" />
            <action
              android:id="@+id/action_fragment1_to_fragment3"
              app:destination="@id/fragment3" />
          </fragment>

          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2" >
            <argument
                android:name="arg1"
                app:argType="string" />
            <argument
                android:name="arg2"
                app:argType="float" />
          </fragment>

          <navigation
              android:id="@+id/nested"
              app:startDestination="@id/fragment3">

            <fragment
                android:id="@+id/fragment3"
                android:name="test.safeargs.Fragment3"
                android:label="Fragment3">
              <argument
                  android:name="arg"
                  app:argType="integer" />
            </fragment>

          </navigation>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment1 {}")

    val mainDirections = safeArgsRule.fixture.findClass("test.safeargs.MainDirections", context) as LightDirectionsClass
    val fragment1directions = safeArgsRule.fixture.findClass("test.safeargs.Fragment1Directions", context) as LightDirectionsClass

    mainDirections.findMethodsByName("actionToNested").first().let { action ->
      assertThat(action.parameters).isEmpty()
      assertThat((action.returnType as PsiClassReferenceType).className).isEqualTo("NavDirections")
    }

    fragment1directions.findMethodsByName("actionFragment1ToFragment2").first().let { action ->
      assertThat(action.parameters.size).isEqualTo(2)
      action.parameters[0].let { arg1 ->
        assertThat((arg1.type as PsiClassReferenceType).className).isEqualTo("String")
        assertThat(arg1.name).isEqualTo("arg1")
      }
      action.parameters[1].let { arg2 ->
        assertThat((arg2.type as PsiPrimitiveType).name).isEqualTo("float")
        assertThat(arg2.name).isEqualTo("arg2")
      }
      assertThat((action.returnType as PsiClassReferenceType).className).isEqualTo("NavDirections")
    }
    fragment1directions.findMethodsByName("actionFragment1ToFragment3").first().let { action ->
      assertThat(action.parameters.size).isEqualTo(1)
      action.parameters[0].let { arg1 ->
        assertThat((arg1.type as PsiPrimitiveType).name).isEqualTo("int")
        assertThat(arg1.name).isEqualTo("arg")
      }

      assertThat((action.returnType as PsiClassReferenceType).className).isEqualTo("NavDirections")
    }
  }
}