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

import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.net.URL;

/**
 * Action to invoke one of the Asset Studio wizards.
 *
 * This action is visible anywhere within a module that has an Android facet.
 */
public abstract class AndroidAssetStudioAction extends AnAction {

  protected AndroidAssetStudioAction(@Nullable String text, @Nullable String description) {
    super(text, description, AndroidIcons.Android);
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

  @Override
  public final void update(AnActionEvent e) {
    e.getPresentation().setVisible(isAvailable(e.getDataContext()));
  }

  @Override
  public final void actionPerformed(AnActionEvent e) {
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

    ModelWizard wizard = createWizard(facet);
    if (wizard != null) {
      StudioWizardDialogBuilder dialogBuilder = new StudioWizardDialogBuilder(wizard, "Asset Studio");
      dialogBuilder.setProject(facet.getModule().getProject())
        .setMinimumSize(getWizardMinimumSize())
        .setPreferredSize(getWizardPreferredSize())
        .setHelpUrl(getHelpUrl());
      dialogBuilder.build().show();
    }
  }

  /**
   * Create a wizard to show or {@code null} if the showing of a wizard should be aborted. If a
   * child class aborts showing the wizard, it should still give some visual indication, such as
   * an error dialog.
   */
  @Nullable
  protected abstract ModelWizard createWizard(@NotNull AndroidFacet facet);

  @NotNull
  protected abstract Dimension getWizardMinimumSize();

  @NotNull
  protected abstract Dimension getWizardPreferredSize();

  @Nullable
  protected URL getHelpUrl() {
    return null;
  }
}
