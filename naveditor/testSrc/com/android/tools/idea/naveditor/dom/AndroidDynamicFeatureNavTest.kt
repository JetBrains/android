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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.addDynamicFeatureModule
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.psi.PsiClass
import org.intellij.lang.annotations.Language

class AndroidDynamicFeatureNavTest : NavTestCase() {
  private val DYNAMIC_FEATURE_MODULE_NAME = "dynamicfeaturemodule"

  override fun setUp() {
    super.setUp()
    StudioFlags.NAV_DYNAMIC_SUPPORT.override(true)
    addDynamicFeatureModule(DYNAMIC_FEATURE_MODULE_NAME, myModule, myFixture)
    addFragment("fragment1")
    addFragment("fragment2")
    addFragment("fragment3")
    addFragment("dynamicFragment", DYNAMIC_FEATURE_MODULE_NAME)
  }

  override fun tearDown() {
    StudioFlags.NAV_DYNAMIC_SUPPORT.clearOverride()
    super.tearDown()
  }

  fun testFragmentCompletion() {
    @Language("XML") val navGraph = """
      <navigation xmlns:app="http://schemas.android.com/apk/res-auto"
          xmlns:tools="http://schemas.android.com/tools"
          xmlns:android="http://schemas.android.com/apk/res/android"
          app:startDestination="@id/blankFragment">
          <fragment
              android:id="@+id/blankFragment"
              android:name="${caret}"
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

  fun testFragmentGotoDeclaration() {
    @Language("XML") val navGraph = """
      <navigation xmlns:app="http://schemas.android.com/apk/res-auto"
          xmlns:tools="http://schemas.android.com/tools"
          xmlns:android="http://schemas.android.com/apk/res/android"
          app:startDestination="@id/blankFragment">
          <fragment
              android:id="@+id/blankFragment"
              android:name="mytest.navtest.dynamic${caret}Fragment"
              android:label="Blank"
              tools:layout="@layout/fragment_blank" />
      </navigation>""".trimIndent()
    val psiFile = myFixture.addFileToProject("res/navigation/nav_graph2.xml", navGraph)
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    val targetElements = GotoDeclarationAction.findAllTargetElements(myFixture.project, myFixture.editor, myFixture.caretOffset)
    assertThat(targetElements).hasLength(1)
    assertThat(targetElements[0]).isInstanceOf(PsiClass::class.java)
    assertThat((targetElements[0] as PsiClass).qualifiedName).isEqualTo("mytest.navtest.dynamicFragment")
  }

  private fun addFragment(name: String, folder: String = "src/mytest/navtest") {
    val relativePath = "$folder/$name.java"
    val fileText = """
      .package mytest.navtest;
      .import android.support.v4.app.Fragment;
      .
      .public class $name extends Fragment {
      .}
      """.trimMargin(".")

    myFixture.addFileToProject(relativePath, fileText)
  }
}