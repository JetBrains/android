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
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.testing.findClass
import com.google.common.truth.Truth
import com.intellij.psi.PsiType
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class LightActionBuilderClassTest {
  @get:Rule
  val safeArgsRule = SafeArgsRule()

  @Test
  fun canFindActionBuilderClasses() {
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
                android:name="arg_one"
                app:argType="string" />
            <action
              android:id="@+id/action_fragment1_to_fragment2"
              app:destination="@id/fragment2" />
          </fragment>
          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2">
            <argument
                android:name="arg_one"
                app:argType="string" />
            <action
              android:id="@+id/action_fragment2_to_main"
              app:destination="@id/main" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    StudioResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment1 {}")

    Truth.assertThat(safeArgsRule.fixture.findClass("test.safeargs.Fragment2Directions.ActionFragment2ToMain", context)).isNull()

    // Classes can be found with context
    val actionBuilderClass = safeArgsRule.fixture.findClass("test.safeargs.Fragment1Directions.ActionFragment1ToFragment2", context)
    Truth.assertThat(actionBuilderClass).isInstanceOf(LightActionBuilderClass::class.java)

    // Check supers
    actionBuilderClass!!.supers.asList().let {
      Truth.assertThat(it).hasSize(1)
      Truth.assertThat(it.first().name).isEqualTo("NavDirections")
    }

    // Check methods
    actionBuilderClass.methods.let { methods ->
      Truth.assertThat(methods.size).isEqualTo(2)

      methods[0].checkSignaturesAndReturnType(
        name = "setArgOne",
        returnType = "ActionFragment1ToFragment2",
        parameters = listOf(
          Parameter("arg", "String")
        )
      )

      methods[1].checkSignaturesAndReturnType(
        name = "getArgOne",
        returnType = "String"
      )
    }
  }

  @Test
  fun testOverriddenArguments() {
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
            <action
              android:id="@+id/action_fragment1_to_fragment2"
              app:destination="@id/fragment2" >
              <argument
                android:name="overriddenArg"
                app:argType="string" />
                
              <argument
                  android:name="overridden_arg_with_default_value"
                  app:argType="integer"
                  android:defaultValue="1" />
            </action>
          </fragment>
          
          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2">
            <argument
                android:name="arg"
                app:argType="string" />
                
            <argument
                android:name="overridden_arg_with_default_value"
                app:argType="integer" />
                
            <action
              android:id="@+id/action_fragment2_to_main"
              app:destination="@id/main" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    StudioResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment1 {}")

    // All resolved arguments are with default values, so it falls back to NavDirections.
    Truth.assertThat(safeArgsRule.fixture.findClass("test.safeargs.Fragment2Directions.ActionFragment2ToMain", context)).isNull()

    // Classes can be found with context
    val actionBuilderClass = safeArgsRule.fixture.findClass("test.safeargs.Fragment1Directions.ActionFragment1ToFragment2", context)
    Truth.assertThat(actionBuilderClass).isInstanceOf(LightActionBuilderClass::class.java)

    // Check supers
    actionBuilderClass!!.supers.asList().let {
      Truth.assertThat(it).hasSize(1)
      Truth.assertThat(it.first().name).isEqualTo("NavDirections")
    }

    // Check methods
    actionBuilderClass.methods.let { methods ->
      Truth.assertThat(methods.size).isEqualTo(6)

      methods[0].checkSignaturesAndReturnType(
        name = "setOverriddenArg",
        returnType = "ActionFragment1ToFragment2",
        parameters = listOf(
          Parameter("overriddenArg", "String")
        )
      )

      methods[1].checkSignaturesAndReturnType(
        name = "getOverriddenArg",
        returnType = "String"
      )

      methods[2].checkSignaturesAndReturnType(
        name = "setOverriddenArgWithDefaultValue",
        returnType = "ActionFragment1ToFragment2",
        parameters = listOf(
          Parameter("overriddenArgWithDefaultValue", "int")
        )
      )

      methods[3].checkSignaturesAndReturnType(
        name = "getOverriddenArgWithDefaultValue",
        returnType = "int"
      )

      methods[4].checkSignaturesAndReturnType(
        name = "setArg",
        returnType = "ActionFragment1ToFragment2",
        parameters = listOf(
          Parameter("arg", "String")
        )
      )

      methods[5].checkSignaturesAndReturnType(
        name = "getArg",
        returnType = "String"
      )
    }

    // Check private constructor
    actionBuilderClass.constructors.let { constructors ->
      Truth.assertThat(constructors.size).isEqualTo(1)
      constructors[0].checkSignaturesAndReturnType(
        name = "ActionFragment1ToFragment2",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("overriddenArg", "String"),
          Parameter("arg", "String")
        )
      )
    }
  }
}