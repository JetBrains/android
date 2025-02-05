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
package org.jetbrains.android

import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.xml.SchemaPrefix
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.XmlExtension
import org.jetbrains.android.dom.converters.ResourceNamespaceReference
import org.jetbrains.android.facet.AndroidFacet

@Suppress("KDocUnresolvedReference") // We don't depend on the XPath plugin.
/**
 * Finds references to xmlns declarations from within XML attribute values that are references to Android resources with the namespace
 * specified.
 *
 * @see XsltImplicitUsagesProvider
 * @see ResourceNamespaceReference
 * @see org.jetbrains.android.dom.converters.ResourceReferenceConverter.createReferences
 */
class AndroidXmlnsImplicitUsagesProvider : ImplicitUsageProvider {
  override fun isImplicitRead(element: PsiElement) = false
  override fun isImplicitWrite(element: PsiElement) = false

  override fun isImplicitUsage(element: PsiElement): Boolean {
    if (element !is XmlAttribute || !element.isNamespaceDeclaration || AndroidFacet.getInstance(element) == null) return false
    val xmlTag = element.parentOfType<XmlTag>() ?: return false

    // References from namespace prefixes resolve to the SchemaPrefix fake elements, not the attribute itself.
    val schemaPrefix: SchemaPrefix =
      XmlExtension.getExtensionByElement(element)?.getPrefixDeclaration(xmlTag, element.localName) ?: return false

    return ReferencesSearch.search(schemaPrefix, LocalSearchScope(xmlTag)).asIterable().any { it is ResourceNamespaceReference }
  }
}
