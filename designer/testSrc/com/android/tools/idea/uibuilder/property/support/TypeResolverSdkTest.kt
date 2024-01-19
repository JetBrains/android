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
package com.android.tools.idea.uibuilder.property.support

import com.android.AndroidXConstants.FLOATING_ACTION_BUTTON
import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ANDROID_WIDGET_PREFIX
import com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID
import com.android.SdkConstants.ATTR_BACKGROUND_TINT_MODE
import com.android.SdkConstants.BUTTON
import com.android.SdkConstants.CLASS_VIEW
import com.android.SdkConstants.CLASS_VIEWGROUP
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.FRAME_LAYOUT
import com.android.SdkConstants.MATERIAL1_PKG
import com.android.SdkConstants.MATERIAL2_PKG
import com.android.SdkConstants.PreferenceAttributes.ATTR_DEFAULT_VALUE
import com.android.SdkConstants.PreferenceClasses
import com.android.SdkConstants.PreferenceClasses.CLASS_PREFERENCE
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.Dependencies
import com.android.tools.idea.uibuilder.property.NlPropertiesModelTest.Companion.waitUntilLastSelectionUpdateCompleted
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.testutils.AndroidAttributeFact
import com.android.tools.idea.uibuilder.property.testutils.PsiLookupRule
import com.android.tools.idea.uibuilder.property.testutils.SupportTestUtil
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.xml.NamespaceAwareXmlAttributeDescriptor
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private const val ANDROID_VIEWS_HEADER = "Android Views"
private const val ANDROID_PREFERENCES_HEADER = "Android Preferences"
private const val APPCOMPAT_VIEWS_HEADER = "AppCompat Views"
private const val CONSTRAINT_LAYOUT_HEADER = "Constraint Layout"
private const val DESIGN_HEADER = "Design Lecagy"
private const val MATERIAL_HEADER = "Material Design"
private const val RECYCLER_VIEW_HEADER = "RecyclerView v7"
private const val CONSTRAINT_LAYOUT_ID = "constraint"
private const val DESIGN_ID = "design"
private const val MATERIAL_ID = "material/material"
private const val PREFERENCE_PACKAGE = "android.preference"
private const val APPCOMPAT_VIEW_PACKAGE = "android.support.v7.widget"
private const val CONSTRAINT_LAYOUT_PACKAGE = "android.support.constraint"
private const val TOTAL_ERROR_MESSAGE = "attributes with mismatched types"
private const val RECYCLER_VIEW_V7_ID = "recyclerview-v7"
private const val DEBUG_DUMP = false

@RunsInEdt
class TypeResolverSdkTest {
  private val projectRule = AndroidProjectRule.withSdk()
  private val lookupRule = PsiLookupRule { projectRule.module.androidFacet }

  @get:Rule val ruleChain = RuleChain.outerRule(EdtRule()).around(projectRule).around(lookupRule)!!

  @Test
  fun testAndroidViewAttributeTypes() {
    val psiFacade = JavaPsiFacade.getInstance(projectRule.project)
    val psiViewClass =
      psiFacade.findClass(CLASS_VIEW, GlobalSearchScope.allScope(projectRule.project))!!
    val psiViewGroupClass =
      psiFacade.findClass(CLASS_VIEWGROUP, GlobalSearchScope.allScope(projectRule.project))!!
    val psiPackage = psiFacade.findPackage(ANDROID_WIDGET_PREFIX.trim { it == '.' })!!
    val report = Report(ANDROID_VIEWS_HEADER)
    psiPackage.classes
      .filter { it.isInheritor(psiViewClass, true) }
      .forEach { checkViewAttributes(it.name!!, report) }
    psiPackage.classes
      .filter { it.isInheritor(psiViewGroupClass, true) }
      .forEach { checkViewLayoutAttributes(it.name!!, report) }
    report.dumpReport(report.totalErrors > 0)
    assertThat(report.totalErrors).named(TOTAL_ERROR_MESSAGE).isEqualTo(0)
  }

  @Test
  fun testAndroidPreferenceAttributeTypes() {
    val psiFacade = JavaPsiFacade.getInstance(projectRule.project)
    val psiPreferenceClass =
      psiFacade.findClass(CLASS_PREFERENCE, GlobalSearchScope.allScope(projectRule.project))!!
    val psiPackage = psiFacade.findPackage(PREFERENCE_PACKAGE)!!
    val report = Report(ANDROID_PREFERENCES_HEADER)
    psiPackage.classes
      .filter { it.isInheritor(psiPreferenceClass, true) }
      .forEach { checkPreferencesAttributes(it.name!!, report) }
    report.dumpReport(report.totalErrors > 0)
    assertThat(report.totalErrors).named(TOTAL_ERROR_MESSAGE).isEqualTo(0)
  }

  @Test
  fun testAppCompatViewAttributeTypes() {
    val versionMap = Dependencies.add(projectRule.fixture, APPCOMPAT_LIB_ARTIFACT_ID)
    val psiFacade = JavaPsiFacade.getInstance(projectRule.project)
    val psiViewClass =
      psiFacade.findClass(CLASS_VIEW, GlobalSearchScope.allScope(projectRule.project))!!
    val psiViewGroupClass =
      psiFacade.findClass(CLASS_VIEWGROUP, GlobalSearchScope.allScope(projectRule.project))!!
    val psiPackage = psiFacade.findPackage(APPCOMPAT_VIEW_PACKAGE)!!
    val report = Report(APPCOMPAT_VIEWS_HEADER, versionMap)
    psiPackage.classes
      .filter { it.isInheritor(psiViewClass, true) }
      .forEach { checkViewAttributes(it.qualifiedName!!, report) }
    psiPackage.classes
      .filter { it.isInheritor(psiViewGroupClass, true) }
      .forEach { checkViewLayoutAttributes(it.qualifiedName!!, report) }
    report.dumpReport(report.totalErrors > 0)
    assertThat(report.totalErrors).named(TOTAL_ERROR_MESSAGE).isEqualTo(0)
  }

  @Test
  fun testConstraintLayoutViewAttributeTypes() {
    val versionMap = Dependencies.add(projectRule.fixture, CONSTRAINT_LAYOUT_ID)
    val psiFacade = JavaPsiFacade.getInstance(projectRule.project)
    val psiViewClass =
      psiFacade.findClass(CLASS_VIEW, GlobalSearchScope.allScope(projectRule.project))!!
    val psiViewGroupClass =
      psiFacade.findClass(CLASS_VIEWGROUP, GlobalSearchScope.allScope(projectRule.project))!!
    val psiPackage = psiFacade.findPackage(CONSTRAINT_LAYOUT_PACKAGE)!!
    val report = Report(CONSTRAINT_LAYOUT_HEADER, versionMap)
    psiPackage.classes
      .filter { it.isInheritor(psiViewClass, true) }
      .forEach { checkViewAttributes(it.qualifiedName!!, report) }
    psiPackage.classes
      .filter { it.isInheritor(psiViewGroupClass, true) }
      .forEach { checkViewLayoutAttributes(it.qualifiedName!!, report) }
    report.dumpReport(report.totalErrors > 0)
    assertThat(report.totalErrors).named(TOTAL_ERROR_MESSAGE).isEqualTo(0)
  }

  @Test
  fun testDesignViewAttributeTypes() {
    val versionMap = Dependencies.add(projectRule.fixture, DESIGN_ID)
    val psiFacade = JavaPsiFacade.getInstance(projectRule.project)
    val psiViewClass =
      psiFacade.findClass(CLASS_VIEW, GlobalSearchScope.allScope(projectRule.project))!!
    val psiViewGroupClass =
      psiFacade.findClass(CLASS_VIEWGROUP, GlobalSearchScope.allScope(projectRule.project))!!
    val psiPackage = psiFacade.findPackage(MATERIAL1_PKG)!!
    val report = Report(DESIGN_HEADER, versionMap)
    psiPackage.classes
      .filter { it.isInheritor(psiViewClass, true) }
      .forEach { checkViewAttributes(it.qualifiedName!!, report) }
    psiPackage.classes
      .filter { it.isInheritor(psiViewGroupClass, true) }
      .forEach { checkViewLayoutAttributes(it.qualifiedName!!, report) }
    report.dumpReport(report.totalErrors > 0)
    assertThat(report.totalErrors).named(TOTAL_ERROR_MESSAGE).isEqualTo(0)
  }

  @Test
  fun testMaterialViewAttributeTypes() {
    val versionMap = Dependencies.add(projectRule.fixture, "appcompat/appcompat", MATERIAL_ID)
    val psiFacade = JavaPsiFacade.getInstance(projectRule.project)
    val psiViewClass =
      psiFacade.findClass(CLASS_VIEW, GlobalSearchScope.allScope(projectRule.project))!!
    val psiViewGroupClass =
      psiFacade.findClass(CLASS_VIEWGROUP, GlobalSearchScope.allScope(projectRule.project))!!
    val psiPackage = psiFacade.findPackage(MATERIAL2_PKG)!!
    val report = Report(MATERIAL_HEADER, versionMap)
    for (folder in psiPackage.subPackages) {
      folder.classes
        .filter { it.isInheritor(psiViewClass, true) }
        .forEach { checkViewAttributes(it.qualifiedName!!, report) }
      folder.classes
        .filter { it.isInheritor(psiViewGroupClass, true) }
        .forEach { checkViewLayoutAttributes(it.qualifiedName!!, report) }
    }
    report.dumpReport(report.totalErrors > 0)
    assertThat(report.totalErrors).named(TOTAL_ERROR_MESSAGE).isEqualTo(0)
  }

  @Test
  fun testMaterial3ViewAttributeTypes() {
    val versionMap = Dependencies.add(projectRule.fixture, "material/material")
    val psiFacade = JavaPsiFacade.getInstance(projectRule.project)
    val psiViewClass =
      psiFacade.findClass(CLASS_VIEW, GlobalSearchScope.allScope(projectRule.project))!!
    val psiViewGroupClass =
      psiFacade.findClass(CLASS_VIEWGROUP, GlobalSearchScope.allScope(projectRule.project))!!
    val psiPackage = psiFacade.findPackage(MATERIAL2_PKG)!!
    val report = Report(MATERIAL_HEADER, versionMap)
    for (folder in psiPackage.subPackages) {
      folder.classes
        .filter { it.isInheritor(psiViewClass, true) }
        .forEach { checkViewAttributes(it.qualifiedName!!, report) }
      folder.classes
        .filter { it.isInheritor(psiViewGroupClass, true) }
        .forEach { checkViewLayoutAttributes(it.qualifiedName!!, report) }
    }
    report.dumpReport(report.totalErrors > 0)
    assertThat(report.totalErrors).named(TOTAL_ERROR_MESSAGE).isEqualTo(0)
  }

  @Test
  fun testRecyclerViewAttributeTypes() {
    val versionMap = Dependencies.add(projectRule.fixture, RECYCLER_VIEW_V7_ID)
    val psiFacade = JavaPsiFacade.getInstance(projectRule.project)
    val psiViewClass =
      psiFacade.findClass(CLASS_VIEW, GlobalSearchScope.allScope(projectRule.project))!!
    val psiViewGroupClass =
      psiFacade.findClass(CLASS_VIEWGROUP, GlobalSearchScope.allScope(projectRule.project))!!
    val psiPackage = psiFacade.findPackage("android.support.v7.widget")!!
    val report = Report(RECYCLER_VIEW_HEADER, versionMap)
    psiPackage.classes
      .filter { it.isInheritor(psiViewClass, true) }
      .forEach { checkViewAttributes(it.qualifiedName!!, report) }
    psiPackage.classes
      .filter { it.isInheritor(psiViewGroupClass, true) }
      .forEach { checkViewLayoutAttributes(it.qualifiedName!!, report) }
    report.dumpReport(report.totalErrors > 0)
    assertThat(report.totalErrors).named(TOTAL_ERROR_MESSAGE).isEqualTo(0)
  }

  private fun checkViewAttributes(tagName: String, report: Report) {
    val util =
      SupportTestUtil(
        projectRule,
        tagName,
        parentTag = FRAME_LAYOUT,
        fileName = "${tagName.substringAfter('.')}$DOT_XML",
      )
    waitUntilLastSelectionUpdateCompleted(util.model)
    val tag = util.components.first().backend.tag!!
    checkAttributes(tag, report)
  }

  private fun checkPreferencesAttributes(tagName: String, report: Report) {
    val util =
      SupportTestUtil(
        projectRule,
        tagName,
        parentTag = SdkConstants.PreferenceTags.PREFERENCE_SCREEN,
        resourceFolder = SdkConstants.FD_RES_XML,
        fileName = "${tagName.substringAfter('.')}$DOT_XML",
      )
    waitUntilLastSelectionUpdateCompleted(util.model)
    val tag = util.components.first().backend.tag!!
    checkAttributes(tag, report)
  }

  private fun checkViewLayoutAttributes(tagName: String, report: Report) {
    val util =
      SupportTestUtil(
        projectRule,
        BUTTON,
        parentTag = tagName,
        fileName = "${tagName.substringAfter('.')}$DOT_XML",
      )
    val tag = util.components.first().parent!!.backend.tag!!
    checkAttributes(tag, report)
  }

  private fun checkAttributes(tag: XmlTag, report: Report) {
    val descriptorProvider = AndroidDomElementDescriptorProvider()
    val descriptor = descriptorProvider.getDescriptor(tag) ?: return
    val attrDescriptors = descriptor.getAttributesDescriptors(tag)
    val resourceManagers = ModuleResourceManagers.getInstance(projectRule.module.androidFacet!!)
    val frameworkResourceManager = resourceManagers.frameworkResourceManager!!
    val localResourceManager = resourceManagers.localResourceManager
    val localAttrDefs = localResourceManager.attributeDefinitions
    val systemAttrDefs = frameworkResourceManager.attributeDefinitions!!
    val componentClass = lookupRule.classOf(tag.name)
    attrDescriptors.forEach {
      val name = it.name
      val namespaceUri =
        (it as NamespaceAwareXmlAttributeDescriptor).getNamespace(tag) ?: ANDROID_URI
      val namespace = ResourceNamespace.fromNamespaceUri(namespaceUri)
      if (namespace != null) {
        val attrDefs = if (ANDROID_URI == namespaceUri) systemAttrDefs else localAttrDefs
        val attrDefinition = attrDefs.getAttrDefinition(ResourceReference.attr(namespace, name))
        val type = TypeResolver.resolveType(name, attrDefinition, componentClass)
        val className = componentClass?.qualifiedName
        val lookupType =
          when {
            name != ATTR_DEFAULT_VALUE -> AndroidAttributeFact.lookup(name)
            className == PreferenceClasses.CLASS_LIST_PREFERENCE -> NlPropertyType.STRING
            className == PreferenceClasses.CLASS_EDIT_TEXT_PREFERENCE -> NlPropertyType.STRING
            className == PreferenceClasses.CLASS_RINGTONE_PREFERENCE -> NlPropertyType.STRING
            className == PreferenceClasses.CLASS_MULTI_SELECT_LIST_PREFERENCE ->
              NlPropertyType.STRING_ARRAY
            className == PreferenceClasses.CLASS_CHECK_BOX_PREFERENCE ->
              NlPropertyType.THREE_STATE_BOOLEAN
            className == PreferenceClasses.CLASS_SWITCH_PREFERENCE ->
              NlPropertyType.THREE_STATE_BOOLEAN
            className == PreferenceClasses.CLASS_SEEK_BAR_PREFERENCE -> NlPropertyType.INTEGER
            else -> NlPropertyType.UNKNOWN
          }

        // Remove this when we have a library in prebuilts with this bug fixed: b/119883920
        if (tag.name == FLOATING_ACTION_BUTTON.oldName() && it.name == ATTR_BACKGROUND_TINT_MODE) {
          // ignore for now...
        } else if (type != lookupType) {
          report.logMismatch(Mismatch(tag.localName, name, type, lookupType))
        }
        report.logAttribute(tag.localName)
      }
    }
  }

  private data class Mismatch(
    val tag: String,
    val attribute: String,
    val found: NlPropertyType,
    val expected: NlPropertyType,
  )

  private class Report(
    private val name: String,
    private val versionMap: Map<String, GradleVersion> = emptyMap(),
  ) {
    private val found = mutableMapOf<String, Int>()
    private val errors = mutableMapOf<String, Int>()
    private val mismatches = mutableListOf<Mismatch>()
    var totalErrors = 0
      private set

    fun logAttribute(tag: String) {
      val count = found[tag]
      found[tag] = 1 + (count ?: 0)
    }

    private fun logError(tag: String) {
      val count = errors[tag]
      errors[tag] = 1 + (count ?: 0)
      totalErrors++
    }

    fun logMismatch(mismatch: Mismatch) {
      mismatches.add(mismatch)
      logError(mismatch.tag)
    }

    fun dumpReport(hasErrors: Boolean) {
      if (!DEBUG_DUMP && !hasErrors) {
        return
      }
      System.err.println("\n==============================================================>")
      System.err.println("           $name")
      if (versionMap.isNotEmpty()) {
        System.err.println("\n==============================================================>")
        versionMap.forEach { (library, version) -> System.err.println("  $library: $version") }
        System.err.println("\n==============================================================>")
      }
      if (mismatches.isNotEmpty()) {
        System.err.println("\nType mismatches found:")
        mismatches.forEach {
          System.err.println(
            "Tag: ${it.tag}, Attr: ${it.attribute}, Got: ${it.found}, Wanted: ${it.expected}"
          )
        }
      }
      System.err.println()
      found.forEach { (tag, count) ->
        System.err.println(
          "${String.format("%4d", count)}: attributes found for: $tag ${formatError(tag)}"
        )
      }
      System.err.println("\n==============================================================>")
    }

    private fun formatError(tag: String): String {
      val count = errors[tag] ?: return ""
      return ", mismatches: $count"
    }
  }
}
