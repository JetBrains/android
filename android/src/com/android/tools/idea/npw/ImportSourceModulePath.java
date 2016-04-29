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
package com.android.tools.idea.npw;

import com.android.tools.idea.gradle.project.ModuleImporter;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.template.TemplateWizardStep;
import com.google.common.collect.Lists;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.npw.ChooseTemplateStep.MetadataListItem;

/**
 * Create a new module using existing ADT or Gradle source files.
 */
public class ImportSourceModulePath implements WizardPath {
  @NotNull private final NewModuleWizardState myWizardState;
  @NotNull private final WizardContext myContext;
  private Collection<ModuleWizardStep> mySteps;

  public ImportSourceModulePath(@Nullable VirtualFile importSource,
                                @NotNull NewModuleWizardState wizardState,
                                @NotNull WizardContext context,
                                @Nullable Icon sidePanelIcon,
                                @Nullable TemplateWizardStep.UpdateListener listener) {
    myWizardState = wizardState;
    myContext = context;
    List<ModuleWizardStep> steps = Lists.newLinkedList();
    ImportSourceLocationStep locationStep = new ImportSourceLocationStep(context, importSource,
                                                                         wizardState, sidePanelIcon, listener);
    steps.add(locationStep);
    for (ModuleImporter importer : ModuleImporter.getAllImporters(myContext)) {
      steps.addAll(importer.createWizardSteps());
    }
    mySteps = steps;
  }

  @NotNull
  protected static MetadataListItem createImportTemplateWithCustomName(@NotNull final String importTemplateName,
                                                                       @Nullable final String description) {
    // Now, we're going to add in two pointers to the same template
    File moduleTemplate = new File(TemplateManager.getTemplateRootFolder(),
                                   FileUtil.join(Template.CATEGORY_PROJECTS, "ImportExistingProject"));
    TemplateManager manager = TemplateManager.getInstance();
    TemplateMetadata metadata = manager.getTemplateMetadata(moduleTemplate);

    assert metadata != null;

    return new ChooseTemplateStep.MetadataListItem(moduleTemplate, metadata) {
      @Override
      public String toString() {
        return importTemplateName;
      }

      @Nullable
      @Override
      public String getDescription() {
        return description == null ? super.getDescription() : description;
      }
    };
  }

  @Override
  public Collection<ModuleWizardStep> getSteps() {
    return mySteps;
  }

  @Override
  public void update() {
    for (ModuleWizardStep step : mySteps) {
      step.updateStep();
    }
  }

  @Override
  public void createModule() {
    Map<String, VirtualFile> modulesToImport = myWizardState.getModulesToImport();
    ModuleImporter.getImporter(myContext).importProjects(modulesToImport);
  }

  @Override
  public boolean isStepVisible(@NotNull ModuleWizardStep step) {
    if (!mySteps.contains(step)) {
      return false;
    }
    if (step instanceof ImportSourceLocationStep || ModuleImporter.getImporter(myContext).isStepVisible(step)) {
      return step.isStepVisible();
    }
    return false;
  }

  @Override
  public Collection<String> getExcludedTemplates() {
    return Collections.singleton(NewModuleWizardState.MODULE_IMPORT_NAME);
  }

  @Override
  public Collection<MetadataListItem> getBuiltInTemplates() {
    return Collections.singleton(createImportTemplateWithCustomName(NewModuleWizardState.MODULE_IMPORT_NAME, null));
  }

  @Override
  public boolean supportsGlobalWizard() {
    return false;
  }
}
