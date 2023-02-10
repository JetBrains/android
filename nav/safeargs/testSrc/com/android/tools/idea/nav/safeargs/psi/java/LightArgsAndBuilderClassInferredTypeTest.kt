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
 * Tests that would normally go in [LightArgsBuilderClassTest] and [LightArgsClass] but are related to
 * a bunch of arguments types that we want to test with parametrization.
 */
@RunsInEdt
@RunWith(Parameterized::class)
class LightArgsAndBuilderClassInferredTypeTest(
  private val defaultValueTypeMapping: DefaultValueTypeMapping
) {
  @get:Rule
  val safeArgsRule = SafeArgsRule()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data() = listOf(
      DefaultValueTypeMapping("1", PsiType.INT.name),
      DefaultValueTypeMapping("0x21", PsiType.INT.name),
      DefaultValueTypeMapping("1f", PsiType.FLOAT.name),
      DefaultValueTypeMapping("1L", PsiType.LONG.name),
      DefaultValueTypeMapping("true", PsiType.BOOLEAN.name),
      DefaultValueTypeMapping("someString", "String"),
      DefaultValueTypeMapping("@null", "String"),
      DefaultValueTypeMapping("@resourceType/resourceName", PsiType.INT.name),
      DefaultValueTypeMapping("someCustomType", "String"), // custom type can't be recognized
      DefaultValueTypeMapping("someEnumType", "String") // custom type can't be recognized
    )
  }

  @Test
  fun expectedBuilderConstructorsAndMethodsAreCreated_inferredType() {
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
                android:defaultValue="${defaultValueTypeMapping.defaultValue}"/>
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    StudioResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val builderClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs.Builder", context) as LightArgsBuilderClass

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    builderClass.constructors.let { constructors ->
      assertThat(constructors.size).isEqualTo(2)
      constructors[0].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("original", "FragmentArgs")
        )
      )

      constructors[1].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name
      )
    }

    // For the above xml, we expect a getter and setter for each <argument> tag as well as a final
    // `build()` method that generates its parent args class.
    builderClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(3)

      methods[0].checkSignaturesAndReturnType(
        name = "setArg1",
        returnType = "Builder",
        parameters = listOf(
          Parameter("arg1", defaultValueTypeMapping.inferredTypeStr)
        )
      )

      methods[1].checkSignaturesAndReturnType(
        name = "getArg1",
        returnType = defaultValueTypeMapping.inferredTypeStr,
        isReturnTypeNullable = defaultValueTypeMapping.defaultValue == "@null"
      )

      methods[2].checkSignaturesAndReturnType(
        name = "build",
        returnType = "FragmentArgs"
      )
    }
  }

  @Test
  fun expectedMethodsAreCreated_inferredType_fromSavedStateHandle() {
    // Use version [SafeArgsFeatureVersions.FROM_SAVED_STATE_HANDLE] and check the corresponding methods and field.
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
                android:name="arg1"
                android:defaultValue="${defaultValueTypeMapping.defaultValue}" />
          </fragment>
        </navigation>
        """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    StudioResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val argClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs", context) as LightArgsClass

    argClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(4)
      methods[0].checkSignaturesAndReturnType(
        name = "getArg1",
        returnType = defaultValueTypeMapping.inferredTypeStr,
        isReturnTypeNullable = defaultValueTypeMapping.defaultValue == "@null"
      )

      methods[1].checkSignaturesAndReturnType(
        name = "fromBundle",
        returnType = "FragmentArgs",
        parameters = listOf(
          Parameter("bundle", "Bundle")
        )
      )

      methods[2].checkSignaturesAndReturnType(
        name = "fromSavedStateHandle",
        returnType = "FragmentArgs",
        parameters = listOf(
          Parameter("savedStateHandle", "SavedStateHandle")
        )
      )

      methods[3].checkSignaturesAndReturnType(
        name = "toBundle",
        returnType = "Bundle"
      )
    }
  }

  @Test
  fun expectedMethodsAreCreated_inferredType_toSavedStateHandle() {
    // Use version [SafeArgsFeatureVersions.TO_SAVED_STATE_HANDLE] and check the corresponding methods and field.
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
                android:name="arg1"
                android:defaultValue="${defaultValueTypeMapping.defaultValue}" />
          </fragment>
        </navigation>
        """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    StudioResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val argClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs", context) as LightArgsClass

    argClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(5)
      methods[0].checkSignaturesAndReturnType(
        name = "getArg1",
        returnType = defaultValueTypeMapping.inferredTypeStr,
        isReturnTypeNullable = defaultValueTypeMapping.defaultValue == "@null"
      )

      methods[1].checkSignaturesAndReturnType(
        name = "fromBundle",
        returnType = "FragmentArgs",
        parameters = listOf(
          Parameter("bundle", "Bundle")
        )
      )

      methods[2].checkSignaturesAndReturnType(
        name = "fromSavedStateHandle",
        returnType = "FragmentArgs",
        parameters = listOf(
          Parameter("savedStateHandle", "SavedStateHandle")
        )
      )

      methods[3].checkSignaturesAndReturnType(
        name = "toSavedStateHandle",
        returnType = "SavedStateHandle"
      )

      methods[4].checkSignaturesAndReturnType(
        name = "toBundle",
        returnType = "Bundle"
      )
    }
  }
}

data class DefaultValueTypeMapping(val defaultValue: String, val inferredTypeStr: String)