// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.dom

import com.android.SdkConstants
import com.android.SdkConstants.CLASS_DRAWABLE
import com.android.SdkConstants.CLASS_VIEW
import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.android.tools.idea.model.queryPackageNameFromManifestIndex
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.psi.TagToClassMapper
import com.android.tools.idea.res.isClassPackageNeeded
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.completion.XmlTagInsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiQualifiedNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.impl.migration.MigrationClassImpl
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.xml.XmlChildRole
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ProcessingContext
import com.intellij.util.xml.DomManager
import org.jetbrains.android.dom.drawable.CustomDrawableDomElement
import org.jetbrains.android.dom.layout.LayoutViewElement
import org.jetbrains.android.dom.layout.View
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil.PreferenceSource
import org.jetbrains.android.dom.xml.PreferenceElement
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.TagFromClassDescriptor
import org.jetbrains.android.facet.findClassValidInXMLByName

class AndroidXmlReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    if (element !is XmlTag) {
      return emptyArray()
    }
    val facet = element.androidFacet ?: return emptyArray()

    val baseClassQName = computeBaseClass(element) ?: return emptyArray()
    val classFilter = getClassFilter(baseClassQName, facet) ?: { _, _ -> true }
    val result: MutableList<PsiReference> = ArrayList()
    val startTagName = XmlChildRole.START_TAG_NAME_FINDER.findChild(element.getNode())
    if (startTagName != null) {
      addReferences(element, startTagName, result, facet, baseClassQName, classFilter, true)
    }

    val closingTagName = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(element.getNode())
    if (closingTagName != null) {
      addReferences(element, closingTagName, result, facet, baseClassQName, classFilter, false)
    }
    return result.toTypedArray()
  }

  private fun getClassFilter(baseClassQName: String, facet: AndroidFacet): ((String, PsiClass) -> Boolean)? {
    if (baseClassQName == CLASS_DRAWABLE) {
      val packageName = facet.queryPackageNameFromManifestIndex() ?: return { _, _ -> false }
      return { _, psiClass -> psiClass.qualifiedName?.startsWith(packageName) == true }
    }
    return null
  }

  private class MyClassOrPackageReference(tag: XmlTag,
                                          private val myNameElement: ASTNode,
                                          private val myRangeInNameElement: TextRange,
                                          private val facet: AndroidFacet,
                                          private val myBaseClassQName: String,
                                          private val classFilter: (String, PsiClass) -> Boolean,
                                          private val myStartTag: Boolean) : PsiReferenceBase<PsiElement?>(tag, true) {
    private val project = tag.project
    private val packagePrefix = myNameElement.text.substring(0, myRangeInNameElement.startOffset)
    private val isParentContainer by lazy {
      val parentTagFromClassDescriptor = ((tag.parent as? XmlTag)?.descriptor as? TagFromClassDescriptor) ?: return@lazy true
      parentTagFromClassDescriptor.isContainer
    }

    override fun resolve(): PsiElement? {
      return ResolveCache.getInstance(project).resolveWithCaching(this, { _, _ -> resolveInner() }, false, false)
    }

    private fun resolveInner(): PsiElement? {
      val end = myRangeInNameElement.endOffset
      val value = myNameElement.text.substring(0, end)
      val facade = JavaPsiFacade.getInstance(project)
      return findClassValidInXMLByName(facet, value, myBaseClassQName) as? PsiElement
             ?: facade.findPackage(value) as? PsiElement
             // Special case for migration, because InheritanceUtil.isInheritorOrSelf works incorrectly with MigrationClassImpl
             ?: facade.findClass(value, facet.module.getModuleSystem().getResolveScope(ScopeType.MAIN)).takeIf { it is MigrationClassImpl }
    }

    override fun getVariants(): Array<Any> {
      if (!isParentContainer) {
        return emptyArray()
      }
      if (!myStartTag) {
        // Lookup elements for closing tag are provided by TagNameReferenceCompletionProvider.createClosingTagLookupElements.
        // Essentially it just duplicates opening tag. It's a common logic for all XML tags.
        // It works because for every tag we have reference - [TagNameReference]. See [TagNameReference.createTagNameReference]
        return emptyArray()
      }

      val apiLevel = StudioAndroidModuleInfo.getInstance(facet).moduleMinApi

      return TagToClassMapper.getInstance(facet.module).getClassMap(myBaseClassQName)
        .filter { (name, psiClass) ->
          return@filter if (packagePrefix.isEmpty() && name == psiClass.qualifiedName) {
            // Don't suggest FQN when we can use short one.
            isClassPackageNeeded(psiClass.qualifiedName!!, psiClass, apiLevel, myBaseClassQName)
          }
          else {
            name.startsWith(packagePrefix)
          }
        }
        .filter { (name, psiClass) -> classFilter(name, psiClass) }
        .map { (name, psiClass) -> createClassAsTagXmlElement(name.removePrefix(packagePrefix), psiClass) }.toTypedArray()
    }

    override fun bindToElement(element: PsiElement): PsiElement {
      val newName = (element as PsiQualifiedNamedElement).qualifiedName
      val range = TextRange(0, myRangeInNameElement.endOffset)
      return ElementManipulators.handleContentChange(myNameElement.psi, range, newName)
    }

    override fun handleElementRename(newElementName: String): PsiElement? {
      return ElementManipulators.handleContentChange(myNameElement.psi, myRangeInNameElement, newElementName)
    }

    override fun getRangeInElement(): TextRange {
      val parentOffset = myNameElement.startOffsetInParent
      return TextRange(parentOffset + myRangeInNameElement.startOffset, parentOffset + myRangeInNameElement.endOffset)
    }
  }

  companion object {
    private fun addReferences(tag: XmlTag,
                              nameElement: ASTNode,
                              result: MutableList<PsiReference>,
                              facet: AndroidFacet,
                              baseClassQName: String,
                              classFilter: (String, PsiClass) -> Boolean,
                              startTag: Boolean) {
      val text = nameElement.text
      val nameParts = text.split(".")

      var offset = 0
      for (name in nameParts) {
        if (name.isNotEmpty()) {
          offset += name.length
          val range = TextRange(offset - name.length, offset)
          result.add(MyClassOrPackageReference(tag, nameElement, range, facet, baseClassQName, classFilter, startTag))
        }
        offset++
      }
    }

    private fun computeBaseClass(tag: XmlTag): String? {
      val domElement = DomManager.getDomManager(tag.project).getDomElement(tag)
      return when {
        domElement is LayoutViewElement && domElement !is View -> CLASS_VIEW
        domElement is PreferenceElement -> {
          val facet = tag.androidFacet ?: return null
          PreferenceSource.getPreferencesSource(tag, facet).qualifiedBaseClass
        }
        domElement is CustomDrawableDomElement -> CLASS_DRAWABLE
        else -> null
      }
    }
  }
}

/**
 * Creates a [LookupElement] for layout tags, to improve editing UX.
 *
 * Makes possible to do completions like "TvV" to "android.media.tv.TvView".
 * Adds [XmlTagInnerClassInsertHandler] for inner classes.
 * Adds low priority for deprecated classes and high priority for androidx and support library alternative.
 */
fun createClassAsTagXmlElement(name: String, clazz: PsiClass): LookupElement {

  var lookupElement = LookupElementBuilder.create(clazz, name)

  val qualifiedName = clazz.qualifiedName!!
  val shortClassName = clazz.name!!

  lookupElement = lookupElement.withLookupString(qualifiedName)
  lookupElement = lookupElement.withLookupString(shortClassName)

  val priority = when {
    clazz.isDeprecated -> -1
    clazz.name == name -> 1
    qualifiedName.startsWith(SdkConstants.ANDROID_SUPPORT_PKG_PREFIX) || qualifiedName.startsWith(SdkConstants.ANDROIDX_PKG_PREFIX) -> 2
    else -> 0
  }

  AndroidDomElementDescriptorProvider.getIconForViewTag(shortClassName)?.let {
    lookupElement = lookupElement.withIcon(it)
  }

  if (clazz.containingClass != null) {
    lookupElement = lookupElement.withInsertHandler(XmlTagInnerClassInsertHandler.INSTANCE)
  }
  else {
    lookupElement = lookupElement.withInsertHandler(XmlTagInsertHandler.INSTANCE)
  }

  return PrioritizedLookupElement.withPriority(lookupElement, priority.toDouble())
}