/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.escape.xml.CharacterDataEscaper
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlTagChild
import com.intellij.util.IncorrectOperationException
import java.io.IOException
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager

/** An object that can carry out various write operations related to string resources. */
interface StringResourceWriter {
  /**
   * Returns the string resource file for the default [Locale]. If the values directory or the
   * string resource file do not exist, this method will create those first.
   *
   * @return the string resource file for the default [Locale], or `null` if the directory or file
   * could not be created
   */
  fun getStringResourceFile(
      project: Project,
      resourceDirectory: VirtualFile,
  ): XmlFile? = getStringResourceFile(project, resourceDirectory, locale = null)

  /**
   * Returns the string resource file for the optionally given [locale]. If the values directory or
   * the string resource file do not exist, this method will create those first.
   *
   * @return the string resource file for the given [locale], or `null` if the directory or file
   * could not be created
   */
  fun getStringResourceFile(
      project: Project,
      resourceDirectory: VirtualFile,
      locale: Locale? = null
  ): XmlFile?

  /** Deletes the locale-specific string resource file. */
  fun removeLocale(locale: Locale, facet: AndroidFacet, requestor: Any)

  /**
   * Sets the value of the given [attribute] on the given [item] to be [value]. If [value] is
   * `null`, the attribute will be removed entirely.
   *
   * @return `true` iff the attribute was modified successfully
   */
  fun setAttribute(
      project: Project,
      attribute: String,
      value: String?,
      item: ResourceItem
  ): Boolean = setAttribute(project, attribute, value, listOf(item))
  /**
   * Sets the value of the given [attribute] on each of the given [items] to be [value]. If [value]
   * is `null`, the attribute will be removed from each item entirely.
   * @return `true` iff the attribute was modified successfully and [items] was not empty
   */
  fun setAttribute(
      project: Project,
      attribute: String,
      value: String?,
      items: Collection<ResourceItem>
  ): Boolean

  /**
   * Deletes the given [item] from the string resource file.
   *
   * @return `true` iff the [item] was successfully deleted
   */
  fun delete(project: Project, item: ResourceItem): Boolean = delete(project, listOf(item))
  /**
   * Deletes all the given [items] from the string resource file.
   * @return `true` iff the [items] were successfully deleted and [items] was not empty
   */
  fun delete(project: Project, items: Collection<ResourceItem>): Boolean

  /**
   * Sets the text of the given [item] to be [value].
   * @return `true` iff the text was set successfully
   */
  fun setItemText(project: Project, item: ResourceItem, value: String): Boolean

  companion object {
    /** A singleton instance of a [StringResourceWriter]. */
    @JvmField val INSTANCE: StringResourceWriter = StringResourceWriterImpl
  }
}

private object StringResourceWriterImpl : StringResourceWriter {
  override fun getStringResourceFile(
      project: Project,
      resourceDirectory: VirtualFile,
      locale: Locale?
  ): XmlFile? {
    val valuesDirectory =
        findOrCreateValuesDirectory(project, resourceDirectory, locale) ?: return null
    return findOrCreateStringResourceFile(project, valuesDirectory)
  }

  /**
   * Finds or creates (if necessary) the values directory inside the resource directory.
   *
   * @return the directory, or `null` if it could not be created
   */
  private fun findOrCreateValuesDirectory(
      project: Project,
      resourceDirectory: VirtualFile,
      locale: Locale?
  ): VirtualFile? {
    val config = FolderConfiguration()
    if (locale != null) config.localeQualifier = locale.qualifier
    val psiManager = PsiManager.getInstance(project)
    val valuesDirectoryName = config.getFolderName(ResourceFolderType.VALUES)

    return try {
      resourceDirectory.findChild(valuesDirectoryName)
          ?: WriteCommandAction.writeCommandAction(project, psiManager.findFile(resourceDirectory))
              .withName("Creating directory $valuesDirectoryName")
              .compute<VirtualFile?, IOException> {
                resourceDirectory.createChildDirectory(this, valuesDirectoryName)
              }
    } catch (e: IOException) {
      null
    }
  }

  /**
   * Finds or creates (if necessary) the string resource file inside the values directory.
   *
   * @return the file, or `null` if it could not be created
   */
  private fun findOrCreateStringResourceFile(
      project: Project,
      valuesDirectory: VirtualFile,
  ): XmlFile? {
    val resourceFileName = getDefaultResourceFileName(ResourceType.STRING) ?: return null
    val psiManager = PsiManager.getInstance(project)
    valuesDirectory.findChild(resourceFileName)?.let {
      return psiManager.findFile(it) as? XmlFile
    }

    val valuesDir = psiManager.findDirectory(valuesDirectory) ?: return null
    return try {
      createFileResource(
          resourceFileName,
          valuesDir,
          rootTagName = "",
          ResourceType.STRING.name,
          valuesResourceFile = true)
    } catch (e: Exception) {
      null
    }
  }

  override fun removeLocale(locale: Locale, facet: AndroidFacet, requestor: Any) {
    WriteCommandAction.writeCommandAction(facet.module.project)
        .withName("Remove $locale Locale")
        .withGlobalUndo()
        .run<Nothing> {
          val name: String =
              FolderConfiguration()
                  .apply { localeQualifier = locale.qualifier }
                  .getFolderName(ResourceFolderType.VALUES)

          ResourceFolderManager.getInstance(facet)
              .folders
              .mapNotNull { it.findChild(name) }
              .forEach {
                try {
                  it.delete(requestor)
                } catch (e: IOException) {
                  Logger.getInstance(this.javaClass).warn(e)
                }
              }
        }
  }

  override fun setAttribute(
      project: Project,
      attribute: String,
      value: String?,
      items: Collection<ResourceItem>
  ): Boolean =
      items.modify(project, "Setting attribute $attribute") { it.setAttribute(attribute, value) }

  override fun delete(project: Project, items: Collection<ResourceItem>): Boolean =
      items.modify(project, "Deleting resources", XmlTag::delete)

  /**
   * Runs the given [modification] on the [XmlTag] for each [ResourceItem] in `this` [Collection].
   * @return true iff the modification changed anything
   */
  private fun Collection<ResourceItem>.modify(
      project: Project,
      operationName: String,
      modification: (XmlTag) -> Unit
  ): Boolean {
    if (isEmpty()) return false
    // Figure out which files they are in because we will need to make them writable.
    val fileToTagMap = map { getItemTag(project, it) ?: return false }.groupBy { it.containingFile }

    WriteCommandAction.writeCommandAction(project, fileToTagMap.keys).withName(operationName).run<
        IncorrectOperationException> {
      CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
      fileToTagMap.values.forEach { tagList -> tagList.forEach(modification) }
    }

    return true
  }

  override fun setItemText(project: Project, item: ResourceItem, value: String): Boolean {
    if (value.isEmpty()) return delete(project, item)
    val tag = getItemTag(project, item) ?: return false

    WriteCommandAction.writeCommandAction(project, tag.containingFile)
        .withName("Setting value of ${tag.name}")
        .run<IncorrectOperationException> {
          // Makes the command global even if only one xml file is modified.
          // That way, the Undo is always available from the translation editor.
          CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
          // First remove the existing value of the tag (any text and possibly other XML nested
          // tags - like xliff:g).
          tag.value.children.forEach(XmlTagChild::delete)
          val escapedXml =
              try {
                CharacterDataEscaper.escape(value)
              } catch (e: IllegalArgumentException) {
                Logger.getInstance(this.javaClass).warn(e)
                value
              }
          val text: XmlTag =
              XmlElementFactory.getInstance(project)
                  .createTagFromText("<String>$escapedXml</string")
          text.value.children.forEach(tag::add)
        }
    return true
  }
}
