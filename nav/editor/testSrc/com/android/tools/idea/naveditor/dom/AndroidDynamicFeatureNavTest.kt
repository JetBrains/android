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
package com.android.tools.idea.naveditor.dom

import com.android.tools.idea.naveditor.NavEditorRule
import com.android.tools.idea.naveditor.addDynamicFeatureModule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.waitForResourceRepositoryUpdates
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.psi.PsiClass
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.intellij.lang.annotations.Language
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class AndroidDynamicFeatureNavTest {
  private val DYNAMIC_FEATURE_MODULE_NAME = "dynamicfeaturemodule"

  @get:Rule
  val edtRule = EdtRule()
  private val projectRule = AndroidProjectRule.withSdk()
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(NavEditorRule(projectRule))

  val myFixture: JavaCodeInsightTestFixture
    get() = projectRule.fixture

  @Before
  fun setUp() {
    addDynamicFeatureModule(DYNAMIC_FEATURE_MODULE_NAME, projectRule.module, myFixture)
    addFragment("fragment1", null)
    addFragment("fragment2", null)
    addFragment("fragment3", null)
    addFragment("dynamicFragment", DYNAMIC_FEATURE_MODULE_NAME)
    addActivity("activity1", null)
    addActivity("dynamicActivity", DYNAMIC_FEATURE_MODULE_NAME)
  }

  @Test
  fun testToolsLayoutFragmentCompletion() {
    @Language("XML") val navGraph = """
      <navigation xmlns:app="http://schemas.android.com/apk/res-auto"
          xmlns:tools="http://schemas.android.com/tools"
          xmlns:android="http://schemas.android.com/apk/res/android"
          app:startDestination="@id/blankFragment">
          <fragment
              android:id="@+id/blankFragment"
              android:name="mytest.navtest.dynamicFragment"
              android:label="Blank"
              tools:layout="@layo${caret}" />
      </navigation>""".trimIndent()
    val psiFile = projectRule.fixture.addFileToProject("res/navigation/nav_graph2.xml", navGraph)
    waitForResourceRepositoryUpdates(projectRule.module)
    projectRule.fixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    projectRule.fixture.completeBasic()
    assertThat(projectRule.fixture.lookupElementStrings).containsExactly(
      "@layout/activity1",
      "@layout/dynamicActivity",
      "@layout/activity_main",
      "@layout/dynamicFragment",
      "@layout/fragment1",
      "@layout/fragment2",
      "@layout/fragment3",
      "@layout/fragment_blank")
  }

  @Test
  fun testToolsLayoutFragmentGotoDeclaration() {
    @Language("XML") val navGraph = """
      <navigation xmlns:app="http://schemas.android.com/apk/res-auto"
          xmlns:tools="http://schemas.android.com/tools"
          xmlns:android="http://schemas.android.com/apk/res/android"
          app:startDestination="@id/blankFragment">
          <fragment
              android:id="@+id/blankFragment"
              android:name="mytest.navtest.dynamicFragment"
              android:label="Blank"
              tools:layout="@layout/dynamic${caret}Fragment" />
      </navigation>""".trimIndent()
    val psiFile = myFixture.addFileToProject("res/navigation/nav_graph2.xml", navGraph)
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    val targetElements = GotoDeclarationAction.findAllTargetElements(myFixture.project, myFixture.editor, myFixture.caretOffset)
    assertThat(targetElements).hasLength(1)
    assertThat(targetElements[0]).isInstanceOf(XmlFile::class.java)
    assertThat((targetElements[0] as XmlFile).name).isEqualTo("dynamicFragment.xml")
  }

  @Test
  fun testFragmentCompletion() {
    @Language("XML") val navGraph = """
      <navigation xmlns:app="http://schemas.android.com/apk/res-auto"
          xmlns:tools="http://schemas.android.com/tools"
          xmlns:android="http://schemas.android.com/apk/res/android"
          app:startDestination="@id/blankFragment">
          <fragment
              android:id="@+id/blankFragment"
              android:name="$caret"
              android:label="Blank"
              tools:layout="@layout/fragment_blank" />
      </navigation>""".trimIndent()
    val psiFile = myFixture.addFileToProject("res/navigation/nav_graph1.xml", navGraph)
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactly(
      "mytest.navtest.BlankFragment",
      "mytest.navtest.fragment1",
      "mytest.navtest.fragment2",
      "mytest.navtest.fragment3",
      "mytest.navtest.dynamicFragment")
  }

  @Test
  fun testFragmentGotoDeclaration() {
    @Language("XML") val navGraph = """
      <navigation xmlns:app="http://schemas.android.com/apk/res-auto"
          xmlns:android="http://schemas.android.com/apk/res/android"
          app:startDestination="@id/blankFragment">
          <fragment
              android:id="@+id/blankFragment"
              android:name="mytest.navtest.dynamic${caret}Fragment"
              android:label="Blank" />
      </navigation>""".trimIndent()
    val psiFile = myFixture.addFileToProject("res/navigation/nav_graph2.xml", navGraph)
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    val targetElements = GotoDeclarationAction.findAllTargetElements(myFixture.project, myFixture.editor, myFixture.caretOffset)
    assertThat(targetElements).hasLength(1)
    assertThat(targetElements[0]).isInstanceOf(PsiClass::class.java)
    assertThat((targetElements[0] as PsiClass).qualifiedName).isEqualTo("mytest.navtest.dynamicFragment")
  }

  @Test
  fun testActivityCompletion() {
    @Language("XML") val navGraph = """
      <navigation xmlns:app="http://schemas.android.com/apk/res-auto"
          xmlns:android="http://schemas.android.com/apk/res/android"
          app:startDestination="@id/blankActivity">
          <activity
              android:id="@+id/blankActivity"
              android:name="$caret"
              android:label="Blank" />
      </navigation>""".trimIndent()
    val psiFile = myFixture.addFileToProject("res/navigation/nav_graph4.xml", navGraph)
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactly(
      "mytest.navtest.MainActivity",
      "mytest.navtest.activity1"
    )
  }

  @Test
  fun testActivityGotoDeclaration() {
    @Language("XML") val navGraph = """
      <navigation xmlns:app="http://schemas.android.com/apk/res-auto"
          xmlns:tools="http://schemas.android.com/tools"
          xmlns:android="http://schemas.android.com/apk/res/android"
          app:startDestination="@id/blankFragment">
          <activity
              android:id="@+id/blankFragment"
              android:name="mytest.navtest.dynamic${caret}Activity"
              android:label="Blank"
              tools:layout="@layout/fragment_blank" />
      </navigation>""".trimIndent()
    val psiFile = myFixture.addFileToProject("res/navigation/nav_graph2.xml", navGraph)
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    val targetElements = GotoDeclarationAction.findAllTargetElements(myFixture.project, myFixture.editor, myFixture.caretOffset)
    assertThat(targetElements).hasLength(1)
    assertThat(targetElements[0]).isInstanceOf(PsiClass::class.java)
    assertThat((targetElements[0] as PsiClass).qualifiedName).isEqualTo("mytest.navtest.dynamicActivity")
  }

  private fun addFragment(name: String, moduleName: String?) {
    val sourcePath = if (moduleName == null) {
      "src/mytest/navtest/$name.java"
    } else {
      "${moduleName}/$name.java"
    }
    val fileText = """
      package mytest.navtest;
      import android.support.v4.app.Fragment;

      public class $name extends Fragment {}
      """.trimIndent()
    myFixture.addFileToProject(sourcePath, fileText)
    addLayout(name, moduleName)
  }

  private fun addActivity(name: String, moduleName: String?) {
    val sourcePath = if (moduleName == null) {
      "src/$name.java"
    } else {
      "${moduleName}/src/$name.java"
    }
    val fileText = """
      package mytest.navtest;
      import android.app.Activity;

      public class $name extends Activity {
      }
      """.trimIndent()
    myFixture.addFileToProject(sourcePath, fileText)
    addLayout(name, moduleName)
  }

  private fun addLayout(name: String, moduleName: String?) {
    val layoutPath =  if (moduleName == null) {
      "res/layout/$name.xml"
    } else {
      "${moduleName}/res/layout/$name.xml"
    }
    val layoutText = """
      <LinearLayout
        xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent">
      </LinearLayout>
      """.trimIndent()
    myFixture.addFileToProject(layoutPath, layoutText)
  }
}