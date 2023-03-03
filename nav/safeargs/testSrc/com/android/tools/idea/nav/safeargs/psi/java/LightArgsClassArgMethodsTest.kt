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
package com.android.tools.idea.nav.safeargs.psi.java

import com.android.tools.idea.nav.safeargs.SafeArgsRule
import com.android.tools.idea.nav.safeargs.extensions.Parameter
import com.android.tools.idea.nav.safeargs.extensions.checkSignaturesAndReturnType
import com.android.tools.idea.nav.safeargs.psi.SafeArgsFeatureVersions
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.testing.findClass
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiType
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests that would normally go in [LightArgsClassTest] but are related to
 * a bunch of arguments types that we want to test with parametrization.
 */
@RunsInEdt
@RunWith(Parameterized::class)
class LightArgsClassArgMethodsTest(private val typeMapping: TypeMapping) {
  @get:Rule
  val safeArgsRule = SafeArgsRule()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data() = listOf(
      TypeMapping("integer", PsiType.INT.name),
      TypeMapping(PsiType.FLOAT.name),
      TypeMapping(PsiType.LONG.name),
      TypeMapping(PsiType.BOOLEAN.name),
      TypeMapping("string", "String"),
      TypeMapping("reference", PsiType.INT.name),
      TypeMapping("test.safeargs.MyCustomType", "MyCustomType"), // e.g Parcelable, Serializable
      TypeMapping("test.safeargs.MyEnum", "MyEnum")
    )
  }

  @Test
  fun expectedMethodsAreCreated() {
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
                android:name="arg_one"
                app:argType="${typeMapping.before}" />
            <argument
                android:name="arg_two"
                app:argType="${typeMapping.before}[]" />
          </fragment>
        </navigation>
        """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    StudioResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val argClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs", context) as LightArgsClass

    // Check supers
    argClass.supers.asList().let {
      assertThat(it).hasSize(1)
      assertThat(it.first().name).isEqualTo("NavArgs")
    }

    // Check methods
    argClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(4)
      methods[0].checkSignaturesAndReturnType(
        name = "getArgOne",
        returnType = typeMapping.after
      )

      methods[1].checkSignaturesAndReturnType(
        name = "getArgTwo",
        returnType = "${typeMapping.after}[]"
      )

      methods[2].checkSignaturesAndReturnType(
        name = "fromBundle",
        returnType = "FragmentArgs",
        parameters = listOf(
          Parameter("bundle", "Bundle")
        )
      )

      methods[3].checkSignaturesAndReturnType(
        name = "toBundle",
        returnType = "Bundle"
      )
    }
  }

  @Test
  fun expectedMethodsAreCreated_AfterFromSavedStateHandleFeature() {
    safeArgsRule.addFakeNavigationDependency(SafeArgsFeatureVersions.FROM_SAVED_STATE_HANDLE)

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
                android:name="arg_one"
                app:argType="${typeMapping.before}" />
            <argument
                android:name="arg_two"
                app:argType="${typeMapping.before}[]" />
          </fragment>
        </navigation>
        """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    StudioResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val argClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs", context) as LightArgsClass

    // Check supers
    argClass.supers.asList().let {
      assertThat(it).hasSize(1)
      assertThat(it.first().name).isEqualTo("NavArgs")
    }

    // Check methods
    argClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(5)
      methods[0].checkSignaturesAndReturnType(
        name = "getArgOne",
        returnType = typeMapping.after
      )

      methods[1].checkSignaturesAndReturnType(
        name = "getArgTwo",
        returnType = "${typeMapping.after}[]"
      )

      methods[2].checkSignaturesAndReturnType(
        name = "fromBundle",
        returnType = "FragmentArgs",
        parameters = listOf(
          Parameter("bundle", "Bundle")
        )
      )

      methods[3].checkSignaturesAndReturnType(
        name = "fromSavedStateHandle",
        returnType = "FragmentArgs",
        parameters = listOf(
          Parameter("savedStateHandle", "SavedStateHandle")
        )
      )

      methods[4].checkSignaturesAndReturnType(
        name = "toBundle",
        returnType = "Bundle"
      )
    }
  }

  @Test
  fun expectedMethodsAreCreated_AfterToSavedStateHandleFeature() {
    safeArgsRule.addFakeNavigationDependency(SafeArgsFeatureVersions.TO_SAVED_STATE_HANDLE)

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
                android:name="arg_one"
                app:argType="${typeMapping.before}" />
            <argument
                android:name="arg_two"
                app:argType="${typeMapping.before}[]" />
          </fragment>
        </navigation>
        """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    StudioResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val argClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs", context) as LightArgsClass

    // Check supers
    argClass.supers.asList().let {
      assertThat(it).hasSize(1)
      assertThat(it.first().name).isEqualTo("NavArgs")
    }

    // Check methods
    argClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(6)
      methods[0].checkSignaturesAndReturnType(
        name = "getArgOne",
        returnType = typeMapping.after
      )

      methods[1].checkSignaturesAndReturnType(
        name = "getArgTwo",
        returnType = "${typeMapping.after}[]"
      )

      methods[2].checkSignaturesAndReturnType(
        name = "fromBundle",
        returnType = "FragmentArgs",
        parameters = listOf(
          Parameter("bundle", "Bundle")
        )
      )

      methods[3].checkSignaturesAndReturnType(
        name = "fromSavedStateHandle",
        returnType = "FragmentArgs",
        parameters = listOf(
          Parameter("savedStateHandle", "SavedStateHandle")
        )
      )

      methods[4].checkSignaturesAndReturnType(
        name = "toSavedStateHandle",
        returnType = "SavedStateHandle"
      )

      methods[5].checkSignaturesAndReturnType(
        name = "toBundle",
        returnType = "Bundle"
      )
    }
  }
}