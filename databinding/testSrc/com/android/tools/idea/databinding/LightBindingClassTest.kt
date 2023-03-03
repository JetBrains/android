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

import com.android.resources.ResourceUrl
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.databinding.psiclass.LightBindingClass
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.databinding.util.LayoutBindingTypeUtil
import com.android.tools.idea.databinding.util.isViewBindingEnabled
import com.android.tools.idea.databinding.utils.assertExpected
import com.android.tools.idea.databinding.viewbinding.LightViewBindingClassTest
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findClass
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
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
    projectRule.waitForResourceRepositoryUpdates()
  }

  private fun deleteXml(psiFile: PsiFile, range: TextRange) {
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(psiFile)!!
    WriteCommandAction.runWriteCommandAction(project) {
      document.deleteString(range.startOffset, range.endOffset)
      documentManager.commitDocument(document)
    }
    projectRule.waitForResourceRepositoryUpdates()
  }

  private fun updateXml(psiFile: PsiFile, range: TextRange, xml: String) {
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(psiFile)!!
    WriteCommandAction.runWriteCommandAction(project) {
      document.replaceString(range.startOffset, range.endOffset, xml)
      documentManager.commitDocument(document)
    }
    projectRule.waitForResourceRepositoryUpdates()
  }

  private inline fun <reified X : XmlElement> findChild(psiFile: PsiFile, predicate: (X) -> Boolean): X {
    return findChildren(psiFile, predicate).first()
  }

  private inline fun <reified X : XmlElement> findChildren(psiFile: PsiFile, predicate: (X) -> Boolean): Array<X> {
    return PsiTreeUtil.findChildrenOfType(psiFile, X::class.java).filterIsInstance<X>().filter(predicate).toTypedArray()
  }

  private fun verifyLightFieldsMatchXml(fields: List<PsiField>, vararg tags: XmlTag) {
    val fieldIds = fields.map(PsiField::getName).toList()
    val tagIds = tags
      .map { tag -> tag.getAttribute("android:id")!!.value!! }
      .map { id -> DataBindingUtil.convertAndroidIdToJavaFieldName(ResourceUrl.parse(id)!!.name) }
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

    // Add fake "View" and "ViewDataBinding" classes to this project so the light binding class can resolve its super class
    val mode = DataBindingMode.ANDROIDX
    with(fixture.addFileToProject("src/android/view/View.java",
      // language=java
      """
        package android.view;

        public abstract class View {}
      """.trimIndent())) {
      fixture.allowTreeAccessForFile(this.virtualFile)
    }

    val databindingPackage = mode.packageName.removeSuffix(".") // Without trailing '.'
    with(fixture.addFileToProject(
      "src/${databindingPackage.replace('.', '/')}/ViewDataBinding.java",
      // language=java
      """
        package $databindingPackage;

        import android.view.View;

        public abstract class ViewDataBinding {
          void executePendingBindings() { }
          View getRoot() { return null; }
        }
      """.trimIndent())) {
      fixture.allowTreeAccessForFile(this.virtualFile)
    }
    LayoutBindingModuleCache.getInstance(facet).dataBindingMode = mode
  }

  @Test
  fun lightClassConstructorIsPrivate() {
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout />
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    assertThat(binding.constructors).hasLength(1)
    assertThat(binding.constructors.first().hasModifier(JvmModifier.PRIVATE))
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
    val tags = findChildren<XmlTag>(file) { it.localName == "LinearLayout" }
    verifyLightFieldsMatchXml(fields.toList(), *tags)
  }

  @Test
  fun androidIdsWithDotSyntaxAreSupported() {
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:id="@+id/test.id"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
        </LinearLayout>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    val field = binding.fields.first { it.name == "testId" }
    assertThat(field.type).isEqualTo(LayoutBindingTypeUtil.parsePsiType("android.view.LinearLayout", context))
  }

  @Test
  fun addingAndRemovingLayoutFilesUpdatesTheCache() {
    val firstFile = fixture.addFileToProject("res/layout/activity_first.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout />
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class FirstActivity {}")

    // This find forces a cache to be initialized
    fixture.findClass("test.db.databinding.ActivityFirstBinding", context) as LightBindingClass

    fixture.addFileToProject("res/layout/activity_second.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout />
      </layout>
    """.trimIndent())
    projectRule.waitForResourceRepositoryUpdates()

    // This second file should be findable, meaning the cache was updated
    fixture.findClass("test.db.databinding.ActivitySecondBinding", context) as LightBindingClass

    // Make sure alternate layouts are found by searching for its BindingImpl
    assertThat(fixture.findClass("test.db.databinding.ActivitySecondBindingLandImpl", context)).isNull()

    fixture.addFileToProject("res/layout-land/activity_second.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout />
      </layout>
    """.trimIndent())
    projectRule.waitForResourceRepositoryUpdates()

    assertThat(fixture.findClass("test.db.databinding.ActivitySecondBindingLandImpl", context)).isNotNull()

    WriteCommandAction.runWriteCommandAction(project) { firstFile.delete() }
    projectRule.waitForResourceRepositoryUpdates()
    assertThat(fixture.findClass("test.db.databinding.ActivityFirstBinding", context)).isNull()
  }

  @Test
  fun changingIncludedLayoutIsReflectedInIncludingLayoutField() {
    val includedLayoutFile = fixture.addFileToProject(
      "res/layout/included_layout.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <EditText android:id="@+id/inner_value" />
      </layout>
    """.trimIndent())

    fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
         <include
          android:id="@+id/outer_value"
          layout="@layout/included_layout" />
      </layout>
      """.trimIndent()
    )

    val context = fixture.addClass("public class ActivityMain {}")

    // Sanity check initial state

    val includedLayoutV1 = fixture.findClass("test.db.databinding.IncludedLayoutBinding", context) as LightBindingClass
    val mainLayoutV1 = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    val outerValueTypeV1 = mainLayoutV1.findFieldByName("outerValue", false)!!.type as PsiClassReferenceType
    assertThat(outerValueTypeV1.reference.resolve()).isEqualTo(includedLayoutV1)

    // Modify inner layout and sanity check that outer layout is affected

    val attr = findChild<XmlAttribute>(includedLayoutFile) { it.localName == "id" }
    updateXml(includedLayoutFile, attr.valueElement!!.valueTextRange, "@+id/inner_value_modified")

    val includedLayoutV2 = fixture.findClass("test.db.databinding.IncludedLayoutBinding", context) as LightBindingClass
    val mainLayoutV2 = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    assertThat(includedLayoutV2.findFieldByName("innerValueModified", false)).isNotNull()
    assertThat(includedLayoutV2.findFieldByName("innerValue", false)).isNull()

    val outerValueTypeV2 = mainLayoutV2.findFieldByName("outerValue", false)!!.type as PsiClassReferenceType
    assertThat(outerValueTypeV2.reference.resolve()).isEqualTo(includedLayoutV2)
    assertThat(outerValueTypeV2.reference.resolve()).isNotEqualTo(includedLayoutV1)
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

    (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
      assertThat(binding.fields).hasLength(1)

      val tag = findChild<XmlTag>(file) { it.localName == "LinearLayout" }
      insertXml(file, tag.textRange.endOffset, """
        <LinearLayout
              android:id="@+id/test_id2"
              android:orientation="vertical"
              android:layout_width="fill_parent"
              android:layout_height="fill_parent">
          </LinearLayout>
      """.trimIndent())
    }

    (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
      val tags = findChildren<XmlTag>(file) { it.name == "LinearLayout" }
      verifyLightFieldsMatchXml(binding.fields.toList(), *tags)
    }
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

    (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
      assertThat(binding.fields).hasLength(1)

      val tag = findChild<XmlTag>(file) { it.localName == "LinearLayout" }
      deleteXml(file, tag.textRange)
    }

    (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
      assertThat(binding.fields).isEmpty()
    }
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

    (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
      assertThat(binding.fields).hasLength(1)

      val attribute = findChild<XmlAttribute>(file) { it.localName == "id" }
      updateXml(file, attribute.valueElement!!.valueTextRange, "@+id/updated_id")
    }

    (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
      val tags = findChildren<XmlTag>(file) { it.localName == "LinearLayout" }
      verifyLightFieldsMatchXml(binding.fields.toList(), *tags)
    }
  }

  @Test
  fun updateVariablesRefreshesLightClassFields_withSingleLayout() {
    val file = fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name='obsolete' type='String'/>
        </data>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")
    (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
      assertThat(binding.methods.map { it.name }).containsAllOf("getObsolete", "setObsolete")
    }
    val tag = findChild<XmlTag>(file) { it.localName == "variable" }
    updateXml(file, tag.textRange,
      // language=XML
              "<variable name='first' type='Integer'/> <variable name='second' type='String'/>")
    (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
      binding.methods.map { it.name }.let { methodNames ->
        assertThat(methodNames).containsAllOf("getFirst", "setFirst", "getSecond", "setSecond")
        assertThat(methodNames).containsNoneOf("getObsolete", "setObsolete")
      }
    }
  }

  @Test
  fun updateVariablesRefreshesLightClassFields_withMultipleLayoutConfigurations() {
    val mainLayout = fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name='first' type='String'/>
        </data>
      </layout>
    """.trimIndent())
    val landscapeLayout = fixture.addFileToProject(
      "res/layout-land/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name='second' type='String'/>
        </data>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")
    (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
      assertThat(binding.methods.map { it.name }).containsAllOf("getFirst", "getSecond", "setFirst", "setSecond")
    }
    (fixture.findClass("test.db.databinding.ActivityMainBindingImpl", context) as LightBindingClass).let { binding ->
      assertThat(binding.methods.map { it.name }).containsAllOf("setFirst", "setSecond")
    }
    (fixture.findClass("test.db.databinding.ActivityMainBindingLandImpl", context) as LightBindingClass).let { binding ->
      assertThat(binding.methods.map { it.name }).containsAllOf("setFirst", "setSecond")
    }
    // Update first XML file
    run {
      val tag = findChild<XmlTag>(mainLayout) { it.localName == "variable" }
      updateXml(mainLayout, tag.textRange, "<variable name='third' type='String'/>")
      (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
        binding.methods.map { it.name }.let { methodNames ->
          assertThat(methodNames).containsAllOf("getSecond", "getThird")
          assertThat(methodNames).doesNotContain("getFirst")
        }
      }
    }
    // Update the second XML file
    run {
      val tag = findChild<XmlTag>(landscapeLayout) { it.localName == "variable" }
      updateXml(landscapeLayout, tag.textRange, "<variable name='fourth' type='String'/>")
      (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
        binding.methods.map { it.name }.let { methodNames ->
          assertThat(methodNames).containsAllOf("getThird", "getFourth")
          assertThat(methodNames).containsNoneOf("getFirst", "getSecond")
        }
      }
    }
    // Update both files at the same time
    run {
      val tagMain = findChild<XmlTag>(mainLayout) { it.localName == "variable" }
      updateXml(mainLayout, tagMain.textRange, "<variable name='fifth' type='String'/>")
      val tagLand = findChild<XmlTag>(landscapeLayout) { it.localName == "variable" }
      updateXml(landscapeLayout, tagLand.textRange, "<variable name='sixth' type='String'/>")
      (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
        binding.methods.map { it.name }.let { methodNames ->
          assertThat(methodNames).containsAllOf("getFifth", "getSixth")
          assertThat(methodNames).containsNoneOf("getFirst", "getSecond", "getThird", "getFourth")
        }
      }
    }
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
            class="com.example.Test"/>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    val fields = binding.fields
    assertThat(fields).hasLength(1)
    fields[0].let { field ->
      val modifierList = field.modifierList!!
      val nullabilityManager = NullableNotNullManager.getInstance(project)
      assertThat(nullabilityManager.isNotNull(field, false)).isTrue()
      assertThat(modifierList.hasExplicitModifier(PsiModifier.PUBLIC)).isTrue()
      assertThat(modifierList.hasExplicitModifier(PsiModifier.FINAL)).isTrue()
    }
    val tags = findChildren<XmlTag>(file) { it.localName == "view" }
    verifyLightFieldsMatchXml(fields.toList(), *tags)

    assertThat(fields[0].type).isEqualTo(LayoutBindingTypeUtil.parsePsiType("com.example.Test", context))
  }

  @Test
  fun fragmentTagsDoNotGenerateFields() {
    fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
          <View
            android:id="@+id/id_view_one"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />
          <fragment
            android:id="@+id/id_fragment"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />
          <View
            android:id="@+id/id_view_two"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />
        </LinearLayout>
      </layout>
    """.trimIndent())

    val context = fixture.addClass("public class MainActivity {}")
    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context)!!
    assertThat(binding.fields.map { it.name }).containsExactly("idViewOne", "idViewTwo")
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
            layout="@layout/other_activity"/>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    // initialize app resources
    StudioResourceRepositoryManager.getAppResources(facet)

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    val fields = binding.fields
    assertThat(fields).hasLength(1)
    val tags = findChildren<XmlTag>(file) { it.localName == "merge" }
    verifyLightFieldsMatchXml(fields.toList(), *tags)

    assertThat(fields[0].type).isEqualTo(LayoutBindingTypeUtil.parsePsiType("test.db.databinding.OtherActivityBinding", context))
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
            layout="@layout/other_activity"/>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    // initialize app resources
    StudioResourceRepositoryManager.getAppResources(facet)

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    val fields = binding.fields
    assertThat(fields).hasLength(1)
    val tags = findChildren<XmlTag>(file) { it.localName == "include" }
    verifyLightFieldsMatchXml(fields.toList(), *tags)

    assertThat(fields[0].name).isEqualTo("testId")
    assertThat(fields[0].type).isEqualTo(LayoutBindingTypeUtil.parsePsiType("test.db.databinding.OtherActivityBinding", context))
  }

  @Test
  fun createIncludeFieldWithPlainType() {
    assertThat(facet.isViewBindingEnabled()).isFalse() // Behavior of includes is slightly different if view binding is enabled

    fixture.addFileToProject("res/layout/simple_text.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <TextView xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"/>
    """.trimIndent())
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <include
            android:id="@+id/included"
            layout="@layout/simple_text"/>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    // initialize app resources
    StudioResourceRepositoryManager.getAppResources(facet)

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    val fields = binding.fields
    assertThat(fields).hasLength(1)
    assertThat(fields[0].name).isEqualTo("included")
    assertThat(fields[0].type).isEqualTo(LayoutBindingTypeUtil.parsePsiType("android.view.TextView", context))
  }

  @Test
  fun expectedStaticMethodsAreGenerated() {
    fixture.addFileToProject("res/layout/view_root_activity.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <view />
      </layout>
    """.trimIndent())
    fixture.addFileToProject("res/layout/merge_root_activity.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <merge />
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class ViewRootActivity {}")

    // Same API for regardless of view root or merge root
    // Compare with LightViewBindingClassTest#expectedStaticMethodsAreGenerated
    listOf("test.db.databinding.ViewRootActivityBinding", "test.db.databinding.MergeRootActivityBinding").forEach { classPath ->
      (fixture.findClass(classPath, context) as LightBindingClass).let { binding ->
        val methods = binding.methods.filter { it.hasModifier(JvmModifier.STATIC) }
        assertThat(methods.map { it.presentation!!.presentableText to it.isDeprecated }).containsExactly(
          "inflate(LayoutInflater)" to false,
          "inflate(LayoutInflater, Object)" to true,
          "inflate(LayoutInflater, ViewGroup, boolean)" to false,
          "inflate(LayoutInflater, ViewGroup, boolean, Object)" to true,
          "bind(View)" to false,
          "bind(View, Object)" to true
        )
      }
    }
  }

  @Test
  fun bindingsNotGeneratedForNonDataBindingLayouts() {
    fixture.addFileToProject("res/layout/activity_view.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <view />
      </layout>
    """.trimIndent())
    fixture.addFileToProject("res/layout/plain_view.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <view xmlns:android="http://schemas.android.com/apk/res/android" />
    """.trimIndent())
    val context = fixture.addClass("public class ViewActivity {}")

    assertThat(fixture.findClass("test.db.databinding.ActivityViewBinding", context) as? LightBindingClass).isNotNull()
    assertThat(fixture.findClass("test.db.databinding.PlainViewBinding", context) as? LightBindingClass).isNull()
  }

  @Test
  fun fieldsAreAnnotatedNonNullAndNullableCorrectly() {
    fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout android:id="@+id/always_present">
          <TextView android:id="@+id/sometimes_present" />
        </LinearLayout>
      </layout>
    """.trimIndent())

    fixture.addFileToProject("res/layout-land/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout android:id="@+id/always_present">
        </LinearLayout>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    assertThat(binding.fields).hasLength(2)
    val alwaysPresentField = binding.fields.first { it.name == "alwaysPresent" }
    val sometimesPresentField = binding.fields.first { it.name == "sometimesPresent" }

    val nullabilityManager = NullableNotNullManager.getInstance(project)
    assertThat(nullabilityManager.isNotNull(alwaysPresentField, false)).isTrue()
    assertThat(nullabilityManager.isNullable(sometimesPresentField, false)).isTrue()
  }

  @Test
  fun inconsistentTypesAcrossLayoutsDefaultsToView() {
    fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout>
          <TextView android:id="@+id/consistent_type" />
          <TextView android:id="@+id/inconsistent_type" />
        </LinearLayout>
      </layout>
    """.trimIndent())

    fixture.addFileToProject("res/layout-land/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout>
          <TextView android:id="@+id/consistent_type" />
          <Button android:id="@+id/inconsistent_type" />
        </LinearLayout>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    assertThat(binding.fields).hasLength(2)
    val consistentField = binding.fields.first { it.name == "consistentType" }
    val inconsistentField = binding.fields.first { it.name == "inconsistentType" }
    assertThat(consistentField.type).isEqualTo(LayoutBindingTypeUtil.parsePsiType("android.view.TextView", context))
    assertThat(inconsistentField.type).isEqualTo(LayoutBindingTypeUtil.parsePsiType("android.view.View", context))
  }

  /** Compare with [LightViewBindingClassTest.fieldTypesCanBeOverridden] */
  @Test
  fun fieldTypesCannotBeOverriddenInDataBinding() {
    fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout
         xmlns:android="http://schemas.android.com/apk/res/android"
         xmlns:tools="http://schemas.android.com/tools">
          <EditText android:id="@+id/ignored_type_override" tools:viewBindingType="TextView" />
      </layout>
    """.trimIndent())

    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    assertThat(binding.fields).hasLength(1)
    val field = binding.fields.first()

    assertThat(field.type.canonicalText).isEqualTo("android.widget.EditText")
  }

  @Test
  fun methodsAreAnnotatedNonNullAndNullableCorrectly() {
    fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout />
      </layout>
    """.trimIndent())

    val context = fixture.addClass("public class MainActivity {}")
    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass

    binding.methods.filter { it.name == "inflate" }.let { inflateMethods ->
      assertThat(inflateMethods).hasSize(4)
      inflateMethods.first { it.parameters.size == 4 }.let { inflateMethod ->
        (inflateMethod.parameters[0] as PsiParameter).assertExpected("LayoutInflater", "inflater")
        (inflateMethod.parameters[1] as PsiParameter).assertExpected("ViewGroup", "root", isNullable = true)
        (inflateMethod.parameters[2] as PsiParameter).assertExpected("boolean", "attachToRoot")
        (inflateMethod.parameters[3] as PsiParameter).assertExpected("Object", "bindingComponent", isNullable = true)
        inflateMethod.returnType!!.assertExpected(project, "ActivityMainBinding")
      }

      inflateMethods.first { it.parameters.size == 3 }.let { inflateMethod ->
        (inflateMethod.parameters[0] as PsiParameter).assertExpected("LayoutInflater", "inflater")
        (inflateMethod.parameters[1] as PsiParameter).assertExpected("ViewGroup", "root", isNullable = true)
        (inflateMethod.parameters[2] as PsiParameter).assertExpected("boolean", "attachToRoot")
        inflateMethod.returnType!!.assertExpected(project, "ActivityMainBinding")
      }

      inflateMethods.first { it.parameters.size == 2 }.let { inflateMethod ->
        (inflateMethod.parameters[0] as PsiParameter).assertExpected("LayoutInflater", "inflater")
        (inflateMethod.parameters[1] as PsiParameter).assertExpected("Object", "bindingComponent", isNullable = true)
        inflateMethod.returnType!!.assertExpected(project, "ActivityMainBinding")
      }

      inflateMethods.first { it.parameters.size == 1 }.let { inflateMethod ->
        (inflateMethod.parameters[0] as PsiParameter).assertExpected("LayoutInflater", "inflater")
        inflateMethod.returnType!!.assertExpected(project, "ActivityMainBinding")
      }
    }

    binding.methods.filter { it.name == "bind" }.let { bindMethods ->
      assertThat(bindMethods).hasSize(2)
      bindMethods.first { it.parameters.size == 2 }.let { bindMethod ->
        (bindMethod.parameters[0] as PsiParameter).assertExpected("View", "view")
        (bindMethod.parameters[1] as PsiParameter).assertExpected("Object", "bindingComponent", isNullable = true)
        bindMethod.returnType!!.assertExpected(project, "ActivityMainBinding")
      }

      bindMethods.first { it.parameters.size == 1 }.let { bindMethod ->
        (bindMethod.parameters[0] as PsiParameter).assertExpected("View", "view")
        bindMethod.returnType!!.assertExpected(project, "ActivityMainBinding")
      }
    }
  }

  @Test
  fun viewStubProxyClassGeneratedForViewStubs() {
    fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <ViewStub
            android:id="@+id/test_id"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
        </ViewStub>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context)!!
    assertThat(binding.findFieldByName("testId", false)!!.type.canonicalText)
      .isEqualTo(LayoutBindingModuleCache.getInstance(facet).dataBindingMode.viewStubProxy)
  }

  @Test
  fun correctTypeGeneratedForViewTag() {
    fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <view class="TextView"
            android:id="@+id/test_id"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent"
        />
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context)!!
    assertThat(binding.findFieldByName("testId", false)!!.type.canonicalText)
      .isEqualTo("android.widget.TextView")
  }

  @Test
  fun noFieldsAreGeneratedForMergeTags() {
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
          <Button android:id="@+id/test_button" />
          <merge android:id="@+id/test_merge" />
          <TextView android:id="@+id/test_text" />
        </LinearLayout>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    assertThat(binding.fields.map { field -> field.name }).containsExactly("testButton", "testText")
  }

  @Test
  fun bindingCacheReturnsConsistentValuesIfResourcesDontChange() {
    val bindingCache = LayoutBindingModuleCache.getInstance(facet)

    // We want to initialize resources but NOT add a data binding layout file yet. This will ensure
    // we test the case where there are no layout resource files in the project yet.
    fixture.addFileToProject(
      "res/values/strings.xml",
      // language=XML
      """
        <resources>
          <string name="app_name">SampleAppName</string>
        </resources>
      """.trimIndent()
    )

    // language=XML
    val sampleXml = """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout />
      </layout>
      """.trimIndent()

    val noResourcesGroups = bindingCache.bindingLayoutGroups
    assertThat(noResourcesGroups.size).isEqualTo(0)
    assertThat(noResourcesGroups).isSameAs(bindingCache.bindingLayoutGroups)

    fixture.addFileToProject("res/layout/activity_first.xml", sampleXml)
    projectRule.waitForResourceRepositoryUpdates()

    val oneResourceGroups = bindingCache.bindingLayoutGroups
    assertThat(oneResourceGroups.size).isEqualTo(1)
    assertThat(oneResourceGroups).isNotSameAs(noResourcesGroups)
    assertThat(oneResourceGroups).isSameAs(bindingCache.bindingLayoutGroups)

    fixture.addFileToProject("res/layout/activity_second.xml", sampleXml)
    projectRule.waitForResourceRepositoryUpdates()

    val twoResourcesGroups = bindingCache.bindingLayoutGroups
    assertThat(twoResourcesGroups.size).isEqualTo(2)
    assertThat(twoResourcesGroups).isNotSameAs(noResourcesGroups)
    assertThat(twoResourcesGroups).isNotSameAs(oneResourceGroups)
    assertThat(twoResourcesGroups).isSameAs(bindingCache.bindingLayoutGroups)
  }

  @Test
  fun bindingCacheRecoversAfterExitingDumbMode() {
    // language=XML
    val sampleXml = """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout />
      </layout>
      """.trimIndent()

    fixture.addFileToProject("res/layout/activity_first.xml", sampleXml)

    // initialize app resources
    StudioResourceRepositoryManager.getAppResources(facet)

    assertThat(LayoutBindingModuleCache.getInstance(facet).bindingLayoutGroups.map { group -> group.mainLayout.className })
      .containsExactly("ActivityFirstBinding")

    val dumbService = DumbService.getInstance(project) as DumbServiceImpl
    dumbService.isDumb = true

    // First, verify that dumb mode doesn't prevent us from accessing the previous cache
    assertThat(LayoutBindingModuleCache.getInstance(facet).bindingLayoutGroups.map { group -> group.mainLayout.className })
      .containsExactly("ActivityFirstBinding")

    // XML updates are ignored in dumb mode
    fixture.addFileToProject("res/layout/activity_second.xml", sampleXml)
    projectRule.waitForResourceRepositoryUpdates()
    assertThat(LayoutBindingModuleCache.getInstance(facet).bindingLayoutGroups.map { group -> group.mainLayout.className })
      .containsExactly("ActivityFirstBinding")

    // XML updates should catch up after dumb mode is exited (in other words, we didn't save a snapshot of the stale
    // cache from before)
    dumbService.isDumb = false
    assertThat(LayoutBindingModuleCache.getInstance(facet).bindingLayoutGroups.map { group -> group.mainLayout.className })
      .containsExactly("ActivityFirstBinding", "ActivitySecondBinding")
  }

  @Test
  fun superClassMethodsCanBeFound() {
    fixture.addFileToProject(
      "res/layout/activity_main.xml",
       // language=XML
     """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
        </LinearLayout>
      </layout>
    """.trimIndent())

    val context = fixture.addClass("public class MainActivity {}")

    run {
      val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context)!!
      assertThat(binding.supers.mapNotNull { it.qualifiedName }).containsExactly("androidx.databinding.ViewDataBinding")
      assertThat(binding.findMethodsByName("getRoot", true)[0].returnType!!.canonicalText).isEqualTo("android.view.View")
      assertThat(binding.findMethodsByName("executePendingBindings", true)).isNotEmpty()
      assertThat(binding.findMethodsByName("getRoot", false)).isEmpty()
      assertThat(binding.findMethodsByName("executePendingBindings", false)).isEmpty()
    }
  }

  @Test
  fun accentedCharactersAreStripped() {
    fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
         <LinearLayout android:id="@+id/tést_íd" />
      </layout>
      """.trimIndent()
    )

    val context = fixture.addClass("public class ActivityMain {}")
    val mainLayout = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    // It's ugly, but this is what the variable looks like after stripping é and í before capitalizing parts
    assertThat(mainLayout.fields.first().name).isEqualTo("tStD")
  }
}
