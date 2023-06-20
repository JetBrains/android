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
package com.android.tools.idea.databinding

import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.databinding.psiclass.LightBindingClass
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.findClass
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiAnchor
import com.intellij.psi.PsiClass
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests that verify navigating between data binding components work
 */
@RunsInEdt
@RunWith(Parameterized::class)
class DataBindingNavigationTests(private val mode: DataBindingMode) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}")
    val modes = listOf(DataBindingMode.SUPPORT, DataBindingMode.ANDROIDX)
  }

  private val projectRule = AndroidProjectRule.onDisk()

  // We want to run tests on the EDT thread, but we also need to make sure the project rule is not
  // initialized on the EDT.
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  /**
   * Expose the underlying project rule fixture directly.
   *
   * We know that the underlying fixture is a [JavaCodeInsightTestFixture] because our
   * [AndroidProjectRule] is initialized to use the disk.
   */
  private val fixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

  private val androidFacet
    get() = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)!!


  @Before
  fun setUp() {
    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    fixture.addFileToProject("AndroidManifest.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.db">
        <application />
      </manifest>
    """.trimIndent())

    LayoutBindingModuleCache.getInstance(androidFacet).dataBindingMode = mode
  }

  @Test
  fun canNavigateToXmlFromLightBindingClass() {
    fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="strValue" type="String"/>
          <variable name="intValue" type="Integer"/>
        </data>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val editors = FileEditorManager.getInstance(fixture.project)
    assertThat(editors.selectedFiles).isEmpty()
    // ActivityMainBinding is in-memory and generated on the fly from activity_main.xml
    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    binding.navigate(true)
    assertThat(editors.selectedFiles[0].name).isEqualTo("activity_main.xml")

    // Regression test for 261536892: PsiAnchor.create throws assertion error for LightBindingClass navigation element.
    PsiAnchor.create(binding.navigationElement)
  }

  @Test
  fun canNavigateToXmlFromGeneratedViewFieldInLightClass() {
    fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="strValue" type="String"/>
          <variable name="intValue" type="Integer"/>
        </data>
        <LinearLayout
            android:id="@+id/test_id"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
          </LinearLayout>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val editors = FileEditorManager.getInstance(fixture.project)
    assertThat(editors.selectedFiles).isEmpty()
    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    val field = binding.fields[0]
    field.navigate(true)
    assertThat(editors.selectedFiles[0].name).isEqualTo("activity_main.xml")
    fixture.openFileInEditor(editors.selectedFiles[0])
    val element = fixture.file.findElementAt(fixture.editor.caretModel.offset)
    assertThat(element!!.parent.text).isEqualTo("""
      <LinearLayout
            android:id="@+id/test_id"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
          </LinearLayout>
    """.trimIndent())
  }

  @Test
  fun canNavigateFromVariableTypeToInnerClassOfImportAlias() {
    val file = fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout>
        <data>
          <import type='java.util.Map' alias='MyMap'/>
          <variable name='sample' type='MyMap.En${caret}try'/>
        </data>
      </layout>
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.virtualFile)
    assertThat((fixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("java.util.Map.Entry")
  }

  @Test
  fun canNavigateFromImportType() {
    val file = fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout>
        <data>
          <import type='java.util.M${caret}ap' />
        </data>
      </layout>
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.virtualFile)
    assertThat((fixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("java.util.Map")
  }

  @Test
  fun canNavigateFromVariableTypeToJavaLangClass() {
    val file = fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout>
        <data>
          <variable name='sample' type='Int${caret}eger'/>
        </data>
      </layout>
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.virtualFile)
    assertThat((fixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("java.lang.Integer")
  }

  @Test
  fun canNavigateFromVariableTypeToModuleClass() {
    fixture.addClass(
      // language=JAVA
    """
      package a.b.c;
      class Sample {}
    """.trimIndent())

    val file = fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout>
        <data>
          <variable name='sample' type='a.b.c.Samp${caret}le'/>
        </data>
      </layout>
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.virtualFile)
    assertThat((fixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("a.b.c.Sample")

  }
}