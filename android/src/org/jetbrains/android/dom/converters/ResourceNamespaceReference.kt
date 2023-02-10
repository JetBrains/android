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
package org.jetbrains.android.dom.converters

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.idea.model.Namespacing
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.AndroidDependenciesCache
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.resolveResourceNamespace
import com.intellij.openapi.util.TextRange
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import com.intellij.util.text.nullize
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.GenericDomValue
import com.intellij.xml.XmlExtension
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.dom.resources.ResourceValue
import org.jetbrains.kotlin.idea.base.util.module

/**
 * PSI Reference to a resource namespace, created in the namespace part of an XML resource reference (e.g. `@com.example:string/foo`).
 *
 * Resolves to the local xmlns declaration if present or a fake [ResourceNamespaceFakePsiElement] object that represents an aapt namespace.
 */
class ResourceNamespaceReference(
  domValue: GenericDomValue<*>,
  private val resourceValue: ResourceValue
) : PsiReferenceBase<XmlElement>(DomUtil.getValueElement(domValue)!!, null, true) {

  override fun getVariants(): Array<PsiElement> = PsiElement.EMPTY_ARRAY

  override fun resolve(): PsiElement? {
    val prefix = rangeInElement.substring(element.text).nullize(nullizeSpaces = true) ?: return null
    val prefixDeclaration = XmlExtension.getExtensionByElement(element)?.getPrefixDeclaration(element.parentOfType<XmlTag>(), prefix)

    val repositoryManager = StudioResourceRepositoryManager.getInstance(element) ?: return null

    if (prefixDeclaration != null && repositoryManager.namespacing != Namespacing.DISABLED) {
      // TODO(b/76409654): In non-namespaced projects, namespaced resource references cannot rely on XML namespace definitions.
      return prefixDeclaration
    }

    val resourceNamespace = element.resolveResourceNamespace(prefix) ?: return null
    return when (element.resolveResourceNamespace(prefix)) {
      ResourceNamespace.ANDROID -> ResourceNamespaceFakePsiElement(resourceNamespace, element)
      in repositoryManager.appResources.namespaces -> ResourceNamespaceFakePsiElement(resourceNamespace, element)
      else -> null
    }
  }

  override fun calculateDefaultRangeInElement(): TextRange {
    val wholeReferenceRange = super.calculateDefaultRangeInElement()
    val startOffset = wholeReferenceRange.startOffset + if (resourceValue.prefix == 0.toChar()) 0 else 1
    return TextRange(startOffset, startOffset + resourceValue.`package`!!.length)
  }
}

/**
 * Fake PSI element that represents an aapt namespace.
 *
 * Provides a readable text description for [com.intellij.codeInsight.navigation.CtrlMouseHandler] and handles navigation (for now rather
 * naively).
 *
 * TODO(namespaces): Create PSI references to these from XML namespace URIs.
 */
class ResourceNamespaceFakePsiElement(
  private val resourceNamespace: ResourceNamespace,
  private val parent: XmlElement
) : FakePsiElement(), NavigatablePsiElement {
  override fun getParent(): PsiElement? = parent
  override fun canNavigate(): Boolean = true

  override fun getNavigationElement(): PsiElement {
    val module = parent.module ?: return this
    val androidDependencies = AndroidDependenciesCache.getAllAndroidDependencies(module, true)
    val androidFacet =
      androidDependencies.firstOrNull { it.module.getModuleSystem().getPackageName() == resourceNamespace.packageName } ?: return this
    return Manifest.getMainManifest(androidFacet)?.`package`?.xmlAttribute ?: this
  }

  override fun getName(): String? {
    // An empty name makes the presentable text appear in the hover popup.
    return ""
  }

  override fun getPresentableText() = when (resourceNamespace.packageName) {
    null -> "Special ${resourceNamespace.xmlNamespaceUri} resources namespace"
    else -> "${resourceNamespace.packageName} resources package"
  }
}
