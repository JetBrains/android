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

import com.android.SdkConstants
import com.android.builder.model.AndroidProject
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet

const val APP_PACKAGE_NAME = "com.example.app"
const val LIB_NAME = "lib"
const val LIB_PACKAGE_NAME = "com.example.lib"

abstract class XmlNamespaceCompletionTest : AndroidTestCase() {

  override fun setUp() {
    super.setUp()

    enableNamespacing(APP_PACKAGE_NAME)
  }

  class NoLibs : XmlNamespaceCompletionTest() {

    fun testValuesResources(){
      myFixture.configureFromExistingVirtualFile(
        myFixture.addFileToProject(
          "res/values/values.xml",
          // language=xml
          """
          <resources xmlns:foo="${caret}">
            <string name="some_string">Some string</string>
          </resources>
          """.trimIndent()
        ).virtualFile
      )

      myFixture.completeBasic()

      assertThat(myFixture.lookupElementStrings).containsExactly(
        SdkConstants.ANDROID_URI,
        SdkConstants.TOOLS_URI,
        SdkConstants.URI_PREFIX + APP_PACKAGE_NAME,
        SdkConstants.XLIFF_URI
      )
    }

    fun testDrawableResources() {
      myFixture.configureFromExistingVirtualFile(
        myFixture.addFileToProject(
        "res/drawable/drawable.xml",
          // language=xml
          """
          <bitmap xmlns:foo="${caret}"/>
          """.trimIndent()
        ).virtualFile
      )

      myFixture.completeBasic()

      // Then
      assertThat(myFixture.lookupElementStrings).containsExactly(
        SdkConstants.ANDROID_URI,
        SdkConstants.TOOLS_URI,
        SdkConstants.URI_PREFIX + APP_PACKAGE_NAME,
        SdkConstants.AAPT_URI
      )
    }

    fun testMipmapResources() {
      myFixture.configureFromExistingVirtualFile(
        myFixture.addFileToProject(
        "res/mipmap/mipmap.xml",
          // language=xml
          """
          <adaptive-icon xmlns:foo="${caret}">
          </adaptive-icon>
          """.trimIndent()
        ).virtualFile
      )

      myFixture.completeBasic()

      assertThat(myFixture.lookupElementStrings).containsExactly(
        SdkConstants.ANDROID_URI,
        SdkConstants.TOOLS_URI
      )
    }

    fun testLayoutResources() {
      myFixture.configureFromExistingVirtualFile(
        myFixture.addFileToProject(
        "res/layout/layout.xml",
          // language=xml
          """
          <android.support.constraint.ConstraintLayout xmlns:foo="${caret}">
          </android.support.constraint.ConstraintLayout>
          """.trimIndent()
        ).virtualFile
      )

      myFixture.completeBasic()

      assertThat(myFixture.lookupElementStrings).containsExactly(
        SdkConstants.ANDROID_URI,
        SdkConstants.TOOLS_URI,
        SdkConstants.URI_PREFIX + APP_PACKAGE_NAME
      )
    }
  }

  class OneLib : XmlNamespaceCompletionTest() {

    override fun configureAdditionalModules(
      projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
      modules: MutableList<MyAdditionalModuleData>
    ) {
      addModuleWithAndroidFacet(
        projectBuilder,
        modules,
        LIB_NAME,
        AndroidProject.PROJECT_TYPE_LIBRARY
      )
    }

    override fun setUp() {
      super.setUp()

      enableNamespacing(AndroidFacet.getInstance(getAdditionalModuleByName(LIB_NAME)!!)!!, LIB_PACKAGE_NAME)
    }

    fun testValuesResources() {
      myFixture.configureFromExistingVirtualFile(
        myFixture.addFileToProject(
        "res/values/values.xml",
          // language=xml
          """
          <resources xmlns:foo="${caret}">
            <string name="some_string">Some string</string>
          </resources>
          """.trimIndent()
        ).virtualFile
      )

      myFixture.completeBasic()

      assertThat(myFixture.lookupElementStrings).containsExactly(
        SdkConstants.ANDROID_URI,
        SdkConstants.TOOLS_URI,
        SdkConstants.URI_PREFIX + APP_PACKAGE_NAME,
        SdkConstants.XLIFF_URI,
        SdkConstants.URI_PREFIX + LIB_PACKAGE_NAME
      )
    }

    fun testDrawableResources() {
      myFixture.configureFromExistingVirtualFile(
        myFixture.addFileToProject(
        "res/drawable/drawable.xml",
          // language=xml
          """
          <bitmap xmlns:foo="${caret}"/>
          """.trimIndent()
        ).virtualFile
      )

      myFixture.completeBasic()

      assertThat(myFixture.lookupElementStrings).containsExactly(
        SdkConstants.ANDROID_URI,
        SdkConstants.TOOLS_URI,
        SdkConstants.URI_PREFIX + APP_PACKAGE_NAME,
        SdkConstants.AAPT_URI,
        SdkConstants.URI_PREFIX + LIB_PACKAGE_NAME
      )
    }

    fun testMipmapResources() {
      myFixture.configureFromExistingVirtualFile(
        myFixture.addFileToProject(
        "res/mipmap/mipmap.xml",
          // language=xml
          """
          <adaptive-icon xmlns:foo="${caret}">
          </adaptive-icon>
          """.trimIndent()
        ).virtualFile
      )

      myFixture.completeBasic()

      assertThat(myFixture.lookupElementStrings).containsExactly(
        SdkConstants.ANDROID_URI,
        SdkConstants.TOOLS_URI
      )
    }

    fun testLayoutResources() {
      myFixture.configureFromExistingVirtualFile(
        myFixture.addFileToProject(
        "res/layout/layout.xml",
          // language=xml
          """
          <android.support.constraint.ConstraintLayout xmlns:foo="${caret}">
          </android.support.constraint.ConstraintLayout>
          """.trimIndent()
        ).virtualFile
      )

      myFixture.completeBasic()

      assertThat(myFixture.lookupElementStrings).containsExactly(
        SdkConstants.ANDROID_URI,
        SdkConstants.TOOLS_URI,
        SdkConstants.URI_PREFIX + APP_PACKAGE_NAME,
        SdkConstants.URI_PREFIX + LIB_PACKAGE_NAME
      )
    }
  }
}
