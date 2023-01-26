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
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.testing.findClass
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiTypes
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
class LightArgsAndBuilderClassNullabilityAnnotationTest(
  private val typeNullabilityMapping: TypeNullabilityMapping
) {
  @get:Rule
  val safeArgsRule = SafeArgsRule()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data() = listOf(
      TypeNullabilityMapping("integer", PsiTypes.intType().name, false),
      TypeNullabilityMapping(PsiTypes.floatType().name, false),
      TypeNullabilityMapping(PsiTypes.longType().name, false),
      TypeNullabilityMapping(PsiTypes.booleanType().name, false),
      TypeNullabilityMapping("string", "String", true),
      TypeNullabilityMapping("reference", PsiTypes.intType().name, false),
      TypeNullabilityMapping("test.safeargs.MyCustomType", "MyCustomType", true) // e.g Parcelable, Serializable
    )
  }

  @Test
  fun expectedBuilderConstructorsAndMethodsAreCreated_withNullabilityAnnotations() {
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
                app:argType="${typeNullabilityMapping.before}"
                app:nullable="true"/>
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val builderClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs.Builder", context) as LightArgsBuilderClass

    // For the above xml, we expect a getter and setter for each <argument> tag as well as a final
    // `build()` method that generates its parent args class.
    builderClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(3)

      methods[0].checkSignaturesAndReturnType(
        name = "setArg1",
        returnType = "Builder",
        parameters = listOf(
          Parameter("arg1", typeNullabilityMapping.after)
        )
      )

      methods[1].checkSignaturesAndReturnType(
        name = "getArg1",
        returnType = typeNullabilityMapping.after,
        isReturnTypeNullable = typeNullabilityMapping.isReturnTypeNullable
      )

      methods[2].checkSignaturesAndReturnType(
        name = "build",
        returnType = "FragmentArgs"
      )
    }
  }

  @Test
  fun expectedGettersAndFromBundleMethodsAreCreated_withNullabilityAnnotations() {
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
                app:argType="${typeNullabilityMapping.before}"
                app:nullable="true"/>
          </fragment>
        </navigation>
        """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val argClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs", context) as LightArgsClass

    argClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(3)
      methods[0].checkSignaturesAndReturnType(
        name = "getArg1",
        returnType = typeNullabilityMapping.after,
        isReturnTypeNullable = typeNullabilityMapping.isReturnTypeNullable
      )

      methods[1].checkSignaturesAndReturnType(
        name = "fromBundle",
        returnType = "FragmentArgs",
        parameters = listOf(
          Parameter("bundle", "Bundle")
        )
      )

      methods[2].checkSignaturesAndReturnType(
        name = "toBundle",
        returnType = "Bundle"
      )
    }
  }
}

data class TypeNullabilityMapping(val before: String, val after: String, val isReturnTypeNullable: Boolean) {
  constructor(beforeAndAfter: String, nullability: Boolean) : this(beforeAndAfter, beforeAndAfter, nullability)
}