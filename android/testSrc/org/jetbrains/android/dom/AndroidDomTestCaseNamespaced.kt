/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.jetbrains.android.dom

import com.android.builder.model.AaptOptions
import com.android.builder.model.AndroidProject
import com.android.tools.idea.model.TestAndroidModel
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet

class AndroidDomTestCaseNamespaced : AndroidTestCase() {

  private val libRes get() = getAdditionalModulePath("lib") + "/res"

  override fun configureAdditionalModules(
    projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
    modules: MutableList<MyAdditionalModuleData>
  ) {
    addModuleWithAndroidFacet(
      projectBuilder,
      modules,
      "lib",
      AndroidProject.PROJECT_TYPE_LIBRARY,
      true
    )
  }

  override fun setUp() {
    super.setUp()

    runUndoTransparentWriteAction {
      myFacet.run {
        configuration.model = TestAndroidModel(
          namespacing = AaptOptions.Namespacing.REQUIRED
        )

        manifest!!.`package`.value = "com.example.app"
      }

      AndroidFacet.getInstance(getAdditionalModuleByName("lib")!!)!!.run {
        configuration.model = TestAndroidModel(
          namespacing = AaptOptions.Namespacing.REQUIRED
        )

        manifest!!.`package`.value = "com.example.lib"
      }
    }

    myFixture.addFileToProject(
      "$libRes/values/strings.xml",
      // language=xml
      """
        <resources>
          <string name="hello">Hello from lib</string>
        </resources>
      """.trimIndent()
    )

  }

  fun testDifferentNamespaces() {
    val values = myFixture.addFileToProject(
      "res/values/values.xml",
      """
        <resources>
          <string name="some_string">Some string</string>
          <string name="app_string">@<caret></string>
        </resources>
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(values.virtualFile)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactly(
      "@android:",
      "@string/app_string",
      "@string/some_string",
      "@com.example.lib:string/hello"
    )
  }

  fun testDifferentNamespaces_prefix() {
    val values = myFixture.addFileToProject(
      "res/values/values.xml",
      """
        <resources xmlns:lib="http://schemas.android.com/apk/res/com.example.lib" xmlns:a="http://schemas.android.com/apk/res/android">
          <string name="some_string">Some string</string>
          <string name="app_string">@<caret></string>
        </resources>
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(values.virtualFile)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactly(
      "@a:",
      "@string/app_string",
      "@string/some_string",
      "@lib:string/hello"
    )

    myFixture.type("a:")
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).contains("@a:string/cancel")
  }
}
