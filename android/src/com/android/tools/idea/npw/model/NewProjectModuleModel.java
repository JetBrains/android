/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npw.model;

import com.android.SdkConstants;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.npw.template.TemplateValueInjector;
import com.android.tools.idea.observable.core.*;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.templates.*;
import com.android.tools.idea.wizard.model.WizardModel;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

import static com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt;
import static com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDummyTemplate;
import static com.android.tools.idea.templates.Template.CATEGORY_APPLICATION;
import static com.android.tools.idea.templates.TemplateManager.CATEGORY_ACTIVITY;
import static com.android.tools.idea.templates.TemplateMetadata.*;
import static org.jetbrains.android.util.AndroidBundle.message;

public final class NewProjectModuleModel extends WizardModel {
  public static final String EMPTY_ACTIVITY = "Empty Activity";
  public static final String ANDROID_MODULE = "Android Module";

  @NotNull private final NewProjectModel myProjectModel;
  @NotNull private final NewModuleModel myNewModuleModel;
  @NotNull private final RenderTemplateModel myExtraRenderTemplateModel;
  @NotNull private final ObjectProperty<FormFactor> myFormFactor = new ObjectValueProperty<>(FormFactor.MOBILE);
  @NotNull private final OptionalProperty<TemplateHandle> myRenderTemplateHandle = new OptionalValueProperty<>();

  @NotNull private final BoolProperty myHasCompanionApp = new BoolValueProperty();

  public NewProjectModuleModel(@NotNull NewProjectModel projectModel) {
    myProjectModel = projectModel;
    myNewModuleModel = new NewModuleModel(myProjectModel, new File(""), createDummyTemplate());
    myExtraRenderTemplateModel =
      RenderTemplateModel.fromModuleModel(myNewModuleModel, null, message("android.wizard.config.activity.title"));
  }

  @NotNull
  public OptionalProperty<AndroidVersionsInfo.VersionItem> androidSdkInfo() {
    return myNewModuleModel.getAndroidSdkInfo();
  }

  @NotNull
  public OptionalProperty<File> moduleTemplateFile() {
    return myNewModuleModel.getTemplateFile();
  }

  @NotNull
  public OptionalProperty<TemplateHandle> renderTemplateHandle() {
    return myRenderTemplateHandle;
  }

  @NotNull
  public BoolProperty hasCompanionApp() {
    return myHasCompanionApp;
  }

  @NotNull
  public ObjectProperty<FormFactor> formFactor() {
    return myFormFactor;
  }

  @NotNull
  public RenderTemplateModel getExtraRenderTemplateModel() {
    return myExtraRenderTemplateModel;
  }

  @Override
  protected void handleFinished() {
    myProjectModel.getNewModuleModels().clear();

    boolean hasCompanionApp = myHasCompanionApp.get();

    initMainModule();

    Map<String, Object> projectTemplateValues = myProjectModel.getTemplateValues();
    addModuleToProject(myNewModuleModel, myFormFactor.get(), myProjectModel, projectTemplateValues);

    if (hasCompanionApp) {
      NewModuleModel companionModuleModel = createCompanionModuleModel(myProjectModel);
      RenderTemplateModel companionRenderModel = createCompanionRenderModel(companionModuleModel);
      addModuleToProject(companionModuleModel, FormFactor.MOBILE, myProjectModel, projectTemplateValues);

      companionModuleModel.getAndroidSdkInfo().setValue(androidSdkInfo().getValue());
      companionModuleModel.setRenderTemplateModel(companionRenderModel);

      companionModuleModel.handleFinished();
      companionRenderModel.handleFinished();
    }

    RenderTemplateModel newRenderTemplateModel = createMainRenderModel();
    myNewModuleModel.setRenderTemplateModel(newRenderTemplateModel);

    boolean hasActivity = newRenderTemplateModel.getTemplateHandle() != null;
    if (hasActivity && newRenderTemplateModel != myExtraRenderTemplateModel) { // Extra render is driven by the Wizard itself
      addRenderDefaultTemplateValues(newRenderTemplateModel);
    }

    myNewModuleModel.handleFinished();
    if (newRenderTemplateModel != myExtraRenderTemplateModel) { // Extra render is driven by the Wizard itself
      if (hasActivity) {
        newRenderTemplateModel.handleFinished();
      }
      else {
        newRenderTemplateModel.handleSkipped(); // "No Activity" selected
      }
    }
  }

  private void initMainModule() {
    String moduleName;
    if (myHasCompanionApp.get()) {
      moduleName = getModuleName(myFormFactor.get());
    }
    else {
      moduleName = SdkConstants.APP_PREFIX;
    }

    String projectLocation = myProjectModel.projectLocation().get();

    myNewModuleModel.getModuleName().set(moduleName);
    myNewModuleModel.getTemplate().set(createDefaultTemplateAt(projectLocation, moduleName));
  }

  @NotNull
  private RenderTemplateModel createMainRenderModel() {
    RenderTemplateModel newRenderTemplateModel;
    if (myProjectModel.enableCppSupport().get()) {
      newRenderTemplateModel = createCompanionRenderModel(myNewModuleModel);
    }
    else if (myExtraRenderTemplateModel.getTemplateHandle() == null) {
      newRenderTemplateModel = RenderTemplateModel.fromModuleModel(myNewModuleModel, null, "");
      newRenderTemplateModel.setTemplateHandle(renderTemplateHandle().getValueOrNull());
    }
    else { // Extra Render is visible. Use it.
      newRenderTemplateModel = myExtraRenderTemplateModel;
    }
    return newRenderTemplateModel;
  }

  private static void addModuleToProject(@NotNull NewModuleModel moduleModel, @NotNull FormFactor formFactor,
                                         @NotNull NewProjectModel projectModel, @NotNull Map<String, Object> projectTemplateValues) {
    projectTemplateValues.put(formFactor.id + ATTR_INCLUDE_FORM_FACTOR, true);
    projectTemplateValues.put(formFactor.id + ATTR_MODULE_NAME, moduleModel.getModuleName().get());
    projectModel.getNewModuleModels().add(moduleModel);
  }

  @NotNull
  private static NewModuleModel createCompanionModuleModel(@NotNull NewProjectModel projectModel) {
    // Note: The companion Module is always a Mobile app
    File moduleTemplateFile = TemplateManager.getInstance().getTemplateFile(CATEGORY_APPLICATION, ANDROID_MODULE);
    String moduleName = getModuleName(FormFactor.MOBILE);
    NamedModuleTemplate namedModuleTemplate = createDefaultTemplateAt(projectModel.projectLocation().get(), moduleName);
    NewModuleModel companionModuleModel = new NewModuleModel(projectModel, moduleTemplateFile, namedModuleTemplate);
    companionModuleModel.getModuleName().set(moduleName);

    return companionModuleModel;
  }

  @NotNull
  private static RenderTemplateModel createCompanionRenderModel(@NotNull NewModuleModel moduleModel) {
    // Note: The companion Render is always a "Empty Activity"
    File renderTemplateFile = TemplateManager.getInstance().getTemplateFile(CATEGORY_ACTIVITY, EMPTY_ACTIVITY);
    TemplateHandle renderTemplateHandle = new TemplateHandle(renderTemplateFile);

    RenderTemplateModel companionRenderModel =
      RenderTemplateModel.fromModuleModel(moduleModel, renderTemplateHandle, "");
    addRenderDefaultTemplateValues(companionRenderModel);

    return companionRenderModel;
  }

  @NotNull
  private static String getModuleName(@NotNull FormFactor formFactor) {
    if (formFactor.baseFormFactor != null) {
      // Form factors like Android Auto build upon another form factor
      formFactor = formFactor.baseFormFactor;
    }
    return formFactor.id.replaceAll("\\s", "_").toLowerCase(Locale.US);
  }

  private static void addRenderDefaultTemplateValues(RenderTemplateModel renderTemplateModel) {
    Map<String, Object>  templateValues = renderTemplateModel.getTemplateValues();
    TemplateMetadata templateMetadata = renderTemplateModel.getTemplateHandle().getMetadata();
    Map<Parameter, Object> userValues = Maps.newHashMap();
    Map<String, Object>  additionalValues = Maps.newHashMap();

    String packageName = renderTemplateModel.getPackageName().get();
    new TemplateValueInjector(additionalValues).addTemplateAdditionalValues(packageName, renderTemplateModel.getTemplate());
    additionalValues.put(ATTR_PACKAGE_NAME, renderTemplateModel.getPackageName().get());

    try {
      Collection<Parameter> renderParameters = templateMetadata.getParameters();
      Map<Parameter, Object> parameterValues = ParameterValueResolver.Companion.resolve(renderParameters, userValues, additionalValues);
      parameterValues.forEach((parameter, value) -> templateValues.put(parameter.id, value));
    } catch (CircularParameterDependencyException e) {
      getLog().error("Circular dependency between parameters in template %1$s", e, templateMetadata.getTitle());
    }
  }

  private static Logger getLog() {
    return Logger.getInstance(NewProjectModuleModel.class);
  }
}
