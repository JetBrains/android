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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw.module;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.project.AndroidProjectPaths;
import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.android.tools.idea.npw.project.DomainToPackageExpression;
import com.android.tools.idea.npw.project.NewProjectModel;
import com.android.tools.idea.npw.template.ChooseActivityTypeStep;
import com.android.tools.idea.npw.template.RenderTemplateModel;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.adtui.LabelWithEditLink;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.ui.properties.expressions.Expression;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.android.tools.idea.ui.validation.Validator;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.List;

import static org.jetbrains.android.util.AndroidBundle.message;


public final class ConfigureAndroidModuleStep extends SkippableWizardStep<NewModuleModel> {
  private final StudioWizardStepPanel myRootPanel;
  private final ValidatorPanel myValidatorPanel;
  private final FormFactor myFormFactor;
  private final int myMinSdkLevel;
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();
  private FormFactorApiComboBox mySdkControls;
  private JTextField myModuleName;
  private JPanel myPanel;
  private JTextField myAppName;
  private LabelWithEditLink myPackageName;

  public ConfigureAndroidModuleStep(@NotNull NewModuleModel model, @NotNull FormFactor formFactor, int minSdkLevel,
                                    @NotNull String title) {
    super(model, title);

    myFormFactor = formFactor;
    myMinSdkLevel = minSdkLevel;

    StringProperty companyDomain = new StringValueProperty(NewProjectModel.getInitialDomain());
    TextProperty packageNameText = new TextProperty(myPackageName);
    TextProperty moduleNameText = new TextProperty(myModuleName);

    Expression<String> computedPackageName = new DomainToPackageExpression(companyDomain, model.applicationName());
    BoolProperty isPackageNameSynced = new BoolValueProperty(true);
    myBindings.bind(packageNameText, computedPackageName, isPackageNameSynced);
    myBindings.bind(model.packageName(), packageNameText);
    myListeners.receive(packageNameText, value -> isPackageNameSynced.set(value.equals(computedPackageName.get())));

    // Project should never be null (we are adding a new module to an existing project)
    Project project = getModel().getProject().getValue();

    Expression<String> computedModuleName = new AppNameToModuleNameExpression(project, model.applicationName());
    BoolProperty isModuleNameSynced = new BoolValueProperty(true);
    myBindings.bind(moduleNameText, computedModuleName, isModuleNameSynced);
    myBindings.bind(model.moduleName(), moduleNameText);
    myListeners.receive(moduleNameText, value -> isModuleNameSynced.set(value.equals(computedModuleName.get())));

    myBindings.bindTwoWay(new TextProperty(myAppName), model.applicationName());

    myValidatorPanel = new ValidatorPanel(this, myPanel);

    myValidatorPanel.registerValidator(model.applicationName(), value -> {
      if (value.isEmpty()) {
        return new Validator.Result(Validator.Severity.ERROR, message("android.wizard.validate.empty.application.name"));
      }
      else if (!model.isLibrary().get() && !Character.isUpperCase(value.charAt(0))) {
        return new Validator.Result(Validator.Severity.INFO,  message("android.wizard.validate.lowercase.application.name"));
      }
      return Validator.Result.OK;
    });

    Expression<File> locationFile = model.moduleName().transform((String str) -> new File(project.getBasePath(), str));
    myValidatorPanel.registerValidator(locationFile, PathValidator.createDefault("module location"));

    myValidatorPanel.registerValidator(model.packageName(), value -> {
      return Validator.Result.fromNullableMessage(WizardUtils.validatePackageName(value));
    });

    myRootPanel = new StudioWizardStepPanel(myValidatorPanel, message("android.wizard.module.config.title"));
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    List<TemplateHandle> templateList = TemplateManager.getInstance().getTemplateList(myFormFactor);

    AndroidSourceSet dummySourceSet = new AndroidSourceSet("main", new AndroidProjectPaths(new File("")));

    RenderTemplateModel renderModel =
      new RenderTemplateModel(getModel().getProject().getValue(), templateList.get(0), dummySourceSet,
                              message("android.wizard.activity.add"));

    // Some changes on this step model trigger changes on the renderModel
    myListeners.listenAll(getModel().getProject(), getModel().moduleName()).withAndFire(() -> {
      String moduleName = getModel().moduleName().get();
      Project project = getModel().getProject().getValue();

      renderModel.getSourceSet().set(new AndroidSourceSet("main", new AndroidProjectPaths(new File(project.getBasePath(), moduleName))));
    });

    ChooseActivityTypeStep chooseActivityTypeStep =
      new ChooseActivityTypeStep(renderModel, "todo.pakage.name", templateList, Lists.newArrayList());

    return Lists.newArrayList(chooseActivityTypeStep);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    AndroidVersionsInfo androidVersionsInfo = new AndroidVersionsInfo();
    androidVersionsInfo.load();

    androidVersionsInfo.loadTargetVersions(myFormFactor, myMinSdkLevel, items -> mySdkControls.init(myFormFactor, items));
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myAppName;
  }

  private void createUIComponents() {
    mySdkControls = new FormFactorApiComboBox();
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }
}
