// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.refactoring

import com.android.SdkConstants
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceFolderType
import com.android.tools.idea.rendering.setIncludingLayout
import com.android.utils.SdkUtils
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomManager
import org.jetbrains.android.actions.CreateResourceFileAction
import org.jetbrains.android.dom.layout.Include
import org.jetbrains.android.dom.layout.LayoutViewElement
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly


class AndroidExtractAsIncludeAction : AndroidBaseLayoutRefactoringAction {
  private val myTestConfig: MyTestConfig?

  constructor() {
    myTestConfig = null
  }

  @TestOnly
  constructor(testConfig: MyTestConfig?) {
    myTestConfig = testConfig
  }

  override fun doRefactorForTags(project: Project, tags: Array<XmlTag>) {
    if (tags.size == 0) {
      return
    }
    val file = tags[0].containingFile ?: return
    var startTag: XmlTag? = null
    var endTag: XmlTag? = null
    var startOffset = Int.MAX_VALUE
    var endOffset = -1

    for (tag in tags) {
      val range = tag.textRange

      val start = range.startOffset
      if (start < startOffset) {
        startOffset = start
        startTag = tag
      }

      val end = range.endOffset
      if (end > endOffset) {
        endOffset = end
        endTag = tag
      }
    }
    assert(startTag != null && endTag != null)
    doRefactorForPsiRange(project, file, startTag!!, endTag!!)
  }

  override fun isEnabledForTags(tags: Array<XmlTag>): Boolean {
    if (tags.size == 0) {
      return false
    }
    val domManager = DomManager.getDomManager(tags[0].project)
    var containsViewElement = false

    for (tag in tags) {
      val domElement = domManager.getDomElement(tag)

      if (!isSuitableDomElement(domElement)) {
        return false
      }
      if (domElement is LayoutViewElement) {
        containsViewElement = true
      }
    }
    if (!containsViewElement) {
      return false
    }
    val parent = tags[0].parent

    if (parent !is XmlTag || parent.getContainingFile() == null) {
      return false
    }

    for (i in 1 until tags.size) {
      if (tags[i].parent !== parent) {
        return false
      }
    }
    return true
  }

  override fun doRefactorForPsiRange(
    project: Project, file: PsiFile, from: PsiElement,
    to: PsiElement
  ) {
    val dir = file.containingDirectory ?: return
    val facet = checkNotNull(AndroidFacet.getInstance(from))
    val parentTag = checkNotNull(PsiTreeUtil.getParentOfType(from, XmlTag::class.java))
    val tagsInRange = collectAllTags(from, to)
    assert(!tagsInRange.isEmpty()) { "there is no tag inside the range" }
    val fileName = myTestConfig?.myLayoutFileName
    val dirName = dir.name
    val config = if (!dirName.isEmpty())
      FolderConfiguration.getConfig(dirName.split(SdkConstants.RES_QUALIFIER_SEP.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
    else
      null
    val title = "Extract Android Layout"

    DataManager.getInstance()
      .dataContextFromFocusAsync
      .onSuccess { dataContext: DataContext? ->
        CommandProcessor.getInstance().executeCommand(project, {
          val newFile =
            CreateResourceFileAction
              .createFileResource(
                facet, ResourceFolderType.LAYOUT, fileName, "temp_root", config, true, title,
                null, dataContext
              )
          if (newFile != null) {
            ApplicationManager.getApplication().runWriteAction {
              doRefactor(
                facet,
                file,
                newFile,
                from,
                to,
                parentTag,
                tagsInRange.size > 1
              )
            }
          }
        }, title, null, UndoConfirmationPolicy.REQUEST_CONFIRMATION)
      }
  }

  override fun isEnabledForPsiRange(from: PsiElement, to: PsiElement?): Boolean {
    val domManager = DomManager.getDomManager(from.project)
    var e: PsiElement? = from
    var containsViewElement = false

    while (e != null) {
      if (e is XmlTag) {
        val domElement = domManager.getDomElement(e)

        if (!isSuitableDomElement(domElement)) {
          return false
        }
        if (domElement is LayoutViewElement) {
          containsViewElement = true
        }
      }
      if (e === to) {
        break
      }
      e = e.nextSibling
    }
    return containsViewElement
  }

  class MyTestConfig(val myLayoutFileName: String)
  companion object {
    const val ACTION_ID: @NonNls String = "AndroidExtractAsIncludeAction"

    private fun doRefactor(
      facet: AndroidFacet,
      file: PsiFile,
      newFile: XmlFile,
      from: PsiElement,
      to: PsiElement,
      parentTag: XmlTag,
      wrapWithMerge: Boolean
    ) {
      val project = facet.module.project
      val textToExtract = file.text.substring(
        from.textRange.startOffset,
        to.textRange.endOffset
      )
      val documentManager = PsiDocumentManager.getInstance(project)
      val document = checkNotNull(documentManager.getDocument(newFile))
      document.setText(
        """<?xml version="1.0" encoding="utf-8"?>
${if (wrapWithMerge) "<merge>\n$textToExtract\n</merge>" else textToExtract}"""
      )
      documentManager.commitDocument(document)

      val unknownPrefixes: MutableSet<String> = HashSet()

      newFile.accept(object : XmlRecursiveElementVisitor() {
        override fun visitXmlTag(tag: XmlTag) {
          super.visitXmlTag(tag)
          val prefix = tag.namespacePrefix

          if (!unknownPrefixes.contains(prefix) && tag.namespace.isEmpty()) {
            unknownPrefixes.add(prefix)
          }
        }

        override fun visitXmlAttribute(attribute: XmlAttribute) {
          val prefix = attribute.namespacePrefix

          if (!unknownPrefixes.contains(prefix) && attribute.namespace.isEmpty()) {
            unknownPrefixes.add(prefix)
          }
        }
      })

      val rootTag = checkNotNull(newFile.rootTag)
      val elementFactory = XmlElementFactory.getInstance(project)
      val attributes = rootTag.attributes
      val firstAttribute = if (attributes.size > 0) attributes[0] else null

      for (prefix in unknownPrefixes) {
        val namespace = parentTag.getNamespaceByPrefix(prefix)
        val xmlNsAttrName = "xmlns:$prefix"

        if (!namespace.isEmpty() && rootTag.getAttribute(xmlNsAttrName) == null) {
          val xmlnsAttr = elementFactory.createXmlAttribute(xmlNsAttrName, namespace)

          if (firstAttribute != null) {
            rootTag.addBefore(xmlnsAttr, firstAttribute)
          } else {
            rootTag.add(xmlnsAttr)
          }
        }
      }

      val includingLayout = SdkConstants.LAYOUT_RESOURCE_PREFIX + SdkUtils.fileNameToResourceName(file.name)
      setIncludingLayout(newFile, includingLayout)

      val resourceName = SdkUtils.fileNameToResourceName(newFile.name)
      val includeTag = elementFactory.createTagFromText("<include layout=\"@layout/$resourceName\"/>")
      parentTag.addAfter(includeTag, to)
      parentTag.deleteChildRange(from, to)

      val codeStyleManager = CodeStyleManager.getInstance(project)
      codeStyleManager.reformat(newFile)
    }

    private fun collectAllTags(from: PsiElement, to: PsiElement): List<XmlTag> {
      val result: MutableList<XmlTag> = ArrayList()
      var e: PsiElement? = from

      while (e != null) {
        if (e is XmlTag) {
          result.add(e)
        }
        if (e === to) {
          break
        }
        e = e.nextSibling
      }
      checkNotNull(e) { "invalid range" }
      return result
    }

    private fun isSuitableDomElement(element: DomElement?): Boolean {
      return element is LayoutViewElement ||
        element is Include
    }
  }
}
