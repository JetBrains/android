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
              android:id="@+id/fragment1"
              android:name="test.safeargs.Fragment1"
              android:label="Fragment1">
              
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

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment1 {}")
    // Classes can be found with context
    val fragment1ArgsClass = safeArgsRule.fixture.findClass("test.safeargs.Fragment1Args", context)
    fragment1ArgsClass!!.methods.let { methods ->
      assertThat(methods.size).isEqualTo(3)
      methods[0].let { arg1getter ->
        assertThat(arg1getter.name).isEqualTo("getArg1")
        assertThat(arg1getter.parameters).isEmpty()
        assertThat((arg1getter.returnType as PsiClassReferenceType).className).isEqualTo("String")
      }

      methods[1].let { arg2getter ->
        assertThat(arg2getter.name).isEqualTo("getArg2")
        assertThat(arg2getter.parameters).isEmpty()
        assertThat((arg2getter.returnType as JvmPrimitiveType).kind.name).isEqualTo("int")
      }

      methods[2].let { fromBundle ->
        assertThat(fromBundle.name).isEqualTo("fromBundle")
        assertThat(fromBundle.parameters.size).isEqualTo(1)
        assertThat((fromBundle.parameters[0].type as PsiClassReferenceType).className).isEqualTo("Bundle")
        assertThat(fromBundle.parameters[0].name).isEqualTo("bundle")
      }
    }
  }
}