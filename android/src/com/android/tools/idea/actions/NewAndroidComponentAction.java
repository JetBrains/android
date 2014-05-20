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

import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.NewTemplateObjectWizard;
import com.google.common.collect.ImmutableSet;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * An action to launch a wizard to create a component from a template.
 */
public class NewAndroidComponentAction extends AnAction {

  private static final Set<String> EXCLUDED = ImmutableSet.of();

  private final String myTemplateCategory;
  private final String myTemplateName;
  private final int myMinSdkVersion;

  public NewAndroidComponentAction(@NotNull String templateCategory, @NotNull String templateName, @Nullable TemplateMetadata metadata) {
    super(templateName, "Create a new " + templateName, null);
    myTemplateCategory = templateCategory;
    myTemplateName = templateName;
    if (templateCategory.equals("Activity")) {
      getTemplatePresentation().setIcon(AndroidIcons.Activity);
    }
    else {
      getTemplatePresentation().setIcon(AndroidIcons.AndroidFile);
    }
    if (metadata != null) {
      myMinSdkVersion = metadata.getMinSdk();
    }
    else {
      myMinSdkVersion = 0;
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Module module = LangDataKeys.MODULE.getData(dataContext);
    if (module == null) {
      return;
    }
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.get(module);
    if (moduleInfo == null) {
      return;
    }
    int moduleMinSdkVersion = moduleInfo.getMinSdkVersion();
    if (myMinSdkVersion > moduleMinSdkVersion) {
      Presentation presentation = getTemplatePresentation();
      presentation.setText(myTemplateName + " (Requires minSdk >= " + myMinSdkVersion + ")");
      presentation.setEnabled(false);
    }
  }


  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();

    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      return;
    }

    final Module module = LangDataKeys.MODULE.getData(dataContext);

    if (module == null) return;
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    VirtualFile targetFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);

    NewTemplateObjectWizard dialog = new NewTemplateObjectWizard(module, targetFile, myTemplateCategory,
                                                                 myTemplateName, EXCLUDED);

    dialog.show();
    if (dialog.isOK()) {
      dialog.createTemplateObject();
    }

    /*
    // TODO: Implement the getCreatedElements call for the wizard
    final PsiElement[] createdElements = dialog.getCreatedElements();

    for (PsiElement createdElement : createdElements) {
      view.selectElement(createdElement);
    }
    */
  }
}
