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
package org.jetbrains.android.refactoring

import com.android.builder.model.AndroidProject
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet

class MigrateToResourceNamespacesProcessorTest : AndroidTestCase() {

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
      myFacet.manifest!!.`package`.value = "com.example.app"
      AndroidFacet.getInstance(getAdditionalModuleByName("lib")!!)!!.manifest!!.`package`.value = "com.example.lib"
    }
  }

  fun testResourceValues() {
    myFixture.addFileToProject(
      "${getAdditionalModulePath("lib") + "/res"}/values/lib.xml",
      // language=xml
      """
        <resources>
          <string name="libString">Hello from lib</string>
        </resources>
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "/res/values/app.xml",
      // language=xml
      """
        <resources>
          <string name="appString">Hello from app</string>
          <string name="s1">@string/appString</string>
          <string name="s2">@string/libString</string>
        </resources>
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "/res/layout/layout.xml",
      // language=xml
      """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
          <TextView android:text="@string/appString" />
          <TextView android:text="@string/libString" />
        </LinearLayout>
      """.trimIndent()
    )

    MigrateToResourceNamespacesProcessor(myFacet).run()

    myFixture.checkResult(
      "res/values/app.xml",
      // language=xml
      """
        <resources>
          <string name="appString">Hello from app</string>
          <string name="s1">@string/appString</string>
          <string name="s2">@com.example.lib:string/libString</string>
        </resources>
      """.trimIndent(),
      true
    )

    myFixture.checkResult(
      "/res/layout/layout.xml",
      // language=xml
      """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
          <TextView android:text="@string/appString" />
          <TextView android:text="@com.example.lib:string/libString" />
        </LinearLayout>
      """.trimIndent(),
      true
    )
  }
}
