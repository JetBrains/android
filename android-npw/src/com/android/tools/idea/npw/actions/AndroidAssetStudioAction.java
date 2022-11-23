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
package com.android.tools.idea.npw.actions;

import com.android.tools.idea.projectsystem.AndroidModulePaths;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import icons.StudioIcons;
import java.awt.Dimension;
import java.io.File;
import java.net.URL;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Action to invoke one of the Asset Studio wizards.
 *
 * This action is visible anywhere within a module that has an Android facet.
 */
public abstract class AndroidAssetStudioAction extends AnAction {

  protected AndroidAssetStudioAction(@Nullable String text, @Nullable String description) {
    super(text, description, StudioIcons.Common.ANDROID_HEAD);
  }

  protected static boolean isAvailable(@NotNull DataContext dataContext) {
    Module module = PlatformCoreDataKeys.MODULE.getData(dataContext);
    IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    VirtualFile location = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);

    return module != null &&
           view != null &&
           location != null &&
           view.getDirectories().length > 0 &&
           AndroidFacet.getInstance(module) != null &&
           ProjectSystemUtil.getProjectSystem(module.getProject()).allowsFileCreation() &&
           getModuleTemplate(module, location) != null;
  }

  @Nullable
  private static NamedModuleTemplate getModuleTemplate(@NotNull Module module, @NotNull VirtualFile location) {
    for (NamedModuleTemplate namedTemplate : ProjectSystemUtil.getModuleSystem(module).getModuleTemplates(location)) {
      if (!namedTemplate.getPaths().getResDirectories().isEmpty()) {
        return namedTemplate;
      }
    }
    return null;
  }

  @Nullable
  private static File findClosestResFolder(@NotNull AndroidModulePaths paths, @NotNull VirtualFile location) {
    String toFind = location.getPath();
    File bestMatch = null;
    int bestCommonPrefixLength = -1;
    for (File resDir : paths.getResDirectories()) {
      int commonPrefixLength = StringUtil.commonPrefixLength(resDir.getPath(), toFind);
      if (commonPrefixLength > bestCommonPrefixLength) {
        bestCommonPrefixLength = commonPrefixLength;
        bestMatch = resDir;
      }
    }
    return bestMatch;
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    e.getPresentation().setVisible(isAvailable(e.getDataContext()));
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();

    IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      return;
    }

    Module module = PlatformCoreDataKeys.MODULE.getData(dataContext);
    if (module == null) {
      return;
    }

    VirtualFile location = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (location == null) {
      return;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return;
    }

    NamedModuleTemplate template = getModuleTemplate(module, location);
    if (template == null) {
      return;
    }

    File resFolder = findClosestResFolder(template.getPaths(), location);
    if (resFolder == null) {
      return;
    }

    ModelWizard wizard = createWizard(facet, template, resFolder);
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
   * Creates a wizard to show or returns {@code null} if the showing of a wizard should be aborted.
   * If a subclass class aborts showing the wizard, it should still give some visual indication,
   * such as an error dialog.
   */
  @Nullable
  protected abstract ModelWizard createWizard(@NotNull AndroidFacet facet, @NotNull NamedModuleTemplate template, @NotNull File resFolder);

  @NotNull
  protected abstract Dimension getWizardMinimumSize();

  @NotNull
  protected abstract Dimension getWizardPreferredSize();

  @Nullable
  protected URL getHelpUrl() {
    return null;
  }
}
