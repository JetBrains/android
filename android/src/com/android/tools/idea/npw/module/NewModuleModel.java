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

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.instantapp.InstantApps;
import com.android.tools.idea.npw.project.NewProjectModel;
import com.android.tools.idea.npw.template.MultiTemplateRenderer;
import com.android.tools.idea.npw.template.RenderTemplateModel;
import com.android.tools.idea.npw.template.TemplateValueInjector;
import com.android.tools.idea.observable.core.*;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.expressions.string.StringExpression;
import com.android.tools.idea.wizard.model.WizardModel;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.intellij.openapi.util.io.FileUtil.join;
import static org.jetbrains.android.util.AndroidBundle.message;

public final class NewModuleModel extends WizardModel {
  private final BindingsManager myBindings = new BindingsManager();

  @NotNull private final StringProperty myModuleName = new StringValueProperty();
  @NotNull private final StringProperty mySplitName = new StringValueProperty("feature");
  @NotNull private final BoolProperty myIsLibrary = new BoolValueProperty();
  // A template that's associated with a user's request to create a new module. This may be null if the user skips creating a
  // module, or instead modifies an existing module (for example just adding a new Activity)
  @NotNull private final OptionalProperty<File> myTemplateFile = new OptionalValueProperty<>();
  @NotNull private final OptionalProperty<Map<String, Object>> myRenderTemplateValues = new OptionalValueProperty<>();
  @NotNull private final Map<String, Object> myTemplateValues = Maps.newHashMap();

  @NotNull private final StringProperty myApplicationName;
  @NotNull private final StringProperty myPackageName = new StringValueProperty();
  @NotNull private final StringProperty myProjectPackageName;
  @NotNull private final BoolProperty myIsInstantApp = new BoolValueProperty();
  @NotNull private final BoolProperty myEnableCppSupport;
  @NotNull private final OptionalProperty<Project> myProject;
  @NotNull private final MultiTemplateRenderer myMultiTemplateRenderer;
  private final boolean myCreateInExistingProject;

  { // Default init constructor
    myModuleName.addConstraint(String::trim);
    mySplitName.addConstraint(String::trim);
  }

  public NewModuleModel(@NotNull Project project) {
    myProject = new OptionalValueProperty<>(project);
    myProjectPackageName = myPackageName;
    myCreateInExistingProject = true;
    myEnableCppSupport = new BoolValueProperty();

    myApplicationName = new StringValueProperty(message("android.wizard.module.config.new.application"));
    myApplicationName.addConstraint(String::trim);
    myIsLibrary.addListener(sender -> myApplicationName.set(
      message(myIsLibrary.get() ? "android.wizard.module.config.new.library" : "android.wizard.module.config.new.application")));

    myMultiTemplateRenderer = new MultiTemplateRenderer();
  }

  public NewModuleModel(@NotNull NewProjectModel projectModel, @NotNull File templateFile) {
    myProject = projectModel.project();
    myProjectPackageName = projectModel.packageName();
    myCreateInExistingProject = false;
    myEnableCppSupport = projectModel.enableCppSupport();
    myApplicationName = projectModel.applicationName();
    myTemplateFile.setValue(templateFile);
    myMultiTemplateRenderer = projectModel.getMultiTemplateRenderer();
    myMultiTemplateRenderer.increment();

    myBindings.bind(myPackageName, myProjectPackageName, myIsInstantApp.not());
  }

  @Override
  public void dispose() {
    super.dispose();
    myBindings.releaseAll();
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
  public StringProperty splitName() {
    return mySplitName;
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
  public BoolProperty instantApp() {
    return myIsInstantApp;
  }

  @NotNull
  public BoolProperty enableCppSupport() {
    return myEnableCppSupport;
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

  @NotNull
  public MultiTemplateRenderer getMultiTemplateRenderer() {
    return myMultiTemplateRenderer;
  }

  @NotNull
  public ObservableString computedFeatureModulePackageName() {
    return new StringExpression(myProjectPackageName, mySplitName) {

      @NotNull
      @Override
      public String get() {
        return myProjectPackageName.get() + "." + mySplitName.get();
      }
    };
  }

  /**
   * This method should be called if there is no "Activity Render Template" step (For example when creating a Library, or the activity
   * creation is skipped by the user)
   */
  public void setDefaultRenderTemplateValues(@NotNull RenderTemplateModel renderModel, @Nullable Project project) {
    Map<String, Object> renderTemplateValues = Maps.newHashMap();
    new TemplateValueInjector(renderTemplateValues)
      .setBuildVersion(renderModel.androidSdkInfo().getValue(), project)
      .setModuleRoots(renderModel.getSourceSet().get().getPaths(), packageName().get());

    getRenderTemplateValues().setValue(renderTemplateValues);
  }

  @Override
  protected void handleFinished() {
    myMultiTemplateRenderer.requestRender(new ModuleTemplateRenderer());
  }

  @Override
  protected void handleSkipped() {
    myMultiTemplateRenderer.skipRender();
  }

  private class ModuleTemplateRenderer implements MultiTemplateRenderer.TemplateRenderer {
    Map<String, Object> myTemplateValues;

    @Override
    public boolean doDryRun() {
      if (myTemplateFile.getValueOrNull() == null) {
        return false; // If here, the user opted to skip creating any module at all, or is just adding a new Activity
      }

      // By the time we run handleFinished(), we must have a Project
      if (!myProject.get().isPresent()) {
        getLog().error("NewModuleModel did not collect expected information and will not complete. Please report this error.");
        return false;
      }

      Map<String, Object> renderTemplateValues = myRenderTemplateValues.getValueOrNull();

      myTemplateValues = new HashMap<>(NewModuleModel.this.myTemplateValues);
      myTemplateValues.put(ATTR_IS_LIBRARY_MODULE, myIsLibrary.get());

      Project project = myProject.getValue();
      if (myIsInstantApp.get()) {
        myTemplateValues.put(ATTR_INSTANT_APP_PACKAGE_NAME, myProjectPackageName.get());

        if (renderTemplateValues != null) {
          renderTemplateValues.put(ATTR_IS_LIBRARY_MODULE, true);

          String projectPath = project.getBasePath();
          assert projectPath != null;
          String defaultResourceSuffix = join("src", "main", "res");
          File projectRoot = new File(projectPath);
          File baseModuleRoot = new File(projectRoot, "base");
          File baseModuleResourceRoot = new File(baseModuleRoot, defaultResourceSuffix);
          if (myCreateInExistingProject) {
            Module baseFeature = InstantApps.findBaseFeature(project);
            if (baseFeature == null) {
              baseModuleRoot = new File(projectRoot, myModuleName.get());
              baseModuleResourceRoot = new File(baseModuleRoot, defaultResourceSuffix);
              renderTemplateValues.put(ATTR_IS_BASE_FEATURE, true);
              String monolithicModuleName = InstantApps.findMonolithicModuleName(project);
              if (monolithicModuleName != null) {
                renderTemplateValues.put(ATTR_MONOLITHIC_MODULE_NAME, monolithicModuleName);
              }
            }
            else {
              AndroidModuleModel moduleModel = AndroidModuleModel.get(baseFeature);
              assert moduleModel != null;
              baseModuleRoot = moduleModel.getRootDirPath();
              Collection<File> resDirectories = moduleModel.getDefaultSourceProvider().getResDirectories();
              assert !resDirectories.isEmpty();
              baseModuleResourceRoot = resDirectories.iterator().next();
            }
          }

          new TemplateValueInjector(renderTemplateValues)
            .setInstantAppSupport();
          // TODO: Figure out a way to put more of the IAPP ATTRS inside TemplateValueInjector
          renderTemplateValues.put(ATTR_BASE_FEATURE_NAME, baseModuleRoot.getName());
          renderTemplateValues.put(ATTR_BASE_FEATURE_DIR, baseModuleRoot.getPath());
          renderTemplateValues.put(ATTR_BASE_FEATURE_RES_DIR, baseModuleResourceRoot.getPath());
        }

        if (myCreateInExistingProject) {
          myTemplateValues.put(ATTR_HAS_MONOLITHIC_APP_WRAPPER, false);
          myTemplateValues.put(ATTR_HAS_INSTANT_APP_WRAPPER, false);
        }
      }

      if (renderTemplateValues != null) {
        myTemplateValues.putAll(renderTemplateValues);
      }

      // returns false if there was a render conflict and the user chose to cancel creating the template
      return renderModule(true, myTemplateValues, project, myModuleName.get());
    }

    @Override
    public void render() {
      Project project = myProject.getValue();
      boolean success = new WriteCommandAction<Boolean>(project, "New Module") {
        @Override
        protected void run(@NotNull Result<Boolean> result) throws Throwable {
          result.setResult(renderModule(false, myTemplateValues, project, myModuleName.get()));
        }
      }.execute().getResultObject();

      if (!success) {
        getLog().warn("A problem occurred while creating a new Module. Please check the log file for possible errors.");
      }
    }

    private boolean renderModule(boolean dryRun, @NotNull Map<String, Object> templateState, @NotNull Project project,
                                 @NotNull String moduleName) {
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
        //.withPerformSync(myPerformSyncIfNecessary) // TODO: Check that we still need this
        //.intoTargetFiles(myState.get(TARGET_FILES_KEY))
        //.intoOpenFiles(myState.get(FILES_TO_OPEN_KEY))
        //.intoDependencies(myState.get(DEPENDENCIES_KEY))
        .build();
      // @formatter:on
      return template.render(context, dryRun);
    }
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(NewModuleModel.class);
  }
}
