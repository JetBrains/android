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

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.android.tools.idea.rendering.LayoutPullParserFactory;
import com.android.tools.idea.res.ResourceNameValidator;
import com.intellij.CommonBundle;
import com.intellij.ide.IdeView;
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
import com.intellij.openapi.fileTypes.StdFileTypes;
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
import org.jetbrains.android.dom.transition.TransitionDomUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.AndroidEditorSettings;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class CreateTypedResourceFileAction extends CreateResourceActionBase {

  protected final ResourceFolderType myResourceType;
  protected final String myResourcePresentableName;
  protected final String myDefaultRootTag;
  private final boolean myValuesResourceFile;
  private boolean myChooseTagName;

  public CreateTypedResourceFileAction(@NotNull String resourcePresentableName,
                                       @NotNull ResourceFolderType resourceFolderType,
                                       boolean valuesResourceFile,
                                       boolean chooseTagName) {
    super(AndroidBundle.message("new.typed.resource.action.title", resourcePresentableName),
          AndroidBundle.message("new.typed.resource.action.description", resourcePresentableName), StdFileTypes.XML.getIcon());
    myResourceType = resourceFolderType;
    myResourcePresentableName = resourcePresentableName;
    myDefaultRootTag = getDefaultRootTagByResourceType(resourceFolderType);
    myValuesResourceFile = valuesResourceFile;
    myChooseTagName = chooseTagName;
  }

  @NotNull
  public ResourceFolderType getResourceFolderType() {
    return myResourceType;
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
      if (pane instanceof AndroidProjectViewPane) {
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
    return doCreateAndNavigate(newName, directory, myDefaultRootTag, myChooseTagName, true);
  }

  PsiElement[] doCreateAndNavigate(String newName, PsiDirectory directory, String rootTagName, boolean chooseTagName, boolean navigate)
    throws Exception {
    final XmlFile file = AndroidResourceUtil
      .createFileResource(newName, directory, rootTagName, myResourceType.getName(), myValuesResourceFile);

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
    if (file.isValid() && LayoutPullParserFactory.isSupported(file)) {
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
    return super.isAvailable(context) && doIsAvailable(context, myResourceType.getName());
  }

  public boolean isChooseTagName() {
    return myChooseTagName;
  }

  @NotNull
  public List<String> getAllowedTagNames(@NotNull AndroidFacet facet) {
    return Collections.singletonList(getDefaultRootTag());
  }

  @NotNull
  public final List<String> getSortedAllowedTagNames(@NotNull AndroidFacet facet) {
    final List<String> result = new ArrayList<String>(getAllowedTagNames(facet));
    Collections.sort(result);
    return result;
  }

  public String getDefaultRootTag() {
    return myDefaultRootTag;
  }

  static boolean doIsAvailable(DataContext context, final String resourceType) {
    final PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(context);
    if (element == null || AndroidFacet.getInstance(element) == null) {
      return false;
    }

    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        PsiElement e = element;
        while (e != null) {
          if (e instanceof PsiDirectory && AndroidResourceUtil.isResourceSubdirectory((PsiDirectory)e, resourceType)) {
            return true;
          }
          e = e.getParent();
        }
        return false;
      }
    });
  }

  @Override
  protected String getErrorTitle() {
    return CommonBundle.getErrorTitle();
  }

  @Override
  protected String getCommandName() {
    return AndroidBundle.message("new.typed.resource.command.name", myResourceType);
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
      default:
    }
    throw new IllegalArgumentException("Incorrect resource folder type");
  }

  private class MyValidator extends MyInputValidator implements InputValidatorEx {
    private final ResourceNameValidator myNameValidator;
    public MyValidator(Project project, PsiDirectory directory) {
      super(project, directory);

      // No validator for value files -- you can call them anything you want (e.g. "public.xml" is a valid
      // name even though public is a Java keyword, etc.)
      myNameValidator = myResourceType == ResourceFolderType.VALUES ? null : ResourceNameValidator.create(true, myResourceType);
    }

    @Override
    public boolean checkInput(String inputString) {
      return getErrorText(inputString) == null;
    }

    @Override
    public String getErrorText(String inputString) {
      return myNameValidator == null ? null : myNameValidator.getErrorText(inputString);
    }
  }
}
