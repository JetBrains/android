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
import com.android.tools.idea.res.createFileResource
import com.android.tools.idea.res.isResourceSubdirectory
import com.android.tools.idea.util.dependsOn
import com.android.tools.rendering.parsers.LayoutPullParsers
import com.intellij.CommonBundle
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.xml.XmlFile
import com.intellij.util.PsiNavigateUtil
import com.intellij.xml.refactoring.XmlTagInplaceRenamer
import org.jetbrains.android.dom.font.FontFamilyDomFileDescription
import org.jetbrains.android.dom.navigation.NavigationDomFileDescription
import org.jetbrains.android.dom.transition.TransitionDomUtil
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.android.util.AndroidUtils
import java.util.Collections

open class CreateTypedResourceFileAction(
  @JvmField protected val myResourcePresentableName: String,
  val resourceFolderType: ResourceFolderType,
  private val myValuesResourceFile: Boolean,
  val isChooseTagName: Boolean
) : CreateResourceActionBase(
  AndroidBundle.message(
    "new.typed.resource.action.title",
    myResourcePresentableName
  ),
  AndroidBundle.message(
    "new.typed.resource.action.description",
    myResourcePresentableName
  ), XmlFileType.INSTANCE.getIcon()
) {
  protected fun createValidator(project: Project?, directory: PsiDirectory?): InputValidator {
    return CreateTypedResourceFileAction.MyValidator(project, directory)
  }

  override fun invokeDialog(project: Project, dataContext: DataContext): Array<PsiElement?>? {
    val view = LangDataKeys.IDE_VIEW.getData(dataContext)
    if (view != null) {
      // If you're in the Android View, we want to ask you not just the filename but also let you
      // create other resource folder configurations
      val pane = ProjectView.getInstance(project).getCurrentProjectViewPane()
      if (pane.getId() == ID) {
        return CreateResourceFileAction.getInstance().invokeDialog(project, dataContext)
      }

      val directory = view.getOrChooseDirectory()
      if (directory != null) {
        val validator = createValidator(project, directory)
        Messages.showInputDialog(
          project, AndroidBundle.message("new.file.dialog.text"),
          AndroidBundle.message("new.typed.resource.dialog.title", myResourcePresentableName),
          Messages.getQuestionIcon(), "", validator
        )
      }
    }
    return PsiElement.EMPTY_ARRAY
  }

  @Throws(Exception::class)
  public override fun create(newName: String, directory: PsiDirectory): Array<PsiElement?> {
    val module = ModuleUtilCore.findModuleForPsiElement(directory)
    return doCreateAndNavigate(newName, directory, getDefaultRootTag(module), this.isChooseTagName, true)
  }

  @Throws(Exception::class)
  fun doCreateAndNavigate(
    newName: String,
    directory: PsiDirectory,
    rootTagName: String,
    chooseTagName: Boolean,
    navigate: Boolean
  ): Array<PsiElement?> {
    val project = directory.getProject()
    if (this.resourceFolderType == ResourceFolderType.RAW) {
      val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(newName)
      val psiFile = WriteCommandAction.writeCommandAction(project)
        .compute<PsiFile?, RuntimeException?>(ThrowableComputable {
          directory.checkCreateFile(newName)
          val file = PsiFileFactory.getInstance(project).createFileFromText(newName, fileType, "")
          directory.add(file) as PsiFile?
        })
      if (navigate) {
        doNavigate(psiFile)
      }
      return arrayOf<PsiElement?>(psiFile)
    }

    val xmlFile = createFileResource(newName, directory, rootTagName, resourceFolderType.getName(), myValuesResourceFile)

    if (navigate) {
      doNavigate(xmlFile)
    }
    if (chooseTagName) {
      val document = xmlFile.getDocument()
      if (document != null) {
        val rootTag = document.getRootTag()
        if (rootTag != null) {
          val editor = FileEditorManager.getInstance(project).getSelectedTextEditor()
          if (editor != null) {
            val caretModel = editor.getCaretModel()
            caretModel.moveToOffset(rootTag.getTextOffset() + 1)
            XmlTagInplaceRenamer.rename(editor, rootTag)
          }
        }
      }
    }
    return arrayOf<PsiElement>(xmlFile)
  }

  protected fun doNavigate(psiFile: PsiFile?) {
    if (psiFile is XmlFile && psiFile.isValid() && LayoutPullParsers.isSupported(PsiXmlFile(psiFile))) {
      val virtualFile = psiFile.getVirtualFile()
      if (virtualFile != null && virtualFile.isValid()) {
        OpenFileDescriptor(psiFile.getProject(), virtualFile).navigate(true)
      }
    } else {
      PsiNavigateUtil.navigate(psiFile)
    }
  }

  override fun isAvailable(context: DataContext): Boolean {
    return super.isAvailable(context) && doIsAvailable(context, resourceFolderType.getName())
  }

  open fun getAllowedTagNames(facet: AndroidFacet): MutableList<String?> {
    return mutableListOf<String?>(getDefaultRootTag(facet.getModule()))
  }

  fun getSortedAllowedTagNames(facet: AndroidFacet): MutableList<String?> {
    val result: MutableList<String?> = ArrayList<String?>(getAllowedTagNames(facet))
    Collections.sort<String?>(result)
    return result
  }

  fun getDefaultRootTag(module: Module?): String {
    return if (this.resourceFolderType == ResourceFolderType.RAW)
      ""
    else
      getDefaultRootTagByResourceType(module, this.resourceFolderType)
  }

  override fun getErrorTitle(): String? {
    return CommonBundle.getErrorTitle()
  }

  override fun getCommandName(): String {
    return AndroidBundle.message("new.typed.resource.command.name", this.resourceFolderType)
  }

  override fun getActionName(directory: PsiDirectory?, newName: String?): String? {
    return CreateResourceFileAction.doGetActionName(directory, newName)
  }

  override fun toString(): String {
    return myResourcePresentableName
  }

  private inner class MyValidator(project: Project?, directory: PsiDirectory?) : MyInputValidator(project, directory), InputValidatorEx {
    private val myNameValidator: IdeResourceNameValidator

    init {
      myNameValidator = forFilename(this.resourceFolderType, SdkConstants.DOT_XML)
    }

    override fun getErrorText(inputString: String?): String? {
      return myNameValidator.getErrorText(inputString)
    }
  }

  companion object {
    @JvmStatic
    fun doIsAvailable(context: DataContext, resourceType: String?): Boolean {
      val element = CommonDataKeys.PSI_ELEMENT.getData(context)
      if (element == null || AndroidFacet.getInstance(element) == null) {
        // Requires a given PsiElement.
        return false
      }

      return ApplicationManager.getApplication().runReadAction<Boolean?>(Computable {
        var e: PsiElement? = element
        while (e != null) {
          if (e is PsiDirectory && isResourceSubdirectory(e, resourceType, true)) {
            // Verify the given PsiElement is a directory within a valid resource type folder (e.g: .../res/color).
            return@Computable true
          }
          e = e.getParent()
        }
        false
      })
    }

    @JvmStatic
    fun getDefaultRootTagByResourceType(module: Module?, resourceType: ResourceFolderType): String {
      when (resourceType) {
        ResourceFolderType.XML -> {
          if (module != null) {
            if (module.dependsOn(GoogleMavenArtifactId.ANDROIDX_PREFERENCE)) {
              return AndroidXConstants.PreferenceAndroidX.CLASS_PREFERENCE_SCREEN_ANDROIDX.newName()
            }
          }
          return "PreferenceScreen"
        }

        ResourceFolderType.DRAWABLE, ResourceFolderType.COLOR -> {
          return "selector"
        }

        ResourceFolderType.VALUES -> {
          return "resources"
        }

        ResourceFolderType.MENU -> {
          return "menu"
        }

        ResourceFolderType.ANIM, ResourceFolderType.ANIMATOR -> {
          return "set"
        }

        ResourceFolderType.LAYOUT -> {
          if (module != null) {
            if (module.dependsOn(GoogleMavenArtifactId.CONSTRAINT_LAYOUT)) {
              return AndroidXConstants.CONSTRAINT_LAYOUT.oldName()
            } else if (module.dependsOn(GoogleMavenArtifactId.ANDROIDX_CONSTRAINT_LAYOUT)) {
              return AndroidXConstants.CONSTRAINT_LAYOUT.newName()
            }
          }
          return AndroidUtils.TAG_LINEAR_LAYOUT
        }

        ResourceFolderType.TRANSITION -> {
          return TransitionDomUtil.DEFAULT_ROOT
        }

        ResourceFolderType.FONT -> {
          return FontFamilyDomFileDescription.TAG_NAME
        }

        ResourceFolderType.NAVIGATION -> {
          return NavigationDomFileDescription.DEFAULT_ROOT_TAG
        }

        else -> throw IllegalArgumentException("Incorrect resource folder type")
      }
    }
  }
}
