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

import com.android.AndroidProjectTypes
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.goToElementAtCaret
import com.android.tools.idea.testing.highlightedAs
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnusedNamespaceInspection
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import org.jetbrains.android.AndroidGotoDeclarationHandlerTestBase
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.dom.inspections.AndroidDomInspection
import org.jetbrains.android.dom.inspections.AndroidElementNotAllowedInspection
import org.jetbrains.android.dom.inspections.AndroidUnknownAttributeInspection
import org.jetbrains.android.facet.AndroidFacet

/**
 * Tests for code editor features when working with value resources XML files in namespaced projects.
 *
 * Namespaced equivalent of [AndroidValueResourcesTest], covers features that have been fixed to work in namespaced projects.
 */
class AndroidNamespacedValueResourcesDomTest : AndroidTestCase() {

  private val libRes get() = getAdditionalModulePath("lib") + "/res"

  override fun configureAdditionalModules(
    projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
    modules: MutableList<MyAdditionalModuleData>
  ) {
    addModuleWithAndroidFacet(
      projectBuilder,
      modules,
      "lib",
      AndroidProjectTypes.PROJECT_TYPE_LIBRARY,
      true
    )
  }

  override fun setUp() {
    super.setUp()

    myFixture.enableInspections(
      AndroidDomInspection::class.java,
      AndroidUnknownAttributeInspection::class.java,
      AndroidElementNotAllowedInspection::class.java,
      XmlUnusedNamespaceInspection::class.java
    )

    enableNamespacing(myFacet, "com.example.app")
    enableNamespacing(AndroidFacet.getInstance(getAdditionalModuleByName("lib")!!)!!, "com.example.lib")

    myFixture.addFileToProject(
      "$libRes/values/strings.xml",
      // language=xml
      """
        <!--suppress ALL -->
        <resources>
          <string name="hello">Hello from lib</string>
        </resources>
      """.trimIndent()
    )

    // Some tests trigger a large list of possible completions.
    Registry.get("ide.completion.variant.limit").setValue(2000, testRootDisposable)
  }

  fun testDifferentNamespacesCompletion() {
    val values = myFixture.addFileToProject(
      "res/values/values.xml",
      """
        <resources>
          <string name="some_string">Some string</string>
          <string name="app_string">@${caret}</string>
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

  fun testNamespaceReferenceGotoDeclarationValues() {
    val values = myFixture.addFileToProject(
      "res/values/values.xml",
      """
        <resources>
          <string name="app_string">@com.ex${caret}ample.lib:string/hello</string>
        </resources>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(values.virtualFile)

    AndroidGotoDeclarationHandlerTestBase.navigateToElementAtCaretFromDifferentFile(myFixture)
    val elementAtCurrentOffset = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!!
    assertThat(elementAtCurrentOffset.containingFile.name).isEqualTo("AndroidManifest.xml")
    assertThat(elementAtCurrentOffset.text).isEqualTo("package")
    assertThat(elementAtCurrentOffset.parentOfType<XmlTag>()!!.text).isEqualTo(
      """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="com.example.lib">
            <application android:icon="@drawable/icon">
            </application>
        </manifest>
      """.trimIndent())
  }

  fun testNamespaceReferenceGotoDeclarationLayout() {
    val layout = myFixture.addFileToProject("res/layout/activity_main.xml",
      // language=xml
      """
      <LinearLayout
        xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <TextView
          android:id="@+id/textView"
          android:layout_height="40dp"
          android:layout_width="match_parent"
          android:text="@com.ex${caret}ample.lib:string/hello"/>
      </LinearLayout>
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(layout.virtualFile)

    AndroidGotoDeclarationHandlerTestBase.navigateToElementAtCaretFromDifferentFile(myFixture)
    val elementAtCurrentOffset = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!!
    assertThat(elementAtCurrentOffset.containingFile.name).isEqualTo("AndroidManifest.xml")
    assertThat(elementAtCurrentOffset.text).isEqualTo("package")
    assertThat(elementAtCurrentOffset.parentOfType<XmlTag>()!!.text).isEqualTo(
      """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="com.example.lib">
            <application android:icon="@drawable/icon">
            </application>
        </manifest>
      """.trimIndent())
  }

  fun testDifferentNamespacesResolution() {
    val values = myFixture.addFileToProject(
      "res/values/values.xml",
      """
        <resources>
          <string name="s1">@android:color/black</string>
          <string name="s2">@com.example.lib:string/hello</string>
          <string name="s3">@string/s1</string>
          <string name="s4">${"@android:string/made_up" highlightedAs ERROR}</string>
          <string name="s5">${"@string/made_up" highlightedAs ERROR}</string>
          <string name="s6">${"@com.example.lib:string/made_up" highlightedAs ERROR}</string>
          <string name="s7">${"@${"made_up" highlightedAs ERROR}:string/s1" highlightedAs ERROR}</string>
        </resources>
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(values.virtualFile)
    myFixture.checkHighlighting(true, false, false)
  }

  fun testDifferentNamespacesPrefixCompletion() {
    val values = myFixture.addFileToProject(
      "res/values/values.xml",
      """
        <resources xmlns:lib="http://schemas.android.com/apk/res/com.example.lib" xmlns:a="http://schemas.android.com/apk/res/android">
          <string name="some_string">Some string</string>
          <string name="app_string">@${caret}</string>
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

  fun testDifferentNamespacesPrefixResolution() {
    val values = myFixture.addFileToProject(
      "res/values/values.xml",
      """
        <resources xmlns:lib="http://schemas.android.com/apk/res/com.example.lib" xmlns:a="http://schemas.android.com/apk/res/android">
          <string name="s1">@a:color/black</string>
          <string name="s2">@lib:string/hello</string>
          <string name="s3">@string/s1</string>
          <string name="s4">${"@a:string/made_up" highlightedAs ERROR}</string>
          <string name="s5">${"@string/made_up" highlightedAs ERROR}</string>
          <string name="s6">${"@lib:string/made_up" highlightedAs ERROR}</string>
          <string name="s7">${"@${"made_up" highlightedAs ERROR}:string/s1" highlightedAs ERROR}</string>
        </resources>
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(values.virtualFile)
    myFixture.checkHighlighting(true, false, false)
  }

  fun testNamespacePrefixReferences_localXmlNs() {
    val values = myFixture.addFileToProject(
      "res/values/values.xml",
      """
        <resources xmlns:lib="http://schemas.android.com/apk/res/com.example.lib">
          <string name="some_string">Some string</string>
          <string name="app_string">@${caret}lib:string/hello</string>
        </resources>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(values.virtualFile)
    myFixture.checkHighlighting()

    myFixture.renameElementAtCaret("newName")
    myFixture.checkResult(
      """
        <resources xmlns:newName="http://schemas.android.com/apk/res/com.example.lib">
          <string name="some_string">Some string</string>
          <string name="app_string">@${caret}newName:string/hello</string>
        </resources>
      """.trimIndent()
    )

    myFixture.goToElementAtCaret()
    myFixture.checkResult(
      """
        <resources xmlns:${caret}newName="http://schemas.android.com/apk/res/com.example.lib">
          <string name="some_string">Some string</string>
          <string name="app_string">@newName:string/hello</string>
        </resources>
      """.trimIndent()
    )
  }

  fun testNamespacePrefixReferences_packageName() {
    val values = myFixture.addFileToProject(
      "res/values/values.xml",
      """
        <resources>
          <string name="some_string">Some string</string>
          <string name="app_string">@${caret}com.example.lib:string/hello</string>
        </resources>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(values.virtualFile)
    myFixture.checkHighlighting()

    myFixture.goToElementAtCaret()
    myFixture.checkResult(
      """
        <resources>
          <string name="some_string">Some string</string>
          <string name="app_string">@${caret}com.example.lib:string/hello</string>
        </resources>
      """.trimIndent()
    )
  }

  fun testNamespacePrefixReferences_packageNameAfterResourceType() {
    // Regression test for b/296217029
    // The format `?<resource_type>/<package_name>:<resource_name> is allowed (even if it's not preferred), so it should resolve correctly.
    val values = myFixture.addFileToProject(
      "res/values/values.xml",
      """
        <resources>
          <string name="some_string">Some string</string>
          <string name="app_string">@string/${caret}com.example.lib:hello</string>
        </resources>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(values.virtualFile)
    myFixture.checkHighlighting()

    myFixture.goToElementAtCaret()
    myFixture.checkResult(
      """
        <resources>
          <string name="some_string">Some string</string>
          <string name="app_string">@string/${caret}com.example.lib:hello</string>
        </resources>
      """.trimIndent()
    )
  }

  fun testAttributeNames() {
    myFixture.addFileToProject(
      "$libRes/values/styles.xml",
      // language=xml
      """
        <!--suppress ALL -->
        <resources>
          <attr name='libAttr1' format='string' />
          <attr name='libAttr2' format='string' />
          <attr name='libAttr3' format='string' />
          <style name='LibStyle'>
            <item name='libAttr1'>one</item>
            <item name='libAttr2'>two</item>
          </style>
        </resources>
      """.trimIndent()
    )

    val values = myFixture.addFileToProject(
      "res/values/values.xml",
      // language=xml
      """
        <!--suppress ALL -->
        <resources xmlns:lib='http://schemas.android.com/apk/res/com.example.lib'>
          <style name='AppStyle' parent='lib:LibStyle'>
            <item name='$caret'></item>
          </style>
        </resources>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(values.virtualFile)

    val lookupStrings = myFixture.completeBasic().map { it.lookupString }
    assertThat(lookupStrings).contains("lib:libAttr1")
    assertThat(lookupStrings).contains("lib:libAttr2")
    assertThat(lookupStrings).contains("lib:libAttr3")
    assertThat(lookupStrings).contains("android:color")
  }

  fun testAttributeValues() {
    myFixture.addFileToProject(
      "$libRes/values/styles.xml",
      // language=xml
      """
        <!--suppress ALL -->
        <resources>
          <attr name='libAttr1' format='string' />
        </resources>
      """.trimIndent()
    )

    val values = myFixture.addFileToProject(
      "res/values/values.xml",
      // language=xml
      """
        <!--suppress ALL -->
        <resources xmlns:lib='http://schemas.android.com/apk/res/com.example.lib'>
          <string name='appString'>app</string>
          <style name='AppStyle'>
            <item name='lib:libAttr1'>@$caret</item>
          </style>
        </resources>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(values.virtualFile)

    val lookupStrings = myFixture.completeBasic().map { it.lookupString }

    // Make sure the "string" type of the attr is taken into account and libColor is not suggested.
    assertThat(lookupStrings).containsExactly(
      "@android:",
      "@lib:string/hello",
      "@string/appString"
    )
  }
}
