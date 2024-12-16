/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.actions

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.resources.ResourceFolderType
import com.android.tools.idea.rendering.parsers.PsiXmlFile
import com.android.tools.idea.res.IdeResourceNameValidator
import com.android.tools.idea.res.IdeResourceNameValidator.Companion.forFilename
import com.android.tools.idea.res.createXmlFileResource
import com.android.tools.idea.res.isResourceSubdirectory
import com.android.tools.idea.util.dependsOn
import com.android.tools.rendering.parsers.LayoutPullParsers
import com.intellij.CommonBundle
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.parents
import com.intellij.psi.xml.XmlFile
import com.intellij.util.PsiNavigateUtil
import com.intellij.util.application
import com.intellij.xml.refactoring.XmlTagInplaceRenamer
import org.jetbrains.android.dom.font.FontFamilyDomFileDescription
import org.jetbrains.android.dom.navigation.NavigationDomFileDescription
import org.jetbrains.android.dom.transition.TransitionDomUtil
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.android.util.AndroidUtils

open class CreateTypedResourceFileAction(
  @JvmField protected val resourcePresentableName: String,
  val resourceFolderType: ResourceFolderType,
  private val valuesResourceFile: Boolean,
  val isChooseTagName: Boolean,
) :
  CreateResourceActionBase(
    AndroidBundle.message("new.typed.resource.action.title", resourcePresentableName),
    AndroidBundle.message("new.typed.resource.action.description", resourcePresentableName),
    XmlFileType.INSTANCE.icon,
  ) {
  protected fun createValidator(project: Project, directory: PsiDirectory): InputValidator {
    return MyValidator(project, directory)
  }

  override fun invokeDialog(project: Project, dataContext: DataContext): Array<PsiElement> {
    val view = LangDataKeys.IDE_VIEW.getData(dataContext) ?: return PsiElement.EMPTY_ARRAY
    // If you're in the Android View, we want to ask you not just the filename but also let you
    // create other resource folder configurations
    if (ProjectView.getInstance(project).currentProjectViewPane.id == com.android.tools.idea.navigator.ID) {
      return CreateResourceFileAction.getInstance().invokeDialog(project, dataContext)
    }

    val directory = view.getOrChooseDirectory() ?: return PsiElement.EMPTY_ARRAY

    val validator = createValidator(project, directory)
    Messages.showInputDialog(
      project,
      AndroidBundle.message("new.file.dialog.text"),
      AndroidBundle.message("new.typed.resource.dialog.title", resourcePresentableName),
      Messages.getQuestionIcon(),
      "",
      validator,
    )
    return PsiElement.EMPTY_ARRAY
  }

  override fun create(newName: String, directory: PsiDirectory): Array<PsiElement> {
    val module = ModuleUtilCore.findModuleForPsiElement(directory)
    return doCreateAndNavigate(
      newName,
      directory,
      getDefaultRootTag(module),
      this.isChooseTagName,
      true,
    )
  }

  fun doCreateAndNavigate(
    newName: String,
    directory: PsiDirectory,
    rootTagName: String,
    chooseTagName: Boolean,
    navigate: Boolean,
  ): Array<PsiElement> {
    val project = directory.project
    if (resourceFolderType == ResourceFolderType.RAW) {
      val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(newName)
      val psiFile =
        WriteCommandAction.writeCommandAction(project).compute<PsiFile, RuntimeException> {
          directory.checkCreateFile(newName)
          val file = PsiFileFactory.getInstance(project).createFileFromText(newName, fileType, "")
          directory.add(file) as PsiFile
        }
      if (navigate) doNavigate(psiFile)
      return arrayOf(psiFile)
    }

    val xmlFile =
      createXmlFileResource(
        newName,
        directory,
        rootTagName,
        resourceFolderType.resourceType,
        valuesResourceFile,
      )

    if (navigate) doNavigate(xmlFile)
    if (chooseTagName) doChooseTagName(xmlFile)

    return arrayOf(xmlFile)
  }

  private fun doChooseTagName(xmlFile: XmlFile) {
    val rootTag = xmlFile.document?.rootTag ?: return
    val editor = FileEditorManager.getInstance(xmlFile.project).selectedTextEditor ?: return
    editor.caretModel.moveToOffset(rootTag.textOffset + 1)
    XmlTagInplaceRenamer.rename(editor, rootTag)
  }

  protected fun doNavigate(file: PsiFile) {
    if (file is XmlFile && file.isValid && LayoutPullParsers.isSupported(PsiXmlFile(file))) {
      val virtualFile = file.virtualFile.takeIf { it.isValid } ?: return
      OpenFileDescriptor(file.project, virtualFile).navigate(true)
    } else {
      PsiNavigateUtil.navigate(file)
    }
  }

  override fun isAvailable(context: DataContext) =
    super.isAvailable(context) && doIsAvailable(context, resourceFolderType.name)

  open fun getAllowedTagNames(facet: AndroidFacet) = listOf(getDefaultRootTag(facet.module))

  fun getSortedAllowedTagNames(facet: AndroidFacet) = getAllowedTagNames(facet).sorted()

  fun getDefaultRootTag(module: Module?) =
    if (resourceFolderType == ResourceFolderType.RAW) ""
    else getDefaultRootTagByResourceType(module, resourceFolderType)

  override fun getErrorTitle(): String = CommonBundle.getErrorTitle()

  override fun getCommandName(): String? =
    AndroidBundle.message("new.typed.resource.command.name", resourceFolderType)

  override fun getActionName(directory: PsiDirectory, newName: String): String =
    CreateResourceFileAction.doGetActionName(directory, newName)

  override fun toString() = resourcePresentableName

  private inner class MyValidator(project: Project, directory: PsiDirectory) :
    MyInputValidator(project, directory), InputValidatorEx {
    private val nameValidator: IdeResourceNameValidator =
      forFilename(resourceFolderType, SdkConstants.DOT_XML)

    override fun getErrorText(inputString: String?) = nameValidator.getErrorText(inputString)

    override fun checkInput(inputString: String?) = super<MyInputValidator>.checkInput(inputString)

    override fun canClose(inputString: String?) = super<MyInputValidator>.canClose(inputString)
  }

  companion object {
    @JvmStatic
    fun doIsAvailable(context: DataContext, resourceType: String?): Boolean {
      // Requires a given PsiElement.
      val element =
        CommonDataKeys.PSI_ELEMENT.getData(context)?.takeIf { AndroidFacet.getInstance(it) != null }
          ?: return false

      return application.runReadAction<Boolean> {
        // Verify the given PsiElement is a directory within a valid resource type folder (e.g:
        // .../res/color).
        element.parents(withSelf = true).any { it is PsiDirectory && isResourceSubdirectory(it, resourceType, true) }
      }
    }

    @JvmStatic
    fun getDefaultRootTagByResourceType(module: Module?, resourceType: ResourceFolderType) =
      when (resourceType) {
        ResourceFolderType.XML -> {
          if (module?.dependsOn(GoogleMavenArtifactId.ANDROIDX_PREFERENCE) == true) {
            AndroidXConstants.PreferenceAndroidX.CLASS_PREFERENCE_SCREEN_ANDROIDX.newName()
          } else {
            "PreferenceScreen"
          }
        }
        ResourceFolderType.DRAWABLE,
        ResourceFolderType.COLOR -> "selector"
        ResourceFolderType.VALUES -> "resources"
        ResourceFolderType.MENU -> "menu"
        ResourceFolderType.ANIM,
        ResourceFolderType.ANIMATOR -> "set"
        ResourceFolderType.LAYOUT ->
          when {
            module == null -> AndroidUtils.TAG_LINEAR_LAYOUT
            module.dependsOn(GoogleMavenArtifactId.CONSTRAINT_LAYOUT) ->
              AndroidXConstants.CONSTRAINT_LAYOUT.oldName()
            module.dependsOn(GoogleMavenArtifactId.ANDROIDX_CONSTRAINT_LAYOUT) ->
              AndroidXConstants.CONSTRAINT_LAYOUT.newName()
            else -> AndroidUtils.TAG_LINEAR_LAYOUT
          }
        ResourceFolderType.TRANSITION -> TransitionDomUtil.DEFAULT_ROOT
        ResourceFolderType.FONT -> FontFamilyDomFileDescription.TAG_NAME
        ResourceFolderType.NAVIGATION -> NavigationDomFileDescription.DEFAULT_ROOT_TAG
        else -> throw IllegalArgumentException("Incorrect resource folder type")
      }
  }
}
