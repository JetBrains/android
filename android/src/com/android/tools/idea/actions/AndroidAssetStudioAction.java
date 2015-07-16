/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.actions;

import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.android.tools.idea.npw.AssetStudioWizard;
import com.intellij.ide.IdeView;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Action to invoke the Asset Studio. This action is visible
 * anywhere within a module that has an Android facet.
 */
public class AndroidAssetStudioAction extends AnAction {


  public AndroidAssetStudioAction () {
    super("Image Asset", "Open Asset Studio to create an image asset", AndroidIcons.Android);
  }

  public AndroidAssetStudioAction (@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(isAvailable(e.getDataContext()));
  }

  protected static boolean isAvailable(DataContext dataContext) {
    final Module module = LangDataKeys.MODULE.getData(dataContext);
    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);

    if (module == null ||
        view == null ||
        view.getDirectories().length == 0 ||
        AndroidFacet.getInstance(module) == null) {
      return false;
    }
    return true;
  }

  // AndroidVectorAssetStudioAction will override this and provide different wizard for vector assets.
  protected void showWizardAndCreateAsset(Project project, Module module, VirtualFile targetFile) {
    AssetStudioWizard dialog = new AssetStudioWizard(project, module, targetFile);
    if (!dialog.showAndGet()) {
      return;
    }
    dialog.createAssets();
  }


  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();

    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      return;
    }

    final Module module = LangDataKeys.MODULE.getData(dataContext);

    if (module == null) {
      return;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return;
    }

    // If you're invoking the Asset Studio by right clicking in the Android Project view on a drawable or
    // mipmap folder for example, the IDE will ask you to pick a specific folder from one of the many
    // actual folders packed into the single resource directory.
    //
    // However, in this case we don't need those folders; we just want the corresponding source set, so
    // asking the user to choose between "drawable-mdpi" and "drawable-hdpi" isn't helpful.
    final PsiDirectory dir;
    AbstractProjectViewPane pane = ProjectView.getInstance(module.getProject()).getCurrentProjectViewPane();
    if (pane instanceof AndroidProjectViewPane) {
      PsiDirectory[] directories = view.getDirectories();
      if (directories.length == 0) {
        return;
      }
      dir = directories[0];
    } else {
      dir = view.getOrChooseDirectory();
    }
    if (dir == null) {
      return;
    }

    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    VirtualFile targetFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);

    showWizardAndCreateAsset(project, module, targetFile);
  }
}
