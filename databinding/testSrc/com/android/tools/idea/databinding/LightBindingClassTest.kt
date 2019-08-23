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

import com.android.ide.common.resources.stripPrefixFromId
import com.android.tools.idea.databinding.DataBindingUtil.parsePsiType
import com.android.tools.idea.databinding.psiclass.LightBindingClass
import com.android.tools.idea.databinding.utils.findClass
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.ui.UIUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Tests that verify that navigating between data binding components.
 */
@RunsInEdt
class LightBindingClassTest {
  private val projectRule = AndroidProjectRule.onDisk()

  // We want to run tests on EDT, but we also need to make sure the project rule is not initialized on EDT.
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

  private val facet
    get() = projectRule.module.androidFacet!!

  private val project
    get() = projectRule.project

  private fun insertXml(psiFile: PsiFile, offset: Int, xml: String) {
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(psiFile)!!
    WriteCommandAction.runWriteCommandAction(project) {
      document.insertString(offset, xml)
      documentManager.commitDocument(document)
    }
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun deleteXml(psiFile: PsiFile, range: TextRange) {
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(psiFile)!!
    WriteCommandAction.runWriteCommandAction(project) {
      document.deleteString(range.startOffset, range.endOffset)
      documentManager.commitDocument(document)
    }
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun updateXml(psiFile: PsiFile, range: TextRange, xml: String) {
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(psiFile)!!
    WriteCommandAction.runWriteCommandAction(project) {
      document.replaceString(range.startOffset, range.endOffset, xml)
      documentManager.commitDocument(document)
    }
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun findChild(psiFile: PsiFile, clazz: Class<out XmlElement>, predicate: (XmlTag) -> Boolean): Array<XmlTag> {
    return PsiTreeUtil.findChildrenOfType(psiFile, clazz).filterIsInstance<XmlTag>().filter(predicate).toTypedArray()
  }

  private fun verifyLightFieldsMatchXml(fields: List<PsiField>, vararg tags: XmlTag) {
    val fieldIds = fields.map(PsiField::getName).toList()
    val tagIds = tags.map { DataBindingUtil.convertToJavaFieldName(stripPrefixFromId(it.getAttribute ("android:id")!!.value!!)) }
      .toList()
    assertThat(fieldIds).isEqualTo(tagIds)
  }

  @Before
  fun setUp() {
    fixture.addFileToProject("AndroidManifest.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.db">
        <application />
      </manifest>
    """.trimIndent())

    ModuleDataBinding.getInstance(facet).setMode(DataBindingMode.ANDROIDX)
  }

  @Test
  fun lightClassContainsFieldByIndex() {
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:id="@+id/test_id"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
        </LinearLayout>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    val fields = binding.fields
    val tags = findChild(file, XmlTag::class.java) { it.localName == "LinearLayout" }
    verifyLightFieldsMatchXml(fields.toList(), *tags)
  }

  @Test
  fun addViewRefreshesLightClassFields() {
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:id="@+id/test_id"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
        </LinearLayout>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    assertThat(binding.fields).hasLength(1)

    val tag = findChild(file, XmlTag::class.java) { it.localName == "LinearLayout" }[0]
    insertXml(file, tag.textRange.endOffset, """
      <LinearLayout
            android:id="@+id/test_id2"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
        </LinearLayout>
    """.trimIndent())

    val tags = findChild(file, XmlTag::class.java) { it.name == "LinearLayout" }
    verifyLightFieldsMatchXml(binding.fields.toList(), *tags)
  }

  @Test
  fun removeViewRefreshesLightClassFields() {
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:id="@+id/test_id"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
        </LinearLayout>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    assertThat(binding.fields).hasLength(1)

    val tag = findChild(file, XmlTag::class.java) { it.localName == "LinearLayout" }[0]
    deleteXml(file, tag.textRange)

    assertThat(binding.fields).isEmpty()
  }

  @Test
  fun updateIdRefreshesLightClassFields() {
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:id="@+id/test_id"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
        </LinearLayout>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    assertThat(binding.fields).hasLength(1)

    val attribute = PsiTreeUtil.findChildrenOfType(file, XmlAttribute::class.java)
      .filter { it is XmlAttribute && it.localName == "id" }[0] as XmlAttribute
    updateXml(file, attribute.valueElement!!.valueTextRange, "@+id/updated_id")

    val tags = findChild(file, XmlTag::class.java) { it.localName == "LinearLayout" }
    verifyLightFieldsMatchXml(binding.fields.toList(), *tags)
  }

  @Test
  fun createViewFieldWithJavaType() {
    fixture.addFileToProject("src/java/com/example/Test.java", """
      package com.example;
      class Test {}
    """.trimIndent())
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <view
            android:id="@+id/test_id"
            android:class="com.example.Test"/>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    val fields = binding.fields
    assertThat(fields).hasLength(1)
    val tags = findChild(file, XmlTag::class.java) { it.localName == "view" }
    verifyLightFieldsMatchXml(fields.toList(), *tags)

    assertThat(fields[0].type).isEqualTo(parsePsiType("com.example.Test", facet, null))
  }

  @Test
  fun createMergeFieldWithTargetLayoutType() {
    fixture.addFileToProject("res/layout/other_activity.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
      </layout>
    """.trimIndent())
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <merge
            android:id="@+id/test_id"
            android:layout="@layout/other_activity"/>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    // initialize app resources
    ResourceRepositoryManager.getAppResources(facet)

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    val fields = binding.fields
    assertThat(fields).hasLength(1)
    val tags = findChild(file, XmlTag::class.java) { it.localName == "merge" }
    verifyLightFieldsMatchXml(fields.toList(), *tags)

    assertThat(fields[0].type).isEqualTo(parsePsiType("test.db.databinding.OtherActivityBinding", facet, null))
  }

  @Test
  fun createIncludeFieldWithTargetLayoutType() {
    fixture.addFileToProject("res/layout/other_activity.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
      </layout>
    """.trimIndent())
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <include
            android:id="@+id/test_id"
            android:layout="@layout/other_activity"/>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    // initialize app resources
    ResourceRepositoryManager.getAppResources(facet)

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    val fields = binding.fields
    assertThat(fields).hasLength(1)
    val tags = findChild(file, XmlTag::class.java) { it.localName == "include" }
    verifyLightFieldsMatchXml(fields.toList(), *tags)

    assertThat(fields[0].type).isEqualTo(parsePsiType("test.db.databinding.OtherActivityBinding", facet, null))
  }
}