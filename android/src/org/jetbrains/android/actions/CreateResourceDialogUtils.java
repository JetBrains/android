/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.navigator.AndroidProjectView;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import java.io.IOException;
import java.util.Collection;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import org.jetbrains.android.actions.widgets.SourceSetCellRenderer;
import org.jetbrains.android.actions.widgets.SourceSetItem;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.facet.SourceProviderManager;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Functions for create resource dialogs, where the dialogs depend on using source sets, and modules
 * for deciding the base resource directory.
 */
public final class CreateResourceDialogUtils {
  private static final Logger LOG = Logger.getInstance(ExternalSystemUtil.class);

  @Nullable
  private static String getResourceUrl(@NotNull JComboBox combo) {
    SourceSetItem comboItem = (SourceSetItem)combo.getSelectedItem();
    if (comboItem != null) {
      return comboItem.getResDirUrl();
    }
    return null;
  }

  /**
   * Tries to find or create the resource directory given by the Source Set {@link JComboBox}.
   * <p>
   * If either operation fails it will try to find the main Source Set resource directory.
   *
   * @param combo  The ComboBox used for Source Set selection, must hold items of type {@link SourceSetItem}.
   * @param module The desired selected {@link Module}, must be the same that was used to populate the Source Set ComboBox.
   * @return The {@link PsiDirectory} corresponding to an Android 'res' directory.
   */
  @Nullable
  public static PsiDirectory getOrCreateResourceDirectory(@NotNull JComboBox combo, @NotNull Module module) {
    String resDirUrl = getResourceUrl(combo);
    ApplicationManager.getApplication().assertReadAccessAllowed();
    final PsiManager manager = PsiManager.getInstance(module.getProject());
    VirtualFile virtualFile = null;
    if (resDirUrl != null) {
      // Find the VirtualFile for the resource directory
      virtualFile = VirtualFileManager.getInstance().findFileByUrl(resDirUrl);
      if (virtualFile == null) {
        try {
          // Try to create the VirtualFile for the resource directory
          virtualFile = VfsUtil.createDirectories(VfsUtilCore.urlToPath(resDirUrl));
        }
        catch (IOException ex) {
          LOG.warn(ex);
        }
      }
    }
    if (virtualFile != null) {
      PsiDirectory dir = manager.findDirectory(virtualFile);
      if (dir != null) {
        return dir;
      }
    }

    // Otherwise use the main source set:
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      VirtualFile res = ResourceFolderManager.getInstance(facet).getPrimaryFolder();
      if (res != null) {
        return PsiManager.getInstance(module.getProject()).findDirectory(res);
      }
    }

    return null;
  }

  public static void updateSourceSetCombo(@NotNull JComponent label,
                                          @NotNull JComboBox combo,
                                          @Nullable AndroidFacet facet,
                                          @Nullable PsiDirectory resDirectory) {
    if (resDirectory != null) {
      // No need of the source set combo if we already know the target res directory.
      // This should only happen when invoking the dialog from the project view under an specific res/ directory.
      label.setVisible(false);
      combo.setVisible(false);
      return;
    }
    if (facet != null && AndroidModel.isRequired(facet) && AndroidModel.get(facet) != null) {
      Collection<NamedIdeaSourceProvider> providers = SourceProviderManager.getInstance(facet).getCurrentAndSomeFrequentlyUsedInactiveSourceProviders();
      DefaultComboBoxModel<SourceSetItem> model = new DefaultComboBoxModel<SourceSetItem>();
      for (NamedIdeaSourceProvider sourceProvider : providers) {
        for (String resDirUrl : sourceProvider.getResDirectoryUrls()) {
          // In gradle, each source provider may have multiple res directories, so we create an element for each one of them.
          SourceSetItem item = SourceSetItem.create(sourceProvider, facet.getModule(), resDirUrl);
          if (item != null) {
            model.addElement(item);
          }
        }
      }
      combo.setModel(model);
      combo.setRenderer(new SourceSetCellRenderer());

      label.setVisible(true);
      combo.setVisible(true);
    }
    else {
      label.setVisible(false);
      combo.setVisible(false);
    }
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
      if (pane.getId().equals(AndroidProjectView.ID)) {
        return null;
      }
    }

    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (file != null) {
      // See if it's inside a res folder (or is equal to a resource folder)
      Module module = PlatformCoreDataKeys.MODULE.getData(dataContext);
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
}
