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
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTypes
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
              android:label="Fragment1" >
              
              <action
                  android:id="@+id/action_Fragment1_to_main"
                  app:popUpTo="@id/main" />
          </fragment>
          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2" >
              
              <action
                  android:id="@+id/action_Fragment2_to_main"
                  app:destination="@id/main" />
          </fragment>

          <action
              android:id="@+id/action_main_to_fragment1"
              app:destination="@id/fragment1" />

          <!-- Sample action -->
          <action android:id="@+id/action_without_destination" />
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment1 {}")

    // Classes can be found with context
    assertThat(safeArgsRule.fixture.findClass("test.safeargs.Fragment1Directions", context)).isInstanceOf(LightDirectionsClass::class.java)
    assertThat(safeArgsRule.fixture.findClass("test.safeargs.Fragment2Directions", context)).isInstanceOf(LightDirectionsClass::class.java)
    assertThat(safeArgsRule.fixture.findClass("test.safeargs.MainDirections", context)).isInstanceOf(LightDirectionsClass::class.java)
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
                android:name="arg_one"
                app:argType="string" />
            <argument
                android:name="arg_two"
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
      (action as PsiMethod).checkSignaturesAndReturnType(
        name = "actionToNested",
        returnType = "NavDirections"
      )
    }

    fragment1directions.findMethodsByName("actionFragment1ToFragment2").first().let { action ->
      (action as PsiMethod).checkSignaturesAndReturnType(
        name = "actionFragment1ToFragment2",
        returnType = "ActionFragment1ToFragment2",
        parameters = listOf(
          Parameter("argOne", "String"),
          Parameter("argTwo", PsiTypes.floatType().name)
        )
      )
    }
    fragment1directions.findMethodsByName("actionFragment1ToFragment3").first().let { action ->
      (action as PsiMethod).checkSignaturesAndReturnType(
        name = "actionFragment1ToFragment3",
        returnType = "ActionFragment1ToFragment3",
        parameters = listOf(
          Parameter("arg", PsiTypes.intType().name)
        )
      )
    }
  }

  @Test
  fun testIncludedNavigationCase() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">
          <include app:graph="@navigation/included_graph" />
          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2" >

              <action
                  android:id="@+id/action_Fragment2_to_IncludedGraph"
                  app:destination="@id/included_graph" />                  
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment1 {}")

    // Class can be found with context
    val fragment2Directions = safeArgsRule.fixture.findClass("test.safeargs.Fragment2Directions", context) as LightDirectionsClass

    // Check method
    fragment2Directions.findMethodsByName("actionFragment2ToIncludedGraph").first().let { action ->
      (action as PsiMethod).checkSignaturesAndReturnType(
        name = "actionFragment2ToIncludedGraph",
        returnType = "NavDirections"
      )
    }
  }

  @Test
  fun testGlobalActionCase() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">
            
          <action
                android:id="@+id/action_to_IncludedGraph"
                app:destination="@id/included_graph" />  
            
          <navigation
                android:id="@+id/inner_navigation"
                app:startDestination="@id/inner_fragment">
                
            <action
                android:id="@+id/action_InnerNavigation_to_IncludedGraph"
                app:destination="@id/included_graph" />  
                 
            <fragment
                android:id="@+id/fragment2"
                android:name="test.safeargs.Fragment2"
                android:label="Fragment2" >

                <action
                    android:id="@+id/action_Fragment2_to_IncludedGraph"
                    app:destination="@id/included_graph" />  
                    
                <!-- Same action with one of global actions -->
                <action
                    android:id="@+id/action_to_IncludedGraph"
                    app:destination="@id/included_graph" />  
            </fragment>
                
          </navigation>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment1 {}")

    // Class can be found with context
    val fragment2Directions = safeArgsRule.fixture.findClass("test.safeargs.Fragment2Directions", context) as LightDirectionsClass
    // Check methods of Fragment2Directions
    fragment2Directions.methods.let { methods ->
      assertThat(methods.size).isEqualTo(3)
      methods[0].checkSignaturesAndReturnType(
        name = "actionFragment2ToIncludedGraph",
        returnType = "NavDirections"
      )

      methods[1].checkSignaturesAndReturnType(
        name = "actionToIncludedGraph",
        returnType = "NavDirections"
      )

      methods[2].checkSignaturesAndReturnType(
        name = "actionInnerNavigationToIncludedGraph",
        returnType = "NavDirections"
      )
    }

    val innerNavigationDirections = safeArgsRule.fixture.findClass("test.safeargs.InnerNavigationDirections",
                                                                   context) as LightDirectionsClass
    // Check methods of InnerNavigationDirections
    innerNavigationDirections.methods.let { methods ->
      assertThat(methods.size).isEqualTo(2)
      methods[0].checkSignaturesAndReturnType(
        name = "actionInnerNavigationToIncludedGraph",
        returnType = "NavDirections"
      )

      methods[1].checkSignaturesAndReturnType(
        name = "actionToIncludedGraph",
        returnType = "NavDirections"
      )
    }

    val mainDirections = safeArgsRule.fixture.findClass("test.safeargs.MainDirections", context) as LightDirectionsClass
    // Check methods of InnerNavigationDirections
    mainDirections.methods.let { methods ->
      assertThat(methods.size).isEqualTo(1)
      methods[0].checkSignaturesAndReturnType(
        name = "actionToIncludedGraph",
        returnType = "NavDirections"
      )
    }
  }

  @Test
  fun testNoDestinationDefined() {
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
              android:label="Fragment1" >
              <argument
                  android:name="arg"
                  app:argType="string" />

              <action
                  android:id="@+id/action_to_Main"
                  app:popUpTo="@id/main" />
          </fragment>
          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2" >
              
              <action
                  android:id="@+id/action_Fragment2_to_Fragment1"
                  app:popUpTo="@id/fragment1" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment1 {}")

    // Classes can be found with context
    val fragment1DirectionsClass = safeArgsRule.fixture.findClass("test.safeargs.Fragment1Directions", context) as LightDirectionsClass
    val fragment2DirectionsClass = safeArgsRule.fixture.findClass("test.safeargs.Fragment2Directions", context) as LightDirectionsClass

    // Because we don't have destination defined, no arguments are supposed to be passed. This means no inner builder
    // action classes are created. NavDirections class(`ActionOnlyNavDirections` as the implementation under the hood)
    // is being used here.
    assertThat(fragment1DirectionsClass.innerClasses).isEmpty()
    assertThat(fragment2DirectionsClass.innerClasses).isEmpty()

    fragment1DirectionsClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(1)
      methods[0].checkSignaturesAndReturnType(
        name = "actionToMain",
        returnType = "NavDirections"
      )
    }

    fragment2DirectionsClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(1)
      methods[0].checkSignaturesAndReturnType(
        name = "actionFragment2ToFragment1",
        returnType = "NavDirections"
      )
    }
  }
}