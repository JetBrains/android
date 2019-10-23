/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiFile
import com.intellij.testFramework.TestRunnerUtil
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.AndroidTestCase

class AndroidXmlTypedHandlerTest : AndroidTestCase() {
  lateinit var tester: CompletionAutoPopupTester

  override fun runInDispatchThread(): Boolean = false
  override fun invokeTestRunnable(runnable: Runnable) = tester.runWithAutoPopupEnabled(runnable)

  override fun providesCustomManifest(): Boolean = true

  override fun setUp() {
    TestRunnerUtil.replaceIdeEventQueueSafely() // See UsefulTestCase#runBare which should be the stack frame above this one.
    runInEdtAndWait { super.setUp() }
    tester = CompletionAutoPopupTester(myFixture)

    addManifest()
  }

  private fun addManifest() {
    myFixture.addFileToProject(
      "./AndroidManifest.xml",
      // language=xml
      """
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="com.example.myapplication">

        <application android:icon="$caret" >
        </application>

      </manifest>
      """.trimIndent()
    )
  }

  override fun tearDown() = runInEdtAndWait { super.tearDown() }

  fun testTagBody() {
    // Given:
    val stringsXml = myFixture.addFileToProject(
      "res/values/strings.xml",
      // language=xml
      """
      <resources>
        <string name='foo'>foo</string>
        <string name='bar'>$caret</string>
      </resources>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(stringsXml.virtualFile)

    // Given:
    typeAtCharacter()

    // Then:
    assertThat(myFixture.lookupElementStrings).containsExactly("@string/foo", "@string/bar", "@android:")
  }

  fun testAttrValueLayout() {
    // Given:
    val stringsXml = myFixture.addFileToProject(
      "res/layout/my_layout.xml",
      // language=xml
      """
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="$caret"
        android:layout_height="match_parent" />
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(stringsXml.virtualFile)

    // Given:
    typeAtCharacter()

    // Then:
    assertThat(myFixture.lookupElementStrings).containsExactly("@android:")
  }

  fun testAttrValueManifest() {
    // Given:
    myFixture.configureFromTempProjectFile("./AndroidManifest.xml")

    // Given:
    typeAtCharacter()

    // Then:
    assertThat(myFixture.lookupElementStrings).containsExactly("@android:")
  }

  fun testAttrValueStyle() {
    // Given:
    val stringsXml = myFixture.addFileToProject(
      "res/values/strings.xml",
      // language=xml
      """
      <resources>
        <style name="foo" />
        <style name="bar" parent="$caret" />
      </resources>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(stringsXml.virtualFile)

    // Given:
    typeAtCharacter()

    // Then:
    assertThat(myFixture.lookupElementStrings).containsExactly("@style/foo", "@style/bar", "@android:")
  }

  private fun typeAtCharacter() {
    myFixture.type('@')
    tester.joinAutopopup()
    tester.joinCompletion()
  }
}