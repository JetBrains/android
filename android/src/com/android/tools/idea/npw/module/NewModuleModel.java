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
 * limitations under the License.
 */
package com.android.tools.idea.npw.module;

import com.android.tools.idea.npw.project.NewProjectModel;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.wizard.model.WizardModel;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LIBRARY_MODULE;
import static org.jetbrains.android.util.AndroidBundle.message;

public final class NewModuleModel extends WizardModel {
  private final StringProperty myModuleName = new StringValueProperty();
  private final BoolProperty myIsLibrary = new BoolValueProperty();
  private final BoolProperty myInstAppEnabled = new BoolValueProperty();
  // A template that's associated with a user's request to create a new module. This may be null if the user skips creating a
  // module, or instead modifies an existing module (for example just adding a new Activity)
  private final OptionalProperty<File> myTemplateFile = new OptionalValueProperty<>();
  private final OptionalProperty<Map<String, Object>> myRenderTemplateValues = new OptionalValueProperty<>();
  private final Map<String, Object> myTemplateValues = Maps.newHashMap();

  @NotNull private final StringProperty myApplicationName;
  @NotNull private final StringProperty myPackageName;
  @NotNull private final OptionalProperty<Project> myProject;

  { // Default init constructor
    myModuleName.addConstraint(String::trim);
  }

  public NewModuleModel(@NotNull Project project) {
    myProject = new OptionalValueProperty<>(project);
    myPackageName = new StringValueProperty();

    myApplicationName = new StringValueProperty(message("android.wizard.module.config.new.application"));
    myApplicationName.addConstraint(String::trim);
    myIsLibrary.addListener(sender -> myApplicationName.set(
      message(myIsLibrary.get() ? "android.wizard.module.config.new.library" : "android.wizard.module.config.new.application")));
  }

  public NewModuleModel(@NotNull NewProjectModel projectModel, @NotNull File templateFile) {
    myProject = projectModel.project();
    myPackageName = projectModel.packageName();
    myApplicationName = projectModel.applicationName();
    myTemplateFile.setValue(templateFile);
  }

  @NotNull
  public OptionalProperty<Project> getProject() {
    return myProject;
  }

  @NotNull
  public StringProperty applicationName() {
    return myApplicationName;
  }

  @NotNull
  public StringProperty moduleName() {
    return myModuleName;
  }

  @NotNull
  public StringProperty packageName() {
    return myPackageName;
  }

  @NotNull
  public BoolProperty isLibrary() {
    return myIsLibrary;
  }

  @NotNull
  public BoolProperty isInstAppEnabled() {
    return myInstAppEnabled;
  }

  @NotNull
  public OptionalProperty<File> templateFile() {
    return myTemplateFile;
  }

  @NotNull
  public Map<String, Object> getTemplateValues() {
    return myTemplateValues;
  }

  @NotNull
  public OptionalProperty<Map<String, Object>> getRenderTemplateValues() {
    return myRenderTemplateValues;
  }

  @Override
  protected void handleFinished() {
    if (myTemplateFile.getValueOrNull() == null) {
      return; // If here, the user opted to skip creating any module at all, or is just adding a new Activity
    }

    // By the time we run handleFinished(), we must have a Project
    if (!myProject.get().isPresent()) {
      getLog().error("NewModuleModel did not collect expected information and will not complete. Please report this error.");
      return;
    }

    Map<String, Object> renderTemplateValues = myRenderTemplateValues.getValueOrNull();
    Map<String, Object> templateValues = new HashMap<>();

    if (renderTemplateValues != null) {
      templateValues.putAll(renderTemplateValues);
    }
    templateValues.putAll(myTemplateValues);

    templateValues.put(ATTR_IS_LIBRARY_MODULE, myIsLibrary.get());

    Project project = myProject.getValue();

    boolean canRender = renderModule(true, templateValues, project);
    if (!canRender) {
      // If here, there was a render conflict and the user chose to cancel creating the template
      return;
    }

    boolean success = new WriteCommandAction<Boolean>(project, "New Module") {
      @Override
      protected void run(@NotNull Result<Boolean> result) throws Throwable {
        boolean success = renderModule(false, templateValues, project);
        result.setResult(success);
      }
    }.execute().getResultObject();

    if (!success) {
      getLog().warn("A problem occurred while creating a new Module. Please check the log file for possible errors.");
    }
  }

  private boolean renderModule(boolean dryRun, @NotNull Map<String, Object> templateState, @NotNull Project project) {
    File projectRoot = new File(project.getBasePath());
    File moduleRoot = new File(projectRoot, myModuleName.get());
    Template template = Template.createFromPath(myTemplateFile.getValue());

    // @formatter:off
    RenderingContext context = RenderingContext.Builder.newContext(template, project)
      .withCommandName("New Module")
      .withDryRun(dryRun)
      .withShowErrors(true)
      .withOutputRoot(projectRoot)
      .withModuleRoot(moduleRoot)
      .withParams(templateState)
      //.withGradleSync(myGradleSyncIfNecessary) // TODO: Check that we still need this
      //.intoTargetFiles(myState.get(TARGET_FILES_KEY))
      //.intoOpenFiles(myState.get(FILES_TO_OPEN_KEY))
      //.intoDependencies(myState.get(DEPENDENCIES_KEY))
      .build();
    // @formatter:on
    return template.render(context);
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(NewModuleModel.class);
  }
}
