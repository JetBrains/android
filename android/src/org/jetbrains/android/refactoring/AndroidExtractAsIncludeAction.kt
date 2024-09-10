/*
 * Copyright (C) 2024 The Android Open Source Project
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
package org.jetbrains.android.refactoring

import com.android.SdkConstants
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceFolderType
import com.android.tools.idea.rendering.setIncludingLayout
import com.android.utils.SdkUtils
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
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
import com.intellij.psi.util.siblings
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.application
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomManager
import org.jetbrains.android.actions.CreateResourceFileAction
import org.jetbrains.android.dom.layout.Include
import org.jetbrains.android.dom.layout.LayoutViewElement
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.range
import org.jetbrains.kotlin.util.takeWhileInclusive

private const val TITLE = "Extract Android Layout"

/**
 * Finds all the [XmlTag]s between [from] and [to], inclusive, and returns them in a [Sequence].
 *
 * If [mustFindTo] is `true`, throws if it runs out of siblings before encountering [to].
 */
private fun xmlTagSiblingsBetween(from: PsiElement, to: PsiElement?, mustFindTo: Boolean = false): Sequence<XmlTag> =
  from.siblings().takeWhileInclusive {
    if (mustFindTo && it.nextSibling == null) check(it === to) { "invalid range" }
    it !== to
  }
    .filterIsInstance(XmlTag::class.java)

/** Determines whether a [DomElement] is either a [LayoutViewElement] or an [Include]. */
private fun DomElement?.isSuitable() = this is LayoutViewElement || this is Include

/** Determines whether every [XmlTag] in a [Sequence] is [isSuitable] and any of them is a [LayoutViewElement]. */
private fun Sequence<XmlTag>.allSuitableAndContainsViewElement(project: Project) : Boolean {
  val domManager = DomManager.getDomManager(project)
  val domElements = this.map(domManager::getDomElement)
  // Every DomElement must be either a LayoutViewElement or an Include. We also
  // keep track of whether we have at least one LayoutViewElement.
  var containsLayoutViewElement = false
  for (domElement in domElements) {
    if (domElement is LayoutViewElement) containsLayoutViewElement = true
    else if (domElement !is Include) return false
  }
  return containsLayoutViewElement
}

class AndroidExtractAsIncludeAction(private val testConfig: TestConfig? = null) :
  AndroidBaseLayoutRefactoringAction() {
  override fun doRefactorForTags(project: Project, tags: Array<XmlTag>) {
    if (tags.isEmpty()) return
    val startTag = tags.minBy { it.range.startOffset }
    val endTag = tags.maxBy { it.range.endOffset }
    val file = tags[0].containingFile ?: return
    doRefactorForPsiRange(project, file, startTag, endTag)
  }

  override fun isEnabledForTags(tags: Array<XmlTag>): Boolean {
    if (tags.isEmpty()) return false
    if (!tags.asSequence().allSuitableAndContainsViewElement(tags.first().project)) return false

    val parent = tags[0].parent

    return parent is XmlTag &&
      parent.containingFile != null &&
      tags.drop(1).all { it.parent === parent }
  }

  override fun doRefactorForPsiRange(
    project: Project,
    file: PsiFile,
    from: PsiElement,
    to: PsiElement,
  ) {
    val dir = file.containingDirectory ?: return
    val facet = checkNotNull(AndroidFacet.getInstance(from))
    val parentTag = checkNotNull(PsiTreeUtil.getParentOfType(from, XmlTag::class.java))
    val numTags = xmlTagSiblingsBetween(from, to, mustFindTo = true).count()
    assert(numTags > 0) { "there is no tag inside the range" }
    val config = dir.name.takeIf(String::isNotEmpty)?.let {
      FolderConfiguration.getConfig(
        it
          .split(SdkConstants.RES_QUALIFIER_SEP.toRegex())
          .toTypedArray()
      )
    }

    DataManager.getInstance().dataContextFromFocusAsync.onSuccess { dataContext: DataContext? ->
      CommandProcessor.getInstance()
        .executeCommand(
          project,
          {
            val newFile = CreateResourceFileAction.createFileResource(
              facet,
              ResourceFolderType.LAYOUT,
              testConfig?.layoutFileName,
              "temp_root",
              config,
              true,
              TITLE,
              null,
              dataContext,
            )
            if (newFile != null)
              application.runWriteAction {
                doRefactor(facet, file, newFile, from, to, parentTag, numTags > 1)
              }
          },
          TITLE,
          null,
          UndoConfirmationPolicy.REQUEST_CONFIRMATION,
        )
    }
  }

  override fun isEnabledForPsiRange(from: PsiElement, to: PsiElement?) =
    xmlTagSiblingsBetween(from, to).allSuitableAndContainsViewElement(from.project)

  class TestConfig(val layoutFileName: String)

  companion object {
    const val ACTION_ID: @NonNls String = "AndroidExtractAsIncludeAction"

    private fun doRefactor(
      facet: AndroidFacet,
      file: PsiFile,
      newFile: XmlFile,
      from: PsiElement,
      to: PsiElement,
      parentTag: XmlTag,
      wrapWithMerge: Boolean,
    ) {
      val project = facet.module.project
      val textToExtract = file.text.substring(from.textRange.startOffset, to.textRange.endOffset)
      val documentManager = PsiDocumentManager.getInstance(project)
      val document = checkNotNull(documentManager.getDocument(newFile))
      document.setText(
        """<?xml version="1.0" encoding="utf-8"?>
${if (wrapWithMerge) "<merge>\n$textToExtract\n</merge>" else textToExtract}"""
      )
      documentManager.commitDocument(document)

      val unknownPrefixes: MutableSet<String> = mutableSetOf()

      newFile.accept(
        object : XmlRecursiveElementVisitor() {
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
        }
      )

      val rootTag = checkNotNull(newFile.rootTag)
      val elementFactory = XmlElementFactory.getInstance(project)
      val attributes = rootTag.attributes
      val firstAttribute = attributes.firstOrNull()

      for (prefix in unknownPrefixes) {
        val namespace = parentTag.getNamespaceByPrefix(prefix)
        val xmlNsAttrName = "xmlns:$prefix"

        if (namespace.isNotEmpty() && rootTag.getAttribute(xmlNsAttrName) == null) {
          val xmlnsAttr = elementFactory.createXmlAttribute(xmlNsAttrName, namespace)

          if (firstAttribute == null) {
            rootTag.add(xmlnsAttr)
          } else {
            rootTag.addBefore(xmlnsAttr, firstAttribute)
          }
        }
      }

      val includingLayout =
        SdkConstants.LAYOUT_RESOURCE_PREFIX + SdkUtils.fileNameToResourceName(file.name)
      setIncludingLayout(newFile, includingLayout)

      val resourceName = SdkUtils.fileNameToResourceName(newFile.name)
      val includeTag =
        elementFactory.createTagFromText("<include layout=\"@layout/$resourceName\"/>")
      parentTag.addAfter(includeTag, to)
      parentTag.deleteChildRange(from, to)

      CodeStyleManager.getInstance(project).reformat(newFile)
    }
  }
}
