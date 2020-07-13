// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.dom

import com.android.SdkConstants
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.codeInsight.completion.JavaLookupElementBuilder
import com.intellij.lang.ASTNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlChildRole
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ArrayUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.isVisibleInXml
import java.util.ArrayList

class AndroidXmlReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    if (element !is XmlTag) {
      return PsiReference.EMPTY_ARRAY
    }
    val module = ModuleUtilCore.findModuleForPsiElement(element)
    if (module == null || AndroidFacet.getInstance(module) == null) {
      return PsiReference.EMPTY_ARRAY
    }
    val startTagName = XmlChildRole.START_TAG_NAME_FINDER.findChild(element.getNode())
    val baseClassQName = computeBaseClass(element) ?: return PsiReference.EMPTY_ARRAY
    val result: MutableList<PsiReference> = ArrayList()
    val tag = element
    if (startTagName != null && areReferencesProvidedByReferenceProvider(startTagName)) {
      addReferences(tag, startTagName.psi, result, module, baseClassQName, true)
    }
    val closingTagName = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(element.getNode())
    if (closingTagName != null && areReferencesProvidedByReferenceProvider(closingTagName)) {
      addReferences(tag, closingTagName.psi, result, module, baseClassQName, false)
    }
    return result.toTypedArray()
  }

  private class MyClassOrPackageReference(tag: XmlTag,
                                          private val myNameElement: PsiElement,
                                          private val myRangeInNameElement: TextRange,
                                          private val myIsPackage: Boolean,
                                          private val myModule: Module,
                                          private val myBaseClassQName: String,
                                          private val myStartTag: Boolean) : PsiReferenceBase<PsiElement?>(tag, rangeInParent(
    myNameElement, myRangeInNameElement), true) {
    override fun resolve(): PsiElement? {
      return ResolveCache.getInstance(myElement!!.project)
        .resolveWithCaching(this, { reference: MyClassOrPackageReference?, incompleteCode: Boolean -> resolveInner() }, false, false)
    }

    private fun resolveInner(): PsiElement? {
      val end = myRangeInNameElement.endOffset
      val value = myNameElement.text.substring(0, end)
      val facade = JavaPsiFacade.getInstance(myElement!!.project)
      return if (myIsPackage) facade.findPackage(value)
      else facade.findClass(value, myModule.getModuleSystem().getResolveScope(
        ScopeType.MAIN))
    }

    override fun getVariants(): Array<Any> {
      val prefix = myNameElement.text.substring(0, myRangeInNameElement.startOffset)
      if (!myStartTag) {
        val startTagNode = XmlChildRole.START_TAG_NAME_FINDER.findChild(myElement!!.node)
        if (startTagNode != null) {
          val startTagName = startTagNode.text
          if (startTagName.startsWith(prefix)) {
            return arrayOf(startTagName.substring(prefix.length))
          }
        }
        return emptyArray()
      }
      val project = myModule.project
      val baseClass = JavaPsiFacade
                        .getInstance(project)
                        .findClass(myBaseClassQName, myModule.getModuleWithDependenciesAndLibrariesScope(false))
                      ?: return emptyArray()
      val result: MutableList<Any?> = ArrayList()
      ClassInheritorsSearch
        .search(baseClass, myModule.getModuleWithDependenciesAndLibrariesScope(false), true, true, false)
        .forEach { psiClass: PsiClass ->
          if (psiClass.containingClass != null) {
            return@forEach
          }
          var name = psiClass.qualifiedName
          if (name != null && name.startsWith(prefix) && psiClass.isVisibleInXml()) {
            name = name.substring(prefix.length)
            result.add(JavaLookupElementBuilder.forClass(psiClass, name, true))
          }
        }
      return ArrayUtil.toObjectArray(result)
    }

    override fun bindToElement(element: PsiElement): PsiElement {
      val newName = if (myIsPackage) (element as PsiPackage).qualifiedName else (element as PsiClass).qualifiedName!!
      val range = TextRange(0, myRangeInNameElement.endOffset)
      return ElementManipulators.handleContentChange(myNameElement, range, newName)
    }

    override fun handleElementRename(newElementName: String): PsiElement? {
      return ElementManipulators.handleContentChange(myNameElement, myRangeInNameElement, newElementName)
    }

    companion object {
      private fun rangeInParent(element: PsiElement, range: TextRange): TextRange {
        val offset = element.startOffsetInParent
        return TextRange(range.startOffset + offset, range.endOffset + offset)
      }
    }
  }

  companion object {
    private fun addReferences(tag: XmlTag,
                              nameElement: PsiElement,
                              result: MutableList<PsiReference>,
                              module: Module,
                              baseClassQName: String,
                              startTag: Boolean) {
      val text = nameElement.text ?: return
      val nameParts = text.split("\\.".toRegex()).toTypedArray()
      if (nameParts.size == 0) {
        return
      }
      var offset = 0
      for (i in nameParts.indices) {
        val name = nameParts[i]
        if (!name.isEmpty()) {
          offset += name.length
          val range = TextRange(offset - name.length, offset)
          val isPackage = i < nameParts.size - 1
          result.add(MyClassOrPackageReference(tag, nameElement, range, isPackage, module, baseClassQName, startTag))
        }
        offset++
      }
    }

    @JvmStatic
    fun areReferencesProvidedByReferenceProvider(nameElement: ASTNode?): Boolean {
      if (nameElement != null) {
        val psiNameElement = nameElement.psi
        val tag = if (psiNameElement != null) PsiTreeUtil.getParentOfType(psiNameElement, XmlTag::class.java) else null
        if (tag != null) {
          val baseClassQName = computeBaseClass(tag)
          if (baseClassQName != null) {
            return nameElement.text.contains(".")
          }
        }
      }
      return false
    }

    private fun computeBaseClass(context: XmlTag): String? {
      var parentTag = context.parentTag
      if (parentTag != null && SdkConstants.TAG_LAYOUT == parentTag.name) {
        // If the tag parent is "layout", let's consider the given tag as the root to compute the base class
        parentTag = null
      }
      val pair = AndroidDomElementDescriptorProvider.getDomElementAndBaseClassQName(parentTag ?: context)
      return pair?.getSecond()
    }
  }
}