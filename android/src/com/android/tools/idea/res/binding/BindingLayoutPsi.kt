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
@file:JvmName("BindingLayoutPsiUtils")

package com.android.tools.idea.res.binding

import com.android.SdkConstants
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.facet.AndroidFacet

/**
 * PSI fields and other information indirectly backed by those fields that are related to a layout
 * xml file that we want to generate a binding class for.
 *
 * One does not construct this directly. Instead, [BindingLayoutInfo] is responsible for creating
 * it.
 */
class BindingLayoutPsi internal constructor(val facet: AndroidFacet,
                                            private val info: BindingLayoutInfo) {
  val project: Project
    get() = facet.module.project

  /**
   * The module which owns this layout file.
   */
  val module: Module
    get() = facet.module

  /**
   * The PSI of the `layout.xml` file this binding layout information is associated with
   */
  internal val xmlPsiFile: XmlFile
    get() = info.xml.toPsiFile(project)

  /**
   * The PSI for a "BindingImpl" class generated for this layout file. It is created externally so
   * it can potentially be `null` until it is set.
   *
   * NOTE: This is a code smell - it's a field that this class never uses, but rather it relies on
   * an external class to set it. Ideally, we can fix this, perhaps by removing the field
   * completely (and having somewhere else own it). The main consumer of this class at the time
   * of writing this comment is `BindingLayoutInfoFile`. It may no longer be necessary if we end
   * up backing a LightBindingClass with its own light file, instead of with its XML file.
   *
   * See also: `DataBindingClassFactory.getOrCreateBindingClassesFor`
   */
  var psiClass: PsiClass? = null

  /**
   * The PSI element representing this layout file, useful if a user wants to navigate
   * when their cursor is on an "XyzBinding" class name.
   */
  val navigationElement: PsiElement = BindingLayoutInfoFile(this)

  val layoutType: BindingLayoutInfo.LayoutType
    get() = if (SdkConstants.TAG_LAYOUT == xmlPsiFile.rootTag!!.name) {
      BindingLayoutInfo.LayoutType.DATA_BINDING_LAYOUT
    }
    else {
      BindingLayoutInfo.LayoutType.VIEW_BINDING_LAYOUT
    }

  private fun findDataTag(): XmlTag? = xmlPsiFile.rootTag?.takeIf { it.name == "layout" }?.findFirstSubTag("data")

  fun findVariableTag(variable: BindingLayoutXml.Variable): XmlTag? {
    return findDataTag()?.findSubTags("variable")?.firstOrNull { tag ->
      variable.name == StringUtil.unescapeXmlEntities(tag.getAttributeValue(SdkConstants.ATTR_NAME).orEmpty()) }
  }

  fun findImportTag(import: BindingLayoutXml.Import): XmlTag? {
    return findDataTag()?.findSubTags("import")?.firstOrNull { tag ->
      import.alias == StringUtil.unescapeXmlEntities(tag.getAttributeValue(SdkConstants.ATTR_ALIAS).orEmpty()) ||
      import.type == StringUtil.unescapeXmlEntities(tag.getAttributeValue(SdkConstants.ATTR_TYPE).orEmpty())
    }
  }
}

/**
 * Convenience method for converting an XML data file into its corresponding PSI [XmlFile].
 * Note: this will throw an exception if the file isn't found given the specified project.
 */
fun BindingLayoutXml.toPsiFile(project: Project) = PsiManager.getInstance(project).findFile(file) as XmlFile
