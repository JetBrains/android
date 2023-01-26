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
 * Tests that would normally go in [LightArgsBuilderClassTest] but are related to
 * a bunch of arguments types that we want to test with parametrization.
 */
@RunsInEdt
@RunWith(Parameterized::class)
class LightArgsBuilderClassConstructorsAndMethodsTest(private val typeMapping: TypeMapping) {
  @get:Rule
  val safeArgsRule = SafeArgsRule()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data() = listOf(
      TypeMapping("integer", PsiTypes.intType().name),
      TypeMapping(PsiTypes.floatType().name),
      TypeMapping(PsiTypes.longType().name),
      TypeMapping(PsiTypes.booleanType().name),
      TypeMapping("string", "String"),
      TypeMapping("reference", PsiTypes.intType().name),
      TypeMapping("test.safeargs.MyCustomType", "MyCustomType"), // e.g Parcelable, Serializable
      TypeMapping("test.safeargs.MyEnum", "MyEnum"),
      TypeMapping("test.safeargs.Outer\$Inner", "Inner"),
    )
  }

  @Test
  fun expectedBuilderConstructorsAndMethodsAreCreated() {
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
                app:argType="${typeMapping.before}"
                android:defaultValue="someDefaultValue"/>
            <argument
                android:name="arg_three"
                app:argType="${typeMapping.before}[]" />
            <argument
                android:name="arg_four"
                app:argType="${typeMapping.before}[]"
                app:nullable="true"
                android:defaultValue="@null"/>
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val builderClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs.Builder", context) as LightArgsBuilderClass

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    builderClass.constructors.let { constructors ->
      assertThat(constructors.size).isEqualTo(2)
      constructors[0].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = "null",
        parameters = listOf(
          Parameter("original", "FragmentArgs")
        )
      )

      constructors[1].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = "null",
        parameters = listOf(
          Parameter("argOne", typeMapping.after),
          Parameter("argThree", "${typeMapping.after}[]")
        )
      )
    }

    // For the above xml, we expect a getter and setter for each <argument> tag as well as a final
    // `build()` method that generates its parent args class.
    builderClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(9)

      methods[0].checkSignaturesAndReturnType(
        name = "setArgOne",
        returnType = "Builder",
        parameters = listOf(
          Parameter("argOne", typeMapping.after)
        )
      )

      methods[1].checkSignaturesAndReturnType(
        name = "getArgOne",
        returnType = typeMapping.after
      )

      methods[2].checkSignaturesAndReturnType(
        name = "setArgTwo",
        returnType = "Builder",
        parameters = listOf(
          Parameter("argTwo", typeMapping.after)
        )
      )

      methods[3].checkSignaturesAndReturnType(
        name = "getArgTwo",
        returnType = typeMapping.after
      )

      methods[4].checkSignaturesAndReturnType(
        name = "setArgThree",
        returnType = "Builder",
        parameters = listOf(
          Parameter("argThree", "${typeMapping.after}[]")
        )
      )

      methods[5].checkSignaturesAndReturnType(
        name = "getArgThree",
        returnType = "${typeMapping.after}[]"
      )

      methods[6].checkSignaturesAndReturnType(
        name = "setArgFour",
        returnType = "Builder",
        parameters = listOf(
          Parameter("argFour", "${typeMapping.after}[]")
        )
      )

      methods[7].checkSignaturesAndReturnType(
        name = "getArgFour",
        isReturnTypeNullable = true,
        returnType = "${typeMapping.after}[]"
      )

      methods[8].checkSignaturesAndReturnType(
        name = "build",
        returnType = "FragmentArgs"
      )
    }
  }
}