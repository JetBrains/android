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


import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.google.common.collect.Maps;
import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class CreateResourceFileAction extends CreateResourceActionBase {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.actions.CreateResourceFileAction");

  private final Map<ResourceFolderType, CreateTypedResourceFileAction> mySubactions = Maps.newEnumMap(ResourceFolderType.class);
  private String myRootElement;
  private boolean myNavigate;

  @NotNull
  public static CreateResourceFileAction getInstance() {
    AnAction action = ActionManager.getInstance().getAction("Android.CreateResourcesActionGroup");
    assert action instanceof CreateResourceFileActionGroup;
    return ((CreateResourceFileActionGroup)action).getCreateResourceFileAction();
  }

  public CreateResourceFileAction() {
    super(AndroidBundle.message("new.resource.action.title"), AndroidBundle.message("new.resource.action.description"),
          StdFileTypes.XML.getIcon());
  }

  public void add(CreateTypedResourceFileAction action) {
    mySubactions.put(action.getResourceFolderType(), action);
  }

  public Collection<CreateTypedResourceFileAction> getSubactions() {
    return mySubactions.values();
  }

  @Override
  protected boolean isAvailable(DataContext context) {
    if (!super.isAvailable(context)) return false;
    return isOutsideResourceTypeFolder(context);
  }

  /** Returns true if the context points to an Android module, but outside of a specific resource type folder */
  static boolean isOutsideResourceTypeFolder(@NotNull DataContext context) {
    // Avoid listing these actions in folders where we have a more specific action (e.g. "Add Layout File")
    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(context);
    if (file != null) {
      if (!file.isDirectory()) {
        file = file.getParent();
      }
      if (file != null && ResourceFolderType.getFolderType(file.getName()) != null) {
        // TODO: Also check that it's really a resource folder!
        return false;
      }
    }

    // Offer creating resource files from anywhere in the project (as is done for Java Classes) as long as it's within an Android module
    Module module = LangDataKeys.MODULE.getData(context);
    if (module != null) {
      return AndroidFacet.getInstance(module) != null;
    }

    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(context);
    return element != null && AndroidFacet.getInstance(element) != null;
  }

  @Nullable
  public static XmlFile createFileResource(@NotNull AndroidFacet facet,
                                           @NotNull final ResourceFolderType resType,
                                           @Nullable String resName,
                                           @Nullable String rootElement,
                                           @Nullable FolderConfiguration config,
                                           boolean chooseResName,
                                           @Nullable String dialogTitle,
                                           boolean navigate) {
    final PsiElement[] elements = doCreateFileResource(facet, resType, resName, rootElement,
                                                       config, chooseResName, dialogTitle, navigate);
    if (elements.length == 0) {
      return null;
    }
    assert elements.length == 1 && elements[0] instanceof XmlFile;
    return (XmlFile)elements[0];
  }

  @Nullable
  public static XmlFile createFileResource(@NotNull AndroidFacet facet,
                                           @NotNull final ResourceFolderType folderType,
                                           @Nullable String resName,
                                           @Nullable String rootElement,
                                           @Nullable FolderConfiguration config,
                                           boolean chooseResName,
                                           @Nullable String dialogTitle) {
    return createFileResource(facet, folderType, resName, rootElement, config, chooseResName, dialogTitle, true);
  }

  @NotNull
  private static PsiElement[] doCreateFileResource(@NotNull AndroidFacet facet,
                                                   @NotNull final ResourceFolderType resType,
                                                   @Nullable String resName,
                                                   @Nullable String rootElement,
                                                   @Nullable FolderConfiguration config,
                                                   boolean chooseResName,
                                                   @Nullable String dialogTitle,
                                                   final boolean navigate) {
    final CreateResourceFileAction action = getInstance();
    final Project project = facet.getModule().getProject();
    action.myNavigate = navigate;

    CreateResourceFileDialogBase.ValidatorFactory validatorFactory = action.createValidatorFactory(project);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      String subdirName = resType.getName();
      VirtualFile resDir = facet.getPrimaryResourceDir();
      if (resDir == null) {
        return PsiElement.EMPTY_ARRAY;
      }
      PsiDirectory resourceDir = PsiManager.getInstance(project).findDirectory(resDir);
      if (resourceDir == null) {
        return PsiElement.EMPTY_ARRAY;
      }
      InputValidator inputValidator = validatorFactory.create(resourceDir, subdirName, null);
      // Simulate the dialog OK action by inlining the checkInput and canClose, before getCreatedElements.
      return (inputValidator.checkInput(resName) && inputValidator.canClose(resName)) ?
             ((MyInputValidator)inputValidator).getCreatedElements() :
             PsiElement.EMPTY_ARRAY;
    }
    NewResourceCreationHandler newResourceHandler = NewResourceCreationHandler.getInstance(project);
    final CreateResourceFileDialogBase dialog = newResourceHandler.createNewResourceFileDialog(
      facet, action.mySubactions.values(), resType, resName, rootElement,
      config, chooseResName, true, null, validatorFactory);
    if (dialogTitle != null) {
      dialog.setTitle(dialogTitle);
    }
    if (!dialog.showAndGet()) {
      return PsiElement.EMPTY_ARRAY;
    }
    return dialog.getCreatedElements();
  }

  @NotNull
  @Override
  public PsiElement[] invokeDialog(@NotNull final Project project, @NotNull final DataContext dataContext) {
    Module module = LangDataKeys.MODULE.getData(dataContext);
    if (module == null) {
      return PsiElement.EMPTY_ARRAY;
    }
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    LOG.assertTrue(facet != null);

    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    ResourceFolderType folderType = getUniqueFolderType(files);
    FolderConfiguration config = null;
    if (files != null && files.length > 0) {
      config = files.length == 1 ? FolderConfiguration.getConfigForFolder(files[0].getName()) : null;
    }
    myNavigate = true;
    NewResourceCreationHandler newResourceHandler = NewResourceCreationHandler.getInstance(project);
    final CreateResourceFileDialogBase dialog = newResourceHandler.createNewResourceFileDialog(
      facet, mySubactions.values(), folderType, null, null, config, true,
      false, findResourceDirectory(dataContext), createValidatorFactory(project));
    if (!dialog.showAndGet()) {
      return PsiElement.EMPTY_ARRAY;
    }
    return dialog.getCreatedElements();
  }

  @Nullable
  static ResourceFolderType getUniqueFolderType(@Nullable VirtualFile[] files) {
    ResourceFolderType folderType = null;
    if (files != null && files.length > 0) {
      for (VirtualFile file : files) {
        if (!file.isDirectory()) {
          file = file.getParent();
        }
        if (file != null) {
          ResourceFolderType type = ResourceFolderType.getFolderType(file.getName());
          if (type != null) {
            // Ensure that if there are multiple files, they all have the same type
            if (type != folderType && folderType != null) {
              folderType = null;
              break;
            }
            else {
              folderType = type;
            }
          }
        }
      }
    }
    return folderType;
  }

  // Or we could pass the action down, the dialog will set the root element, and then it can choose how to
  // create the validator on its own. On the other hand this is symmetric with the other dialogs.
  @NotNull
  private CreateResourceFileDialogBase.ValidatorFactory createValidatorFactory(@NotNull final Project project) {
    return new CreateResourceFileDialogBase.ValidatorFactory() {
      @Override
      @NotNull
      public ElementCreatingValidator create(@NotNull final PsiDirectory resourceDirectory, @NotNull final String subdirName,
                                             @Nullable String rootElement) {
        PsiDirectory resSubdir = resourceDirectory.findSubdirectory(subdirName);
        if (resSubdir == null) {
          resSubdir = ApplicationManager.getApplication().runWriteAction(
            (Computable<PsiDirectory>)() -> resourceDirectory.createSubdirectory(subdirName));
        }
        // Stash the chosen rootElement before action is asked to create() the elements.
        myRootElement = rootElement;
        return new MyInputValidator(project, resSubdir);
      }
    };
  }

  @NotNull
  @Override
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    CreateTypedResourceFileAction action = getActionByDir(directory);
    if (action == null) {
      throw new IllegalArgumentException("Incorrect directory");
    }
    if (myRootElement != null && myRootElement.length() > 0) {
      return action.doCreateAndNavigate(newName, directory, myRootElement, false, myNavigate);
    }
    return action.create(newName, directory);
  }

  private CreateTypedResourceFileAction getActionByDir(PsiDirectory directory) {
    String baseDirName = directory.getName();
    ResourceFolderType folderType = ResourceFolderType.getFolderType(baseDirName);
    if (folderType == null) {
      return null;
    }
    return mySubactions.get(folderType);
  }

  @Override
  protected String getErrorTitle() {
    return CommonBundle.getErrorTitle();
  }

  @Override
  protected String getCommandName() {
    return AndroidBundle.message("new.resource.command.name");
  }

  @Nullable
  @Override
  protected String getActionName(PsiDirectory directory, String newName) {
    return doGetActionName(directory, newName);
  }

  static String doGetActionName(PsiDirectory directory, String newName) {
    if (FileUtilRt.getExtension(newName).length() == 0) {
      newName += ".xml";
    }
    return AndroidBundle.message("new.resource.action.name", directory.getName() + File.separator + newName);
  }
}
