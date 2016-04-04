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

package org.jetbrains.android.actions;

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.android.tools.idea.ui.ApiComboBoxItem;
import com.intellij.ide.IdeView;
import com.intellij.ide.actions.ElementCreator;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.List;

/**
 * Based on {@link com.intellij.ide.actions.CreateElementActionBase} but
 * customized to let us <b>not</b> show the target directory chooser. This
 * is because we'll be able to pick the target directory ourselves based
 * on inputs in the dialog (e.g. the resource type, the source set chosen, etc).
 *
 * Also, instead of passing a specific directory <b>to</b> the dialog, we pass just
 * the data context, such that the dialog can figure out the actual directory on
 * its own (based on dialog choices like which source provider to use.)
 */
public abstract class CreateResourceActionBase extends AnAction {

  protected CreateResourceActionBase() {
  }

  protected CreateResourceActionBase(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @Nullable
  public static SourceProvider getSourceProvider(@Nullable JComboBox combo) {
    if (combo != null && combo.isVisible()) {
      Object selectedItem = combo.getSelectedItem();
      if (selectedItem instanceof ApiComboBoxItem) {
        return (SourceProvider)((ApiComboBoxItem)selectedItem).getData();
      }
    }

    return null;
  }

  @Nullable
  public static PsiDirectory getResourceDirectory(@Nullable SourceProvider sourceProvider, @NotNull Module module, boolean create) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (sourceProvider != null) {
      final PsiManager manager = PsiManager.getInstance(module.getProject());
      for (final File file : sourceProvider.getResDirectories()) {
        if (create && !file.exists()) {
          PsiDirectory dir = ApplicationManager.getApplication().runWriteAction(new Computable<PsiDirectory>() {
            @Override
            public PsiDirectory compute() {
              return DirectoryUtil.mkdirs(manager, FileUtil.toSystemIndependentName(file.getPath()));
            }
          });
          if (dir != null) {
            return dir;
          }
        }
        else {
          VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
          if (virtualFile != null) {
            PsiDirectory dir = manager.findDirectory(virtualFile);
            if (dir != null) {
              return dir;
            }
          }
        }
      }
    }

    // Otherwise use the main source set:
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      VirtualFile res = facet.getPrimaryResourceDir();
      if (res != null) {
        return PsiManager.getInstance(module.getProject()).findDirectory(res);
      }
    }

    return null;
  }

  public static void updateSourceSetCombo(@NotNull JComponent label, @NotNull JComboBox combo, @Nullable AndroidFacet facet,
                                          @SuppressWarnings("UnusedParameters") @Nullable PsiDirectory resDirectory) {
    // Ideally, if we're in the Project View and you select a file under main/res, we already know that
    // the resource folder is "res" and we pass it in here -- and we shouldn't ask the user for a source set.
    // However, in the Android Project view there is only a single "res" node, shared by multiple possible source
    // sets, so we *always* want to ask for the target source set there. We don't have a way to know which view
    // we're in here, so we default to always including the source set combo (if it's a Gradle project that is.)
    if (/*resDirectory == null && */ facet != null && facet.requiresAndroidModel() && facet.getAndroidModel() != null) {
      List<SourceProvider> providers = IdeaSourceProvider.getAllSourceProviders(facet);
      DefaultComboBoxModel model = new DefaultComboBoxModel();
      for (SourceProvider sourceProvider : providers) {
        //noinspection unchecked
        model.addElement(new ApiComboBoxItem(sourceProvider, sourceProvider.getName(), 0, 0));
      }
      combo.setModel(model);

      label.setVisible(true);
      combo.setVisible(true);
    } else {
      label.setVisible(false);
      combo.setVisible(false);
    }
  }

  /**
   * @return created elements. Never null.
   */
  @NotNull
  protected abstract PsiElement[] invokeDialog(@NotNull Project project, @NotNull DataContext dataContext);

  /**
   * @return created elements. Never null.
   */
  @NotNull
  protected abstract PsiElement[] create(String newName, PsiDirectory directory) throws Exception;

  protected abstract String getErrorTitle();

  @SuppressWarnings("UnusedDeclaration") // For symmetry with CreateElementActionBase
  protected abstract String getCommandName();

  protected abstract String getActionName(PsiDirectory directory, String newName);

  @Override
  public final void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();

    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      return;
    }

    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }

    // This is where we differ from CreateElementActionBase:
    // Rather than asking for a directory here, we just allow
    // *any* directory
    final PsiElement[] createdElements = invokeDialog(project, dataContext);

    for (PsiElement createdElement : createdElements) {
      view.selectElement(createdElement);
    }
  }

  @Override
  public void update(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Presentation presentation = e.getPresentation();

    final boolean enabled = isAvailable(dataContext);

    presentation.setVisible(enabled);
    presentation.setEnabled(enabled);
  }

  @Override
  public boolean isDumbAware() {
    return false;
  }

  protected boolean isAvailable(final DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return false;
    }

    if (DumbService.getInstance(project).isDumb() && !isDumbAware()) {
      return false;
    }

    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null || view.getDirectories().length == 0) {
      return false;
    }

    return true;
  }

  @Nullable
  private static VirtualFile getResFolderParent(@NotNull LocalResourceManager manager, @NotNull VirtualFile file) {
    VirtualFile current = file;
    while (current != null) {
      if (current.isDirectory() && manager.isResourceDir(current)) {
        return current;
      }
      current = current.getParent();
    }

    return null;
  }

  @Nullable
  public static PsiDirectory findResourceDirectory(@NotNull DataContext dataContext) {
    // Look at the set of selected files and see if one *specific* resource directory is implied (selected, or a parent
    // of all selected nodes); if so, use it; otherwise return null.
    //
    // In the Android Project View we don't want to do this, since there is only ever a single "res" node,
    // even when you have other overlays.
    // If you're in the Android View, we want to ask you not just the filename but also let you
    // create other resource folder configurations
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project != null) {
      AbstractProjectViewPane pane = ProjectView.getInstance(project).getCurrentProjectViewPane();
      if (pane instanceof AndroidProjectViewPane) {
        return null;
      }
    }

    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (file != null) {
      // See if it's inside a res folder (or is equal to a resource folder
      Module module = LangDataKeys.MODULE.getData(dataContext);
      if (module != null) {
        LocalResourceManager manager = LocalResourceManager.getInstance(module);
        if (manager != null) {
          VirtualFile resFolder = getResFolderParent(manager, file);
          if (resFolder != null) {
            return AndroidPsiUtils.getPsiDirectorySafely(module.getProject(), resFolder);
          }
        }
      }
    }

    return null;
  }

  protected class MyInputValidator extends ElementCreator implements ElementCreatingValidator {
    private final PsiDirectory myDirectory;
    private PsiElement[] myCreatedElements = PsiElement.EMPTY_ARRAY;

    public MyInputValidator(final Project project, final PsiDirectory directory) {
      super(project, getErrorTitle());
      myDirectory = directory;
    }

    @Override
    public boolean checkInput(final String inputString) {
      return true;
    }

    @Override
    public PsiElement[] create(String newName) throws Exception {
      return CreateResourceActionBase.this.create(newName, myDirectory);
    }

    @Override
    public String getActionName(String newName) {
      return CreateResourceActionBase.this.getActionName(myDirectory, newName);
    }

    @Override
    public boolean canClose(final String inputString) {
      myCreatedElements = tryCreate(inputString);
      return myCreatedElements.length > 0;
    }

    @NotNull
    @Override
    public final PsiElement[] getCreatedElements() {
      return myCreatedElements;
    }
  }
}