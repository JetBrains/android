/*
 * Copyright (C) 2015 The Android Open Source Project
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
@file:JvmName("RecipeMergeUtils")

package com.android.tools.idea.templates

import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.SdkConstants.XMLNS_PREFIX
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.MergingReport
import com.android.manifmerger.XmlDocument
import com.android.resources.ResourceFolderType
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.utils.StdLogger
import com.android.utils.XmlUtils
import com.google.common.base.Charsets
import com.google.common.collect.Lists.newArrayList
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlComment
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlTagChild
import com.intellij.psi.xml.XmlText
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Utility methods to support the recipe.xml merge instruction.
 */

private val LOG: Logger get() = Logger.getInstance("RecipeMergeUtils.kt")

private const val MERGE_ATTR_STRATEGY = "templateMergeStrategy"
private const val MERGE_ATTR_STRATEGY_REPLACE = "replace"
private const val MERGE_ATTR_STRATEGY_PRESERVE = "preserve"

/**
 * Merges sourceXml into targetXml/targetFile (targetXml is the contents of targetFile).
 * @return the resulting xml if it still needs to be written to targetFile or null if the file has already been/doesn't need to be updated.
 */
fun mergeXml(context: RenderingContext, sourceXml: String, targetXml: String, targetFile: File): String {
  val fileName = targetFile.name
  var errors: String? = null

  fun mergeManifest(): String? {
    XmlUtils.parseDocumentSilently(targetXml, true) ?: error("$targetXml failed to parse")
    XmlUtils.parseDocumentSilently(sourceXml, true) ?: error("$sourceXml failed to parse")
    val namespace = context.moduleTemplateData?.namespace ?: ""
    val report = mergeManifest(namespace, context.moduleRoot!!, targetFile, targetXml, sourceXml) ?: return null
    if (report.result.isSuccess) {
      return report.getMergedDocument(MergingReport.MergedManifestKind.MERGED)
    }
    // report.reportString isn't useful, it just says to look at the logs
    // Also, some of the warnings are misleading -- e.g. "missing package declaration";
    // that's deliberate. Users only have to deal with errors to get the manifest merge to succeed.
    errors = report.loggingRecords.asSequence()
      .filter { it.severity == MergingReport.Record.Severity.ERROR }
      .joinToString("") { "* ${it.message}\n\n" }
      .replace("AndroidManifest.xml", "current AndroidManifest.xml") // Error messages may refer to our internal temp name for the target manifest file
      .replace("nevercreated.xml", "template AndroidManifest.xml")
      .trim()
    return null
  }

  fun mergePlainXml(): String {
    val parentFolderName = targetFile.parentFile.name
    val folderType = ResourceFolderType.getFolderType(parentFolderName)
    // mergeResourceFile handles the file updates itself
    return mergeResourceFile(context.project, context.warnings, targetXml, sourceXml, fileName, folderType)
  }

  return (if (fileName == FN_ANDROID_MANIFEST_XML) mergeManifest() else mergePlainXml())
         ?: // Just insert into file along with comment, using the "standard" conflict syntax that many tools and editors recognize.
         wrapWithMergeConflict(targetXml, sourceXml).also {
           context.warnings.add(
             "Merge conflict for: ${targetFile.name}\nThis file must be fixed by hand. Errors encountered during the merge:\n\n$errors")
         }
}

/**
 * Merges the given resource file contents into the given resource file
 */
fun mergeResourceFile(project: Project,
                      warningsToAdd: MutableCollection<String>,
                      targetXml: String,
                      sourceXml: String,
                      fileName: String,
                      folderType: ResourceFolderType?): String {
  val targetPsiFile = PsiFileFactory.getInstance(project)
    .createFileFromText("targetFile", XMLLanguage.INSTANCE, StringUtil.convertLineSeparators(targetXml), false, true) as XmlFile
  val sourcePsiFile = PsiFileFactory.getInstance(project)
    .createFileFromText("sourceFile", XMLLanguage.INSTANCE, StringUtil.convertLineSeparators(sourceXml), false, true) as XmlFile
  val root = targetPsiFile.document!!.rootTag ?: error("Cannot find XML root in target: $targetXml")

  val attributes = sourcePsiFile.rootTag!!.attributes
  attributes.filter {it.namespacePrefix == XMLNS_PREFIX}.forEach { root.setAttribute(it.name, it.value) }

  if (folderType != ResourceFolderType.VALUES) {
    // In other file types, such as layouts, just append all the new content at the end.
    sourcePsiFile.rootTag!!.children.filterIsInstance<XmlTag>().forEach {
      root.addSubTag(it, false)
    }
    return targetPsiFile.text
  }

  val prependElements = newArrayList<XmlTagChild>()
  var indent: XmlText? = null
  // Try to merge items of the same name
  val old = root.subTags.associateBy { getResourceId(it) }
  loop@ for (child in sourcePsiFile.rootTag!!.children) {
    when (child) {
      is XmlComment -> {
        if (indent != null) {
          prependElements.add(indent)
        }
        prependElements.add(child as XmlTagChild)
      }
      is XmlText -> indent = child
      is XmlTag -> {
        var subTag = child
        val mergeStrategy = subTag.getAttributeValue(MERGE_ATTR_STRATEGY)
        subTag.setAttribute(MERGE_ATTR_STRATEGY, null)
        // remove the space left by the deleted attribute
        CodeStyleManager.getInstance(project).reformat(subTag)
        val name = getResourceId(subTag)
        val replace = if (name == null) null else old[name]
        if (replace == null) {
          if (indent != null) {
            prependElements.add(indent)
          }
          subTag = root.addSubTag(subTag, false)
          prependElements.forEach {
            root.addBefore(it, subTag)
          }
          prependElements.clear()
          continue@loop
        }
        // There is an existing item with the same id. Either replace it  or preserve it depending on the "templateMergeStrategy" attribute.
        // If that attribute does not exist, default to preserving it.

        // Let's say you've used the activity wizard once, and it  emits some configuration parameter as a resource that
        // it depends on, say "padding". Then the user goes and  tweaks the padding to some other number.
        // Now running the wizard a *second* time for some new activity, we should NOT go and set the value back to the template's default!
        when {
          MERGE_ATTR_STRATEGY_REPLACE == mergeStrategy -> {
            val newChild = replace.replace(child)
            // When we're replacing, the line is probably already indented. Skip the initial indent
            if (newChild.prevSibling is XmlText && prependElements[0] is XmlText) {
              prependElements.removeAt(0)
              // If we're adding something we'll need a newline/indent after it
              if (prependElements.isNotEmpty()) {
                prependElements.add(indent)
              }
            }
            for (element in prependElements) {
              root.addBefore(element, newChild)
            }
          }
          MERGE_ATTR_STRATEGY_PRESERVE == mergeStrategy -> {
            // Preserve the existing value.
          }
          areXmlTagsEquivalent(replace, child) -> {
            // There are no differences, do not issue a warning.
          }
          else -> // No explicit directive given, preserve the original value by default.
            warningsToAdd.add("Ignoring conflict for the value: $name wanted: \"%${child.text}\" but it already is: \"%${replace.text}\" in the file: $fileName")
        }
        prependElements.clear()
      }
    }
  }

  return targetPsiFile.text
}

/**
 * Checks if the two XML tags have the same names and the same sets of attributes.
 */
private fun areXmlTagsEquivalent(element1: XmlTag, element2: XmlTag): Boolean {
  if (element1.name != element2.name) {
    return false
  }
  val attributes1 = element1.attributes.sortedBy(XmlAttribute::getName)
  val attributes2 = element2.attributes.sortedBy(XmlAttribute::getName)
  if (attributes1.size != attributes2.size) {
    return false
  }
  for (i in attributes1.indices) {
    val attr1 = attributes1[i]
    val attr2 = attributes2[i]
    if (attr1.name != attr2.name || attr1.value != attr2.value) {
      return false
    }
  }
  return true
}

/**
 * Merges the given manifest fragment into the given manifest file
 */
private fun mergeManifest(namespace: String, moduleRoot: File, targetManifest: File,
                          targetXml: String, mergeText: String): MergingReport? {
  try {
    val isMasterManifest = FileUtil.filesEqual(moduleRoot, targetManifest.parentFile)

    val tempFile2 = File(targetManifest.parentFile, "nevercreated.xml")
    val logger = StdLogger(StdLogger.Level.INFO)
    return ManifestMerger2.newMerger(targetManifest, logger, ManifestMerger2.MergeType.APPLICATION)
      .withFeatures(ManifestMerger2.Invoker.Feature.EXTRACT_FQCNS,
                    ManifestMerger2.Invoker.Feature.HANDLE_VALUE_CONFLICTS_AUTOMATICALLY,
                    ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT)
      .setNamespace(namespace)
      .addFlavorAndBuildTypeManifest(tempFile2)
      .asType(if (isMasterManifest) XmlDocument.Type.MAIN else XmlDocument.Type.OVERLAY)
      .withFileStreamProvider(object : ManifestMerger2.FileStreamProvider() {
        @Throws(FileNotFoundException::class)
        override fun getInputStream(file: File): InputStream {
          val text = if (FileUtil.filesEqual(file, targetManifest)) targetXml else mergeText
          return ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))
        }
      })
      .merge()
  }
  catch (e: ManifestMerger2.MergeFailureException) {
    LOG.warn(e)
    return null
  }
}

private fun getResourceId(tag: XmlTag): String? = tag.getAttributeValue(ATTR_NAME) ?: tag.getAttributeValue(ATTR_ID)

/**
 * Wraps the given strings in the standard conflict syntax
 */
private fun wrapWithMergeConflict(original: String, added: String): String =
  "<<<<<<< Original\n$original\n=======\n$added>>>>>>> Added\n"
