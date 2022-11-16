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
package org.jetbrains.android.refactoring.renaming

import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.idea.res.IdeResourceNameValidator
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.findStyleableAttrFieldsForAttr
import com.android.tools.idea.res.findStyleableAttrFieldsForStyleable
import com.android.tools.idea.res.getResourceElementFromSurroundingValuesTag
import com.android.tools.idea.res.psi.AndroidResourceToPsiResolver
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.android.tools.idea.res.psi.ResourceReferencePsiElement.Companion.RESOURCE_CONTEXT_ELEMENT
import com.android.tools.idea.res.psi.ResourceRepositoryToPsiResolver
import com.android.tools.idea.res.scheduleNewResolutionAndHighlighting
import com.android.tools.idea.util.androidFacet
import com.android.utils.reflection.qualifiedName
import com.intellij.ide.TitledHandler
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.xml.XmlTag
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.BindablePsiReference
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.ui.EditorTextField
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.android.augment.ResourceLightField
import org.jetbrains.android.augment.StyleableAttrFieldUrl
import org.jetbrains.android.augment.StyleableAttrLightField
import org.jetbrains.android.util.AndroidBuildCommonUtils.PNG_EXTENSION
import org.jetbrains.kotlin.idea.KotlinLanguage

/**
 * Custom Rename processor that accepts ResourceReferencePsiElement and renames all corresponding references to that resource.
 */
class ResourceReferenceRenameProcessor : RenamePsiElementProcessor() {

  override fun canProcessElement(element: PsiElement): Boolean {
    return ResourceReferencePsiElement.create(element)?.toWritableResourceReferencePsiElement() != null
  }

  override fun findExistingNameConflicts(element: PsiElement, newName: String, conflicts: MultiMap<PsiElement, String>) {
    if (element !is ResourceReferencePsiElement) {
      return super.findExistingNameConflicts(element, newName, conflicts)
    }
    val contextElement = element.getCopyableUserData(RESOURCE_CONTEXT_ELEMENT)
                         ?: return super.findExistingNameConflicts(element, newName, conflicts)
    val oldResourceReference = element.resourceReference
    val repository = ResourceRepositoryManager.getInstance(contextElement)?.getResourcesForNamespace(oldResourceReference.namespace)
                     ?: return super.findExistingNameConflicts(element, newName, conflicts)
    if (repository.hasResources(oldResourceReference.namespace, oldResourceReference.resourceType, newName)) {
      val newReference = ResourceReference(oldResourceReference.namespace, oldResourceReference.resourceType, newName)
      // Find all of the existing resource declarations for which this new name clashes
      val gotoDeclarationTargets = ResourceRepositoryToPsiResolver.getGotoDeclarationTargets(newReference, contextElement)
      for (target in gotoDeclarationTargets) {
        conflicts.put(target, mutableListOf("Resource ${newReference.resourceUrl} already exists"))
      }
    }
    return super.findExistingNameConflicts(element, newName, conflicts)
  }

  override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
    if (element !is ResourceReferencePsiElement) {
      val resourceReferenceElement = ResourceReferencePsiElement.create(element)?.toWritableResourceReferencePsiElement() ?: return
      resourceReferenceElement.putCopyableUserData(RESOURCE_CONTEXT_ELEMENT, element)
      allRenames.remove(element)
      allRenames[resourceReferenceElement] = newName
    }
  }

  override fun renameElement(element: PsiElement, newName: String, usages: Array<out UsageInfo>, listener: RefactoringElementListener?) {
    for (usage in usages) {
      // BindablePsiReference is a marker interface for elements that should be using bindToElement. It is rarely used and in practice we
      // don't need to rename those elements.
      if (usage.reference !is BindablePsiReference) {
        val language = usage.element?.language
        if (language != null &&
            (language == KotlinLanguage.INSTANCE || language == JavaLanguage.INSTANCE) &&
            element is ResourceReferencePsiElement) {
          // Java and Kotlin Fields can require custom newName strings as do not control their references and so cannot provide custom
          // implementation there.
          renameAndroidLightField(element, usage, newName)
        } else {
          RenameUtil.rename(usage, newName)
        }
      }
    }
  }

  private fun renameAndroidLightField(element: PsiElement, usage: UsageInfo, newName: String) {
    val resolvedValue = usage.reference?.resolve()
    if (resolvedValue is ResourceLightField) {
      RenameUtil.rename(usage, newName)
    }
    else if (resolvedValue is StyleableAttrLightField) {
      val fieldUrl = resolvedValue.styleableAttrFieldUrl
      val resourceReference = (element as? ResourceReferencePsiElement)?.resourceReference ?: return
      when (resourceReference.resourceType) {
        ResourceType.ATTR -> {
          val newAttrName = StyleableAttrFieldUrl(fieldUrl.styleable, ResourceReference(
            fieldUrl.attr.namespace, ResourceType.ATTR, newName)).toFieldName()
          RenameUtil.rename(usage, newAttrName)
        }
        ResourceType.STYLEABLE -> {
          val newStyleableName = StyleableAttrFieldUrl(ResourceReference(
            fieldUrl.styleable.namespace, ResourceType.STYLEABLE, newName), fieldUrl.attr).toFieldName()
          RenameUtil.rename(usage, newStyleableName)
        }
        else -> {
          //Cannot rename a styleable attr field any other ResourceType
          if (LOG.isDebugEnabled) {
            LOG.debug("Trying to rename styleable attr field from incorrect resource type: ${resourceReference.resourceType.displayName}")
          }
        }
      }
    }
    else {
      // Attempt to rename a synthetic element related to the resource
      RenameUtil.rename(usage, newName)
    }
  }

  @WorkerThread
  override fun findReferences(
    element: PsiElement,
    searchScope: SearchScope,
    searchInCommentsAndStrings: Boolean
  ): MutableCollection<PsiReference> {
    val resourceElement = (element as? ResourceReferencePsiElement)
                          ?: return super.findReferences(element, searchScope, searchInCommentsAndStrings)
    val contextElement = resourceElement.getCopyableUserData(RESOURCE_CONTEXT_ELEMENT)
                         ?: return super.findReferences(element, searchScope, searchInCommentsAndStrings)
    val resourceScope = ResourceRepositoryToPsiResolver.getResourceSearchScope(resourceElement.resourceReference, contextElement)
    val found = super.findReferences(element, searchScope.intersectWith(resourceScope), searchInCommentsAndStrings)

    // Add any file based resources not found in references search
    val fileResources =
      AndroidResourceToPsiResolver.getInstance().getGotoDeclarationFileBasedTargets(resourceElement.resourceReference, contextElement)
    found.addAll(fileResources.map { ResourceFileReference(it) })

    // Add styleableAttr fields not found in references search
    val androidFacet = contextElement.androidFacet ?: return found
    when (resourceElement.resourceReference.resourceType) {
      ResourceType.ATTR -> {
        val fields = findStyleableAttrFieldsForAttr(androidFacet, resourceElement.resourceReference.name)
        found.addAll(fields.map { super.findReferences(it, searchScope, searchInCommentsAndStrings) }.flatten())}
      ResourceType.STYLEABLE -> {
        val fields = findStyleableAttrFieldsForStyleable(androidFacet, resourceElement.resourceReference.name)
        found.addAll(fields.map { super.findReferences(it, searchScope, searchInCommentsAndStrings) }.flatten())}
      else -> { /* Fields for other types are found in the references search */}
    }
    return found
  }

  /**
   * PsiReference class to wrap a Resource File, eg. Drawable image, layout file. So that they can be manually appended to the
   * [RenamePsiElementProcessor.findReferences] results. Also provides custom renaming for 9-patch files.
   */
  class ResourceFileReference(val myFile: PsiFile) : PsiReference {
    override fun handleElementRename(newElementName: String): PsiElement {
      if (myFile.isValid) {
        val nameWithoutExtension = FileUtilRt.getNameWithoutExtension(myFile.name)
        val extension = FileUtilRt.getExtension(myFile.name)
        if (nameWithoutExtension.endsWith(".9") && FileUtilRt.extensionEquals(myFile.name, PNG_EXTENSION)) {
          myFile.name = "$newElementName.9.$extension"
        } else {
          myFile.name = "$newElementName.$extension"
        }
      }
      return myFile
    }

    override fun getElement() = myFile
    override fun getRangeInElement(): TextRange = TextRange.EMPTY_RANGE
    override fun resolve() = myFile
    override fun getCanonicalText() = myFile.name
    override fun bindToElement(element: PsiElement): PsiElement = myFile
    override fun isReferenceTo(element: PsiElement): Boolean = false
    override fun isSoft(): Boolean = true
  }

  override fun getPostRenameCallback(element: PsiElement, newName: String, elementListener: RefactoringElementListener): Runnable? {
    val psiManager = (element as? ResourceReferencePsiElement)?.manager ?: return null
    return Runnable {
      scheduleNewResolutionAndHighlighting(psiManager)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(ResourceReferenceRenameProcessor::class.java)
  }
}

/**
 * [DataKey] used by areas of the IDE that want to override the name suggestion field of the resource rename dialog with their own new name
 * suggestion.
 */
val NEW_NAME_RESOURCE: DataKey<String> = DataKey.create(::NEW_NAME_RESOURCE.qualifiedName)

/**
 * [RenameHandler] for Android Resources, in Java, XML, and PsiFiles.
 */
open class ResourceRenameHandler : RenameHandler, TitledHandler {
  override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
    val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return false
    return isAvailableInFile(file) && getWritableResourceReferenceElement(dataContext) != null
  }

  open fun isAvailableInFile(file: PsiFile) : Boolean {
    return file.language != KotlinLanguage.INSTANCE
  }

  private fun getWritableResourceReferenceElement(dataContext: DataContext): ResourceReferencePsiElement? {
    val element = CommonDataKeys.PSI_ELEMENT.getData(dataContext) ?: return null
    val resourceReferenceElement = ResourceReferencePsiElement.create(element)?.toWritableResourceReferencePsiElement()
    if (resourceReferenceElement != null) {
      return resourceReferenceElement
    }
    else {
      // The user has selected an element that does not resolve to a resource, check whether they have selected something nearby and if we
      // can assume the correct resource if any. The allowed matches are:
      // XmlValue of a values resource if it is not a reference to another resource. eg. <color name="foo">#12${caret}3456</color>
      val offset = CommonDataKeys.CARET.getData(dataContext)?.offset ?: return null
      val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return null
      val elementInFile = file.findElementAt(offset) ?: return null
      if (elementInFile.parent is XmlTag) {
        // No longer supporting renaming XmlTags themselves, in this case the caret exists inside a resource tag name eg. <strin<caret>g>
        // http://b/153850296
        return null
      }
      return getResourceElementFromSurroundingValuesTag(elementInFile)?.toWritableResourceReferencePsiElement()
    }
  }

  override fun isRenaming(dataContext: DataContext): Boolean {
    return isAvailableOnDataContext(dataContext)
  }

  @UiThread
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext) {
    val referencePsiElement = getWritableResourceReferenceElement(dataContext) ?: return
    if (file == null) {
      return
    }
    referencePsiElement.putCopyableUserData(RESOURCE_CONTEXT_ELEMENT, file)
    val newName = NEW_NAME_RESOURCE.getData(dataContext)
    ResourceRenameDialog(project, referencePsiElement, null, editor, newName).show(dataContext)
  }

  @UiThread
  override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext) {
    val editor = CommonDataKeys.EDITOR.getData(dataContext)
    val file = CommonDataKeys.PSI_FILE.getData(dataContext)
    invoke(project, editor, file, dataContext)
  }

  override fun getActionTitle(): String {
    return "Rename Android Resource"
  }

  /**
   * Custom [RenameDialog] for renaming Android resources.
   */
  private class ResourceRenameDialog(
    project: Project,
    resourceReferenceElement: ResourceReferencePsiElement,
    nameSuggestionContext: PsiElement?,
    editor: Editor?,
    providedName: String?
  ) : RenameDialog(project, resourceReferenceElement, nameSuggestionContext, editor) {

    private var nameValidator: InputValidatorEx? = null

    init {
      if (providedName != null) {
        (nameSuggestionsField.focusableComponent as? EditorTextField)?.text = providedName
      }
    }

    fun show(dataContext: DataContext) {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        val newTestingName = NEW_NAME_RESOURCE.getData(dataContext) ?: PsiElementRenameHandler.DEFAULT_NAME.getData(dataContext) ?: return
        performRename(newTestingName)
        close(DialogWrapper.OK_EXIT_CODE)
      }
      else {
        super.show()
      }
    }

    // This method is called by the superclass constructor before this object is fully constructed.
    override fun canRun() {
      val name = newName
      val errorText = getNameValidator().getErrorText(name)
      if (errorText != null) {
        throw ConfigurationException(errorText)
      }
    }

    private fun getNameValidator(): InputValidatorEx {
      var validator = nameValidator
      if (validator == null) {
        val resourceReference = (psiElement as ResourceReferencePsiElement).resourceReference
        validator = if (isFileBased(resourceReference, psiElement)) {
          // Guaranteed to be not null for a file based resource type.
          val resourceFolderType = FolderTypeRelationship.getNonValuesRelatedFolder(resourceReference.resourceType)!!
          IdeResourceNameValidator.forFilename(resourceFolderType)
        }
        else {
          IdeResourceNameValidator.forResourceName(resourceReference.resourceType)
        }
        nameValidator = validator
      }
      return validator
    }
  }
}

/**
 * [RenameHandler] for Android Resources, in Kotlin.
 */
class KotlinResourceRenameHandler : ResourceRenameHandler() {
  override fun isAvailableInFile(file: PsiFile) : Boolean {
    return file.language == KotlinLanguage.INSTANCE
  }
}

/**
 * Checks if this a resource that is defined in a file named by the resource plus the extension.
 *
 * Some resource types (color and drawable) can be defined **both** as a separate XML file as well as defined within a value XML file along
 * with other properties. In those cases, we have to find the declaration of the resource to determine whether it's defined in its own file
 * or not.
 *
 * @return true if the given resource is stored in a file named by the resource
 */
internal fun isFileBased(resourceReference: ResourceReference, context: PsiElement): Boolean {
  return when (val resourceType = resourceReference.resourceType) {
    ResourceType.ID ->
      // The folder types for ID is not only VALUES but also LAYOUT and MENU. However, unlike resources, they are only defined inline there
      // so for the purposes of isFileBased (where the intent is to figure out files that are uniquely identified by a resource's name) this
      // method should return false anyway.
      false

    ResourceType.COLOR, ResourceType.DRAWABLE ->
      // These resources can be either file-based or not, and we have to resolve the reference to figure it out.
      AndroidResourceToPsiResolver.getInstance().getGotoDeclarationFileBasedTargets(resourceReference, context).any()

    else ->
      FolderTypeRelationship.getRelatedFolders(resourceType).any { it != ResourceFolderType.VALUES }
  }
}
