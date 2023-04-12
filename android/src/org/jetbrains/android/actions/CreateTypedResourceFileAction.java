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

package org.jetbrains.android.actions;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.navigator.AndroidProjectView;
import com.android.tools.idea.rendering.parsers.LayoutPullParsers;
import com.android.tools.idea.rendering.parsers.PsiXmlFile;
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.intellij.CommonBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.xml.refactoring.XmlTagInplaceRenamer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.android.dom.font.FontFamilyDomFileDescription;
import org.jetbrains.android.dom.navigation.NavigationDomFileDescription;
import org.jetbrains.android.dom.transition.TransitionDomUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.AndroidEditorSettings;
import org.jetbrains.android.util.AndroidBundle;
import com.android.tools.idea.res.IdeResourcesUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CreateTypedResourceFileAction extends CreateResourceActionBase {

  protected final ResourceFolderType myResourceFolderType;
  protected final String myResourcePresentableName;
  String myDefaultRootTag;
  private final boolean myValuesResourceFile;
  private boolean myChooseTagName;

  public CreateTypedResourceFileAction(@NotNull String resourcePresentableName,
                                       @NotNull ResourceFolderType resourceFolderType,
                                       boolean valuesResourceFile,
                                       boolean chooseTagName) {
    super(AndroidBundle.message("new.typed.resource.action.title", resourcePresentableName),
          AndroidBundle.message("new.typed.resource.action.description", resourcePresentableName), XmlFileType.INSTANCE.getIcon());
    myResourceFolderType = resourceFolderType;
    myResourcePresentableName = resourcePresentableName;
    myDefaultRootTag = getDefaultRootTagByResourceType(resourceFolderType);
    myValuesResourceFile = valuesResourceFile;
    myChooseTagName = chooseTagName;
  }

  @NotNull
  public ResourceFolderType getResourceFolderType() {
    return myResourceFolderType;
  }

  protected InputValidator createValidator(Project project, PsiDirectory directory) {
    return new MyValidator(project, directory);
  }

  @NotNull
  @Override
  protected PsiElement[] invokeDialog(@NotNull Project project, @NotNull DataContext dataContext) {
    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view != null) {
      // If you're in the Android View, we want to ask you not just the filename but also let you
      // create other resource folder configurations
      AbstractProjectViewPane pane = ProjectView.getInstance(project).getCurrentProjectViewPane();
      if (pane.getId().equals(AndroidProjectView.ID)) {
        return CreateResourceFileAction.getInstance().invokeDialog(project, dataContext);
      }

      final PsiDirectory directory = view.getOrChooseDirectory();
      if (directory != null) {
        InputValidator validator = createValidator(project, directory);
        Messages.showInputDialog(project, AndroidBundle.message("new.file.dialog.text"),
                                 AndroidBundle.message("new.typed.resource.dialog.title", myResourcePresentableName),
                                 Messages.getQuestionIcon(), "", validator);
      }
    }
    return PsiElement.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    Module module = ModuleUtilCore.findModuleForPsiElement(directory);
    return doCreateAndNavigate(newName, directory, getDefaultRootTag(module), myChooseTagName, true);
  }

  PsiElement[] doCreateAndNavigate(String newName, PsiDirectory directory, String rootTagName, boolean chooseTagName, boolean navigate)
    throws Exception {
    final XmlFile file = IdeResourcesUtil
      .createFileResource(newName, directory, rootTagName, myResourceFolderType.getName(), myValuesResourceFile);

    if (navigate) {
      doNavigate(file);
    }
    if (chooseTagName) {
      XmlDocument document = file.getDocument();
      if (document != null) {
        XmlTag rootTag = document.getRootTag();
        if (rootTag != null) {
          final Project project = file.getProject();
          final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
          if (editor != null) {
            CaretModel caretModel = editor.getCaretModel();
            caretModel.moveToOffset(rootTag.getTextOffset() + 1);
            XmlTagInplaceRenamer.rename(editor, rootTag);
          }
        }
      }
    }
    return new PsiElement[]{file};
  }

  protected void doNavigate(XmlFile file) {
    if (file.isValid() && LayoutPullParsers.isSupported(new PsiXmlFile(file))) {
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null && virtualFile.isValid()) {
        if (AndroidEditorSettings.getInstance().getGlobalState().isPreferXmlEditor()) {
          new OpenFileDescriptor(file.getProject(), virtualFile, 0).navigate(true);
        } else {
          new OpenFileDescriptor(file.getProject(), virtualFile).navigate(true);
        }
      }
    } else {
      PsiNavigateUtil.navigate(file);
    }
  }

  @Override
  protected boolean isAvailable(DataContext context) {
    return super.isAvailable(context) && doIsAvailable(context, myResourceFolderType.getName());
  }

  public boolean isChooseTagName() {
    return myChooseTagName;
  }

  @NotNull
  public List<String> getAllowedTagNames(@NotNull AndroidFacet facet) {
    return Collections.singletonList(getDefaultRootTag(facet.getModule()));
  }

  @NotNull
  public final List<String> getSortedAllowedTagNames(@NotNull AndroidFacet facet) {
    final List<String> result = new ArrayList<>(getAllowedTagNames(facet));
    Collections.sort(result);
    return result;
  }

  public String getDefaultRootTag(@Nullable Module module) {
    return myDefaultRootTag;
  }

  static boolean doIsAvailable(DataContext context, final String resourceType) {
    final PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(context);
    if (element == null || AndroidFacet.getInstance(element) == null) {
      // Requires a given PsiElement.
      return false;
    }

    return ApplicationManager.getApplication().runReadAction((Computable<Boolean>)() -> {
      PsiElement e = element;
      while (e != null) {
        if (e instanceof PsiDirectory && IdeResourcesUtil.isResourceSubdirectory((PsiDirectory)e, resourceType, true)) {
          // Verify the given PsiElement is a directory within a valid resource type folder (e.g: .../res/color).
          return true;
        }
        e = e.getParent();
      }
      return false;
    });
  }

  @Override
  protected String getErrorTitle() {
    return CommonBundle.getErrorTitle();
  }

  @Override
  protected String getCommandName() {
    return AndroidBundle.message("new.typed.resource.command.name", myResourceFolderType);
  }

  @Nullable
  @Override
  protected String getActionName(PsiDirectory directory, String newName) {
    return CreateResourceFileAction.doGetActionName(directory, newName);
  }

  @Override
  public String toString() {
    return myResourcePresentableName;
  }

  @NotNull
  public static String getDefaultRootTagByResourceType(@NotNull ResourceFolderType resourceType) {
    switch (resourceType) {
      case XML:
        return "PreferenceScreen";
      case DRAWABLE:
        return "selector";
      case COLOR:
        return "selector";
      case VALUES:
        return "resources";
      case MENU:
        return "menu";
      case ANIM:
        return "set";
      case ANIMATOR:
        return "set";
      case LAYOUT:
        return AndroidUtils.TAG_LINEAR_LAYOUT;
      case TRANSITION:
        return TransitionDomUtil.DEFAULT_ROOT;
      case FONT:
        return FontFamilyDomFileDescription.TAG_NAME;
      case NAVIGATION:
        return NavigationDomFileDescription.DEFAULT_ROOT_TAG;
      default:
    }
    throw new IllegalArgumentException("Incorrect resource folder type");
  }

  private class MyValidator extends MyInputValidator implements InputValidatorEx {
    @NotNull
    private final IdeResourceNameValidator myNameValidator;

    public MyValidator(Project project, PsiDirectory directory) {
      super(project, directory);
      myNameValidator = IdeResourceNameValidator.forFilename(myResourceFolderType, SdkConstants.DOT_XML);
    }

    @Override
    public String getErrorText(String inputString) {
      return myNameValidator.getErrorText(inputString);
    }
  }
}
