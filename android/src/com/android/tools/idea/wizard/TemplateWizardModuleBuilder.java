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
package com.android.tools.idea.wizard;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.collect.ImmutableSet;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;

/*
 * Builder that also supports creating Android modules
 */
public class TemplateWizardModuleBuilder extends ImportWizardModuleBuilder {
  protected static final String PROJECT_NAME = "Android Project";
  protected static final String MODULE_NAME = "Android Module";
  protected static final String APP_TEMPLATE_NAME = "Android Application";
  protected static final String LIB_TEMPLATE_NAME = "Android Library";

  protected static final Set<String> EXCLUDED_TEMPLATES = ImmutableSet.of(MODULE_NAME, PROJECT_NAME);

  @Nullable private final TemplateMetadata myMetadata;
  private NewAndroidModulePath myNewAndroidModulePath;
  private TemplateParameterStep myTemplateParameterStep;

  public TemplateWizardModuleBuilder(@Nullable File templateLocation,
                                     @Nullable TemplateMetadata metadata,
                                     @Nullable Project project,
                                     @Nullable Icon sidePanelIcon,
                                     @NotNull List<ModuleWizardStep> steps,
                                     @NotNull Disposable disposable,
                                     boolean inGlobalWizard) {
    super(templateLocation, project, null, sidePanelIcon, steps, disposable, inGlobalWizard);
    myWizardState.myIsModuleImport = false;
    myMetadata = metadata;
  }

  @Override
  protected WizardPath[] setupWizardPaths(Project project, Icon sidePanelIcon, Disposable disposable) {
    WizardPath[] paths = super.setupWizardPaths(project, sidePanelIcon, disposable);
    myNewAndroidModulePath = new NewAndroidModulePath(myWizardState, this, project, sidePanelIcon, disposable);
    myTemplateParameterStep = new TemplateParameterStep(myWizardState, project, null, sidePanelIcon, this);
    mySteps.add(new ChooseAndroidAndJavaSdkStep());
    mySteps.add(myTemplateParameterStep);

    mySteps.add(0, buildChooseModuleStep(project));
    addSteps(myNewAndroidModulePath);
    return ArrayUtil.append(paths, myNewAndroidModulePath);
  }

  @Override
  public void templateChanged(String templateName) {
    myNewAndroidModulePath.templateChanged();
    super.templateChanged(templateName);
  }

  @Nullable
  @Override
  public String getBuilderId() {
    assert myMetadata != null;
    return myMetadata.getTitle();
  }

  @Override
  public boolean updateWizardSteps() {
    if (super.updateWizardSteps()) {
      myTemplateParameterStep.setVisible(!myWizardState.myIsAndroidModule);
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Create a template chooser step populated with the correct templates for the new modules.
   */
  @VisibleForTesting
  protected ChooseTemplateStep buildChooseModuleStep(@Nullable Project project) {
    // We're going to build up our own list of templates here
    // This is a little hacky, we should clean this up later.
    ChooseTemplateStep chooseModuleStep =
      new ChooseTemplateStep(myWizardState, null, project, null, AndroidIcons.Wizards.NewModuleSidePanel,
                             this, this);

    // Get the list of templates to offer, but exclude the NewModule and NewProject template
    List<ChooseTemplateStep.MetadataListItem> templateList =
      ChooseTemplateStep.getTemplateList(myWizardState, CATEGORY_PROJECTS, EXCLUDED_TEMPLATES);

    // Now, we're going to add in two pointers to the same template
    File moduleTemplate = new File(TemplateManager.getTemplateRootFolder(),
                                   FileUtil.join(CATEGORY_PROJECTS, NewProjectWizardState.MODULE_TEMPLATE_NAME));
    TemplateManager manager = TemplateManager.getInstance();
    TemplateMetadata metadata = manager.getTemplate(moduleTemplate);

    ChooseTemplateStep.MetadataListItem appListItem = new ChooseTemplateStep.MetadataListItem(moduleTemplate, metadata) {
      @Override
      public String toString() {
        return APP_TEMPLATE_NAME;
      }
    };
    ChooseTemplateStep.MetadataListItem libListItem = new ChooseTemplateStep.MetadataListItem(moduleTemplate, metadata) {
      @Override
      public String toString() {
        return LIB_TEMPLATE_NAME;
      }
    };
    templateList.add(0, libListItem);
    templateList.add(0, appListItem);
    chooseModuleStep.setListData(templateList);
    return chooseModuleStep;
  }

}
