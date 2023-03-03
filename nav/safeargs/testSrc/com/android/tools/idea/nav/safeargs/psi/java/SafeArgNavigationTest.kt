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
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.testing.findClass
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class SafeArgNavigationTest {
  @get:Rule
  val safeArgsRule = SafeArgsRule()

  @Test
  fun canNavigateToXmlTagFromArgClass() {
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
          </fragment>
          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2">
          <argument
                android:name="arg_two"
                app:argType="string" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    StudioResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment1 {}")

    val editors = FileEditorManager.getInstance(safeArgsRule.fixture.project)
    assertThat(editors.selectedFiles).isEmpty()

    // check Fragment1Args class navigation
    val arg1Class = safeArgsRule.fixture.findClass("test.safeargs.Fragment1Args", context) as LightArgsClass
    arg1Class.let {
      it.navigate(true)
      assertThat(editors.selectedFiles[0].name).isEqualTo("main.xml")
      assertThat(it.navigationElement).isInstanceOf(XmlTag::class.java)
      assertThat(it.navigationElement.text).contains("id=\"@+id/fragment1\"")
    }

    // check Fragment2Args class navigation
    val arg2Class = safeArgsRule.fixture.findClass("test.safeargs.Fragment2Args", context) as LightArgsClass
    arg2Class.let {
      it.navigate(true)
      assertThat(editors.selectedFiles[0].name).isEqualTo("main.xml")
      assertThat(it.navigationElement).isInstanceOf(XmlTag::class.java)
      assertThat(it.navigationElement.text).contains("id=\"@+id/fragment2\"")
    }

    // check methods navigation of Fragment1Args
    arg1Class.methods.forEach {
      it.navigate(true)
      assertThat(editors.selectedFiles[0].name).isEqualTo("main.xml")
      assertThat(it.navigationElement).isInstanceOf(XmlTag::class.java)

      // check getter method
      if (it.name == "getArgOne") {
        assertThat(it.navigationElement.text).isEqualTo(
          """
          <argument
                  android:name="arg_one"
                  app:argType="string" />
          """.trimIndent())
      }
      else {
        assertThat(it.navigationElement.text).contains("id=\"@+id/fragment1\"")
      }
    }

    // check methods navigation of Fragment2Args
    arg2Class.methods.forEach {
      it.navigate(true)
      assertThat(editors.selectedFiles[0].name).isEqualTo("main.xml")
      assertThat(it.navigationElement).isInstanceOf(XmlTag::class.java)

      // check getter method
      if (it.name == "getArgTwo") {
        assertThat(it.navigationElement.text).isEqualTo(
          """
          <argument
                  android:name="arg_two"
                  app:argType="string" />
          """.trimIndent())
      }
      else {
        assertThat(it.navigationElement.text).contains("id=\"@+id/fragment2\"")
      }
    }
  }

  @Test
  fun canNavigateToXmlTagFromInnerArgsBuilderClass() {
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
          </fragment>
          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2" />
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    StudioResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment1 {}")

    val editors = FileEditorManager.getInstance(safeArgsRule.fixture.project)
    assertThat(editors.selectedFiles).isEmpty()

    // check class navigation
    val innerBuilderClass = safeArgsRule.fixture.findClass("test.safeargs.Fragment1Args.Builder", context) as LightArgsBuilderClass
    innerBuilderClass.navigate(true)
    assertThat(editors.selectedFiles[0].name).isEqualTo("main.xml")
    assertThat(innerBuilderClass.navigationElement).isInstanceOf(XmlTag::class.java)
    assertThat(innerBuilderClass.navigationElement.text).contains("id=\"@+id/fragment1\"")

    // check constructor navigation
    innerBuilderClass.constructors.forEach {
      it.navigate(true)
      assertThat(editors.selectedFiles[0].name).isEqualTo("main.xml")
      assertThat(it.navigationElement).isInstanceOf(XmlTag::class.java)
      assertThat(it.navigationElement.text).contains("id=\"@+id/fragment1\"")
    }

    // check methods navigation
    innerBuilderClass.methods.forEach {
      it.navigate(true)
      assertThat(editors.selectedFiles[0].name).isEqualTo("main.xml")
      assertThat(it.navigationElement).isInstanceOf(XmlTag::class.java)

      // check getter and setter method
      if (it.name == "getArgOne" || it.name == "setArgOne") {
        assertThat(it.navigationElement.text).isEqualTo(
          """
          <argument
                  android:name="arg_one"
                  app:argType="string" />
          """.trimIndent())
      }
      else {
        assertThat(it.navigationElement.text).contains("id=\"@+id/fragment1\"")
      }
    }
  }

  @Test
  fun canNavigateToXmlTagFromDirectionClass() {
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
            app:destination="@id/nested" />

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
          </fragment>

          <navigation
              android:id="@+id/nested"
              app:startDestination="@id/fragment3">

            <fragment
                android:id="@+id/fragment3"
                android:name="test.safeargs.Fragment3"
                android:label="Fragment3">
            </fragment>

          </navigation>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    StudioResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment1 {}")

    val editors = FileEditorManager.getInstance(safeArgsRule.fixture.project)
    assertThat(editors.selectedFiles).isEmpty()

    // check mainDirections class navigation
    val mainDirections = safeArgsRule.fixture.findClass("test.safeargs.MainDirections", context) as LightDirectionsClass
    mainDirections.let {
      it.navigate(true)
      assertThat(editors.selectedFiles[0].name).isEqualTo("main.xml")
      assertThat(it.navigationElement).isInstanceOf(XmlTag::class.java)
      assertThat(it.navigationElement.text).contains("id=\"@+id/main\"")
    }

    // check fragment1directions class navigation
    val fragment1directions = safeArgsRule.fixture.findClass("test.safeargs.Fragment1Directions", context) as LightDirectionsClass
    fragment1directions.let {
      it.navigate(true)
      assertThat(editors.selectedFiles[0].name).isEqualTo("main.xml")
      assertThat(it.navigationElement).isInstanceOf(XmlTag::class.java)
      assertThat(it.navigationElement.text).contains("id=\"@+id/fragment1\"")
    }

    // check methods navigation of mainDirections
    mainDirections.methods.forEach {
      it.navigate(true)
      assertThat(editors.selectedFiles[0].name).isEqualTo("main.xml")
      assertThat(it.navigationElement).isInstanceOf(XmlTag::class.java)
      assertThat(it.navigationElement.text).isEqualTo(
        """
        <action
            android:id="@+id/action_to_nested"
            app:destination="@id/nested" />
        """.trimIndent())
    }

    // check methods navigation of fragment1directions
    fragment1directions.methods.forEach {
      it.navigate(true)
      assertThat(editors.selectedFiles[0].name).isEqualTo("main.xml")
      assertThat(it.navigationElement).isInstanceOf(XmlTag::class.java)
      when (it.name) {
        "actionFragment1ToFragment2" -> {
          assertThat(it.navigationElement.text).isEqualTo(
            """
            <action
                  android:id="@+id/action_fragment1_to_fragment2"
                  app:destination="@id/fragment2" />
            """.trimIndent())
        }
        "actionFragment1ToFragment3" -> {
          assertThat(it.navigationElement.text).isEqualTo(
            """
            <action
                  android:id="@+id/action_fragment1_to_fragment3"
                  app:destination="@id/fragment3" />
            """.trimIndent())
        }
      }
    }
  }

  @Test
  fun canNavigateToXmlTagFromInnerActionsBuilderClass() {
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
                android:id="@+id/action_fragment1_to_fragment2"
                app:destination="@id/fragment2" >
                <argument
                  android:name="arg"
                  app:argType="string"
                  android:defaultValue="defaultString" />
                <argument
                  android:name="arg_in_action"
                  app:argType="string" />
            </action>
          </fragment>

          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2" >
            <argument
              android:name="arg"
              app:argType="string" />
            <argument
                android:name="arg_in_destination"
                app:argType="string" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    StudioResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment1 {}")

    val editors = FileEditorManager.getInstance(safeArgsRule.fixture.project)
    assertThat(editors.selectedFiles).isEmpty()

    // check actionBuilderClass navigation
    val actionBuilderClass = safeArgsRule.fixture
      .findClass("test.safeargs.Fragment1Directions.ActionFragment1ToFragment2", context) as LightActionBuilderClass
    actionBuilderClass.let {
      it.navigate(true)
      assertThat(editors.selectedFiles[0].name).isEqualTo("main.xml")
      assertThat(it.navigationElement).isInstanceOf(XmlTag::class.java)
      assertThat(it.navigationElement.text).isEqualTo(
        """
          <action
                  android:id="@+id/action_fragment1_to_fragment2"
                  app:destination="@id/fragment2" >
                  <argument
                    android:name="arg"
                    app:argType="string"
                    android:defaultValue="defaultString" />
                  <argument
                    android:name="arg_in_action"
                    app:argType="string" />
              </action>
        """.trimIndent())
    }

    // check methods navigation of actionBuilderClass
    actionBuilderClass.methods.forEach {
      it.navigate(true)
      assertThat(editors.selectedFiles[0].name).isEqualTo("main.xml")
      assertThat(it.navigationElement).isInstanceOf(XmlTag::class.java)

      // check getters and setters
      when (it.name) {
        "getArg", "setArg" -> {
          assertThat(it.navigationElement.text).isEqualTo(
            """
              <argument
                        android:name="arg"
                        app:argType="string"
                        android:defaultValue="defaultString" />
            """.trimIndent())
        }
        "getArgInAction", "setArgInAction" -> {
          assertThat(it.navigationElement.text).isEqualTo(
            """
              <argument
                        android:name="arg_in_action"
                        app:argType="string" />
            """.trimIndent())
        }
        "getArgInDestination", "setArgInDestination" -> {
          assertThat(it.navigationElement.text).isEqualTo(
            """
              <argument
                      android:name="arg_in_destination"
                      app:argType="string" />
            """.trimIndent())
        }
        else -> throw IllegalAccessError("This should never happen!")
      }
    }
  }
}