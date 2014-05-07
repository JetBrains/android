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

import com.android.tools.gradle.eclipse.GradleImport;
import com.android.tools.idea.gradle.eclipse.AdtImportBuilder;
import com.android.tools.idea.gradle.eclipse.AdtImportProvider;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.ImportSourceKind;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static com.android.tools.idea.wizard.ChooseTemplateStep.MetadataListItem;

/**
 * Create a new module using existing ADT or Gradle source files.
 */
public class ImportSourceModulePath implements WizardPath {
  private final static Logger LOG = Logger.getInstance(ImportSourceLocationStep.class);

  @NotNull private final NewModuleWizardState myWizardState;
  @NotNull private final WizardContext myContext;
  private Collection<ModuleWizardStep> mySteps;

  public ImportSourceModulePath(@Nullable VirtualFile importSource,
                                @NotNull NewModuleWizardState wizardState,
                                @NotNull WizardContext context,
                                @NotNull Disposable disposable,
                                @Nullable TemplateWizardStep.UpdateListener listener) {
    myWizardState = wizardState;
    myContext = context;
    AdtImportProvider provider = new AdtImportProvider(false);
    context.setProjectBuilder(provider.getBuilder());
    ModuleWizardStep[] adtImportSteps = provider.createSteps(context);
    ImportSourceLocationStep locationStep = new ImportSourceLocationStep(context, importSource,
                                                                         wizardState, disposable, listener);
    mySteps = Lists.asList(locationStep, adtImportSteps);
  }

  @NotNull
  protected static MetadataListItem createImportTemplateWithCustomName(@NotNull final String importTemplateName) {
    // Now, we're going to add in two pointers to the same template
    File moduleTemplate = new File(TemplateManager.getTemplateRootFolder(),
                                   FileUtil.join(Template.CATEGORY_PROJECTS, "ImportExistingProject"));
    TemplateManager manager = TemplateManager.getInstance();
    TemplateMetadata metadata = manager.getTemplate(moduleTemplate);

    assert metadata != null;

    return new ChooseTemplateStep.MetadataListItem(moduleTemplate, metadata) {
      @Override
      public String toString() {
        return importTemplateName;
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
    ImportSourceKind importKind = myWizardState.getImportKind();
    assert importKind != null && modulesToImport != null;
    switch (importKind) {
      case ADT:
        importAdtModules(modulesToImport);
        break;
      case GRADLE:
        importGradleModule(modulesToImport);
        break;
      default:
        LOG.error("Unsupported import kind: " + importKind);
    }
  }

  private void importGradleModule(@NotNull Map<String, VirtualFile> modules) {
    try {
      Project project = myContext.getProject();
      assert project != null;
      GradleProjectImporter.getInstance().importModules(modules, project, null);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    catch (ConfigurationException e) {
      LOG.error(e);
    }
  }

  private void importAdtModules(@NotNull Map<String, VirtualFile> modules) {
    AdtImportBuilder builder = AdtImportBuilder.getBuilder(myContext);
    Project project = myContext.getProject();
    assert builder != null && project != null;
    GradleImport importer = builder.getImporter();
    assert importer != null;
    importer.setModulesToImport(Maps.transformValues(modules, new Function<VirtualFile, File>() {
      @Override
      public File apply(VirtualFile input) {
        return VfsUtilCore.virtualToIoFile(input);
      }
    }));
    if (builder.validate(null, project)) {
      builder.commit(project, null, ModulesProvider.EMPTY_MODULES_PROVIDER, null);
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        project.save();
      }
      builder.cleanup();
    }
  }

  @Override
  public boolean isStepVisible(@NotNull ModuleWizardStep step) {
    return mySteps.contains(step) &&
           (myWizardState.getImportKind() == ImportSourceKind.ADT || step instanceof ImportSourceLocationStep) &&
           step.isStepVisible();
  }

  @Override
  public Collection<String> getExcludedTemplates() {
    return Collections.singleton(NewModuleWizardState.MODULE_IMPORT_NAME);
  }

  @Override
  public Collection<MetadataListItem> getBuiltInTemplates() {
    return Collections.singleton(createImportTemplateWithCustomName(NewModuleWizardState.MODULE_IMPORT_NAME));
  }

  @Override
  public boolean supportsGlobalWizard() {
    return false;
  }
}
