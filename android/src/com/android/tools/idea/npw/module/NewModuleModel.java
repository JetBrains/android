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
import com.android.tools.idea.npw.template.RenderTemplateModel;
import com.android.tools.idea.npw.template.TemplateValueInjector;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.wizard.model.WizardModel;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.templates.TemplateUtils.checkedCreateDirectoryIfMissing;
import static org.jetbrains.android.util.AndroidBundle.message;

public final class NewModuleModel extends WizardModel {
  @NotNull private final StringProperty myModuleName = new StringValueProperty();
  @NotNull private final BoolProperty myIsLibrary = new BoolValueProperty();
  // A template that's associated with a user's request to create a new module. This may be null if the user skips creating a
  // module, or instead modifies an existing module (for example just adding a new Activity)
  @NotNull private final OptionalProperty<File> myTemplateFile = new OptionalValueProperty<>();
  @NotNull private final OptionalProperty<Map<String, Object>> myRenderTemplateValues = new OptionalValueProperty<>();
  @NotNull private final Map<String, Object> myTemplateValues = Maps.newHashMap();

  @NotNull private final StringProperty myApplicationName;
  @NotNull private final StringProperty myPackageName;
  @NotNull private final StringProperty mySupportedRoutes = new StringValueProperty("/.*");
  @NotNull private final BoolProperty myInstantApp = new BoolValueProperty();
  @NotNull private final boolean myCreateIAPK;
  @NotNull private final BoolProperty myEnableCppSupport;
  @NotNull private final StringProperty myCppFlags;
  @NotNull private final OptionalProperty<Project> myProject;

  { // Default init constructor
    myModuleName.addConstraint(String::trim);
    mySupportedRoutes.addConstraint(String::trim);
  }

  public NewModuleModel(@NotNull Project project) {
    myProject = new OptionalValueProperty<>(project);
    myPackageName = new StringValueProperty();
    myCreateIAPK = false;
    myEnableCppSupport = new BoolValueProperty();
    myCppFlags = new StringValueProperty();

    myApplicationName = new StringValueProperty(message("android.wizard.module.config.new.application"));
    myApplicationName.addConstraint(String::trim);
    myIsLibrary.addListener(sender -> myApplicationName.set(
      message(myIsLibrary.get() ? "android.wizard.module.config.new.library" : "android.wizard.module.config.new.application")));
  }

  public NewModuleModel(@NotNull NewProjectModel projectModel, @NotNull File templateFile) {
    myProject = projectModel.project();
    myPackageName = projectModel.packageName();
    myCreateIAPK = true;
    myEnableCppSupport = projectModel.enableCppSupport();
    myCppFlags = projectModel.cppFlags();
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
  public StringProperty supportedRoutes() {
    return mySupportedRoutes;
  }

  @NotNull
  public BoolProperty instantApp() {
    return myInstantApp;
  }

  @NotNull
  public BoolProperty enableCppSupport() {
    return myEnableCppSupport;
  }

  @NotNull
  public StringProperty cppFlags() {
    return myCppFlags;
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

  /**
   * This method should be called if there is no "Activity Render Template" step (For example when creating a Library, or the activity
   * creation is skipped by the user)
   */
  public void setDefaultRenderTemplateValues(@NotNull RenderTemplateModel renderModel) {
    Map<String, Object> renderTemplateValues = Maps.newHashMap();
    new TemplateValueInjector(renderTemplateValues)
      .setBuildVersion(renderModel.androidSdkInfo().getValue())
      .setModuleRoots(renderModel.getSourceSet().get().getPaths(), packageName().get());

    getRenderTemplateValues().setValue(renderTemplateValues);
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
    Map<String, Object> templateValues = new HashMap<>(myTemplateValues);

    templateValues.put(ATTR_IS_LIBRARY_MODULE, myIsLibrary.get());

    if (renderTemplateValues != null) {
      // Cpp/Instant Apps attributes are needed to generate the Module and to generate the Render Template files (activity and layout)
      renderTemplateValues.put(ATTR_CPP_SUPPORT, myEnableCppSupport.get());
      renderTemplateValues.put(ATTR_CPP_FLAGS, myCppFlags.get());
      if (myInstantApp.get()) {
        renderTemplateValues.put(ATTR_IS_INSTANT_APP, myInstantApp.get());
        renderTemplateValues.put(ATTR_SPLIT_NAME, myModuleName.get());
      }

      templateValues.putAll(renderTemplateValues);
    }

    //TODO - this code is a little messy. Once atom workflow is finalised refactor to avoid template parameter copying and overriding
    boolean createIAPK = myInstantApp.get() && myCreateIAPK;
    final Map<String, Object> moduleTemplateState = createIAPK ? new  HashMap<>(templateValues) : templateValues;
    if (createIAPK) {
      moduleTemplateState.put(ATTR_IS_LIBRARY_MODULE, Boolean.TRUE);
      moduleTemplateState.put(ATTR_IS_BASE_ATOM, Boolean.TRUE);
    }

    Project project = myProject.getValue();

    boolean canRender = renderModule(true, moduleTemplateState, project, myModuleName.get());
    if (!canRender) {
      // If here, there was a render conflict and the user chose to cancel creating the template
      return;
    }

    // Note, this naming is provisional until the instant app workflow is better sorted out.
    String iapkName = "instant-" + /*myModuleName.get()*/ "app"; // TODO
    Map<String, Object> iapkTemplateState = createIAPK ? new  HashMap<>(templateValues) : templateValues;
    if (createIAPK) {
      iapkTemplateState.put(ATTR_ALSO_CREATE_IAPK, Boolean.TRUE);
      iapkTemplateState.put(ATTR_ATOM_NAME, myModuleName.get());
      iapkTemplateState.put(ATTR_MODULE_NAME, iapkName);
      iapkTemplateState.put(ATTR_SPLIT_NAME, iapkName);

      File iapkRoot = new File(project.getBasePath(), iapkName);
      try {
        checkedCreateDirectoryIfMissing(iapkRoot);
      }
      catch (IOException e) {
        getLog().error(e);
        return;
      }
      iapkTemplateState.put(ATTR_PROJECT_OUT, iapkRoot.getPath());
      String relativeLocation = FileUtil.toSystemDependentName((String)iapkTemplateState.get(ATTR_MANIFEST_DIR));
      iapkTemplateState.put(ATTR_MANIFEST_OUT, new File(iapkRoot, relativeLocation).getPath());

      if (!renderModule(true, iapkTemplateState, project, iapkName)) {
        // If here, there was a render conflict and the user chose to cancel creating the template
        return;
      }
    }

    boolean success = new WriteCommandAction<Boolean>(project, "New Module") {
      @Override
      protected void run(@NotNull Result<Boolean> result) throws Throwable {
        boolean success = renderModule(false, moduleTemplateState, project, myModuleName.get());
        if (success && createIAPK) {
          success = renderModule(false, iapkTemplateState, project, iapkName);
        }
        result.setResult(success);
      }
    }.execute().getResultObject();

    if (!success) {
      getLog().warn("A problem occurred while creating a new Module. Please check the log file for possible errors.");
    }
  }

  private boolean renderModule(boolean dryRun, @NotNull Map<String, Object> templateState, @NotNull Project project,
                               @NotNull  String moduleName) {
    File projectRoot = new File(project.getBasePath());
    File moduleRoot = new File(projectRoot, moduleName);
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
