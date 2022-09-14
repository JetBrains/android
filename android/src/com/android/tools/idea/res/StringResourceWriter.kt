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

import com.android.SdkConstants
import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.escape.xml.CharacterDataEscaper
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.idea.editors.strings.model.StringResourceKey
import com.intellij.ide.util.DeleteHandler
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlTagChild
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.safeDelete.SafeDeleteDialog
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager
import java.io.IOException

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
  ): XmlFile?

  /**
   * Adds a new resource string default with the given [value].
   *
   * @param key A [StringResourceKey] specifying the name of the resource and the directory in which
   * it will be created
   * @param value the default value of the resource
   * @param translatable whether the resource is translatable
   * @return true iff the resource was successfully created
   */
  fun addDefault(
    project: Project,
    key: StringResourceKey,
    value: String,
    translatable: Boolean = true,
  ): Boolean

  /**
   * Adds a new resource string translation with the given [value].
   *
   * The [insertBefore] key cannot be in a different directory than the [key] being inserted.
   *
   * @param key A [StringResourceKey] specifying the name of the resource and the directory in which
   * it will be created
   * @param locale The [Locale] for which the translation applies
   * @param resourceFileName The preferred file name for the resource
   * @param insertBefore A [StringResourceKey] before which the new resource should be added.
   * @return true iff the resource was successfully created
   */
  fun addTranslation(
    project: Project,
    key: StringResourceKey,
    value: String,
    locale: Locale,
    resourceFileName: String = DEFAULT_STRING_RESOURCE_FILE_NAME,
    insertBefore: StringResourceKey? = null,
  ): Boolean

  /**
   * Adds a new resource string translation with the given [value] to a specific file.
   *
   * The [insertBefore] key cannot be in a different directory than the [key] being inserted.
   *
   * @param key A [StringResourceKey] specifying the name of the resource and the directory in which
   * it will be created
   * @param xmlFile An [XmlFile] to which the resource string translation should be added
   * @param insertBefore A [StringResourceKey] before which the new resource should be added.
   * @return true iff the resource was successfully created
   */
  fun addTranslationToFile(
    project: Project,
    xmlFile: XmlFile,
    key: StringResourceKey,
    value: String,
    insertBefore: StringResourceKey? = null,
  ): Boolean

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
      item: ResourceItem,
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
      items: Collection<ResourceItem>,
  ): Boolean

  /**
   * Deletes the given [item] from the string resource file.
   * @return `true` iff the [item] was successfully deleted
   */
  fun delete(project: Project, item: ResourceItem): Boolean = delete(project, listOf(item))
  /**
   * Deletes all the given [items] from the string resource files.
   * @return `true` iff the [items] were successfully deleted and [items] was not empty
   */
  fun delete(project: Project, items: Collection<ResourceItem>): Boolean

  /**
   * Attempts to safely delete the given [item] from the string resource file.
   * @param successCallback callback invoked iff the delete operation completes successfully
   */
  fun safeDelete(project: Project, item: ResourceItem, successCallback: Runnable) {
    safeDelete(project, listOf(item), successCallback)
  }
  /**
   * Attempts to safely delete the given [items] from the string resource files.
   * @param successCallback callback invoked iff the delete operation completes successfully
   */
  fun safeDelete(project: Project, items: Collection<ResourceItem>, successCallback: Runnable)

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

  override fun getStringResourceFile(project: Project, resourceDirectory: VirtualFile): XmlFile? =
    getStringResourceFile(project, resourceDirectory, locale = null, DEFAULT_STRING_RESOURCE_FILE_NAME)

  private fun getStringResourceFile(
      project: Project,
      resourceDirectory: VirtualFile,
      locale: Locale?,
      resourceFileName: String,
  ): XmlFile? {
    val valuesDirectory =
        findOrCreateValuesDirectory(project, resourceDirectory, locale) ?: return null
    return findOrCreateStringResourceFile(project, valuesDirectory, resourceFileName)
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
      resourceFileName: String,
  ): XmlFile? {
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

  override fun addDefault(project: Project, key: StringResourceKey, value: String, translatable: Boolean): Boolean =
    add(project, key, value, DEFAULT_STRING_RESOURCE_FILE_NAME, translatable = translatable)

  override fun addTranslation(
    project: Project,
    key: StringResourceKey,
    value: String,
    locale: Locale,
    resourceFileName: String,
    insertBefore: StringResourceKey?,
  ): Boolean {
    require(insertBefore == null || key.directory == insertBefore.directory) {
      "Can't insert before a key in a different directory."
    }
    return add(project, key, value, resourceFileName, locale, translatable = true, insertBefore)
  }

  override fun addTranslationToFile(
    project: Project,
    xmlFile: XmlFile,
    key: StringResourceKey,
    value: String,
    insertBefore: StringResourceKey?,
  ): Boolean {
    require(insertBefore == null || key.directory == insertBefore.directory) {
      "Can't insert before a key in a different directory."
    }
    return addToFile(project, xmlFile, key, value, translatable = true, insertBefore = insertBefore)
  }

  fun addToFile(
    project: Project,
    xmlFile: XmlFile,
    key: StringResourceKey,
    value: String,
    translatable: Boolean,
    insertBefore: StringResourceKey? = null,
  ): Boolean {
    val resources: XmlTag = xmlFile.rootTag ?: return false
    val resource: XmlTag =
      resources
        .createChildTag(
          SdkConstants.TAG_STRING,
          resources.namespace,
          escapeIfValid(value),
          /* enforceNamespacesDeep= */ false)
        .apply {
          setAttribute(SdkConstants.ATTR_NAME, key.name)
          if (!translatable) setAttribute(SdkConstants.ATTR_TRANSLATABLE, false.toString())
        }
    val beforeTag: XmlTag? = resources.findSubtagForKey(insertBefore)
    WriteCommandAction.writeCommandAction(project).withName("Add string resource ${key.name}").run<Nothing> {
      if (beforeTag == null) {
        resources.addSubTag(resource, /* first= */ false)
      }
      else {
        resources.addBefore(resource, beforeTag)
      }
    }
    return true
  }

  private fun add(
    project: Project,
    key: StringResourceKey,
    value: String,
    resourceFileName: String,
    locale: Locale? = null,
    translatable: Boolean = true,
    insertBefore: StringResourceKey? = null,
  ): Boolean {
    val dir = key.directory ?: return false
    val xmlFile = getStringResourceFile(project, dir, locale, resourceFileName) ?: return false

    return addToFile(project, xmlFile, key, value, translatable, insertBefore)
  }

  /**
   * Returns the subtag of `this` [XmlTag] with a name equal to the [key]'s name, or `null` if none
   * exists.
   */
  private fun XmlTag.findSubtagForKey(key: StringResourceKey?): XmlTag? {
    if (key == null) return null
    return findSubTags(SdkConstants.TAG_STRING, namespace).find {
      it.getAttributeValue(SdkConstants.ATTR_NAME) == key.name
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

  override fun safeDelete(
      project: Project,
      items: Collection<ResourceItem>,
      successCallback: Runnable
  ) {
    // TODO(b/232444069): Long term this probably shouldn't be showing dialogs, etc. But right now
    //  it's too difficult to separate out the confirmation dialog for the first dumb delete.

    val xmlTags = items.mapNotNull { item -> getItemTag(project, item) }
    if (xmlTags.isEmpty()) return

    if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(
        project, xmlTags, /* notifyOnFail= */ true)) {
      return
    }

    // If we don't have usable indices, we can't really do a smart delete, so just do a normal
    // delete with a warning and confirmation dialog.
    if (DumbService.getInstance(project).isDumb) {
      DeleteHandler.deletePsiElement(xmlTags.toTypedArray(), project)
      successCallback.run()
      return
    }

    var safeDeleteSelected = false
    val dialog =
        object : SafeDeleteDialog(project, xmlTags.toTypedArray(), { safeDeleteSelected = true }) {
          init {
            title = RefactoringBundle.message(/* key= */ "delete.title")
          }

          override fun isDelete() = true
        }

    if (!dialog.showAndGet()) return

    if (safeDeleteSelected) {
      SafeDeleteProcessor.createInstance(
              project,
              successCallback,
              xmlTags.toTypedArray(),
              dialog.isSearchInComments,
              dialog.isSearchForTextOccurences,
              /* askForAccessors= */ true)
          .run()
    } else {
      delete(project, items)
      successCallback.run()
    }
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

  /**
   * Returns the escaped version of [xml] unless it is invalid XML, in which case just returns [xml]
   * unmodified.
   */
  private fun escapeIfValid(xml: String) =
      try {
        CharacterDataEscaper.escape(xml)
      } catch (e: IllegalArgumentException) {
        Logger.getInstance(this.javaClass).warn(e)
        xml
      }
}
