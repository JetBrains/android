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

import com.android.repository.api.RemotePackage;
import com.android.repository.api.UpdatablePackage;
import com.android.sdklib.AndroidVersion.VersionCodes;
import com.android.tools.adtui.LabelWithEditButton;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.model.NewModuleModel;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.platform.Language;
import com.android.tools.idea.npw.template.components.LanguageComboProvider;
import com.android.tools.idea.npw.template.ChooseActivityTypeStep;
import com.android.tools.idea.npw.model.RenderTemplateModel;
import com.android.tools.idea.npw.validator.ModuleValidator;
import com.android.tools.idea.observable.core.*;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.wizard.InstallSelectedPackagesStep;
import com.android.tools.idea.sdk.wizard.LicenseAgreementModel;
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ContextHelpLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;
import org.jetbrains.annotations.TestOnly;

import static com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt;
import static com.android.tools.idea.npw.model.NewProjectModel.nameToJavaPackage;
import static com.android.tools.idea.npw.platform.AndroidVersionsInfoKt.getSdkManagerLocalPath;
import static com.android.tools.idea.templates.TemplateAttributes.ATTR_INCLUDE_FORM_FACTOR;
import static org.jetbrains.android.refactoring.MigrateToAndroidxUtil.isAndroidx;
import static org.jetbrains.android.util.AndroidBundle.message;


public class ConfigureAndroidModuleStep extends SkippableWizardStep<NewModuleModel> {
  private final AndroidVersionsInfo myAndroidVersionsInfo = new AndroidVersionsInfo();
  private final List<UpdatablePackage> myInstallRequests = new ArrayList<>();
  private final List<RemotePackage> myInstallLicenseRequests = new ArrayList<>();
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  @NotNull private final StudioWizardStepPanel myRootPanel;
  @NotNull private ValidatorPanel myValidatorPanel;
  @NotNull private final FormFactor myFormFactor;

  private final int myMinSdkLevel;

  private AndroidApiLevelComboBox myApiLevelCombo;
  private JComboBox<Language> myLanguageCombo;
  private JTextField myModuleName;
  private JPanel myPanel;
  private JTextField myAppName;
  private LabelWithEditButton myPackageName;
  private JLabel myModuleNameLabel;

  @NotNull private RenderTemplateModel myRenderModel;

  public ConfigureAndroidModuleStep(@NotNull NewModuleModel model, @NotNull FormFactor formFactor, int minSdkLevel, String basePackage,
                                    @NotNull String title) {
    super(model, title, formFactor.getIcon());

    myFormFactor = formFactor;
    myMinSdkLevel = minSdkLevel;

    TextProperty packageNameText = new TextProperty(myPackageName);
    TextProperty moduleNameText = new TextProperty(myModuleName);
    Expression<String> computedPackageName = new Expression<String>(model.getModuleName()) {
      @NotNull
      @Override
      public String get() {
        return String.format("%s.%s", basePackage, nameToJavaPackage(model.getModuleName().get()));
      }
    };
    BoolProperty isPackageNameSynced = new BoolValueProperty(true);
    myBindings.bind(packageNameText, computedPackageName, isPackageNameSynced);
    myBindings.bind(model.getPackageName(), packageNameText);
    myListeners.listen(packageNameText, value -> isPackageNameSynced.set(value.equals(computedPackageName.get())));

    // Project should never be null (we are adding a new module to an existing project)
    NewModuleModel moduleModel = getModel();
    Project project = moduleModel.getProject().getValue();

    Expression<String> computedModuleName = new AppNameToModuleNameExpression(project, model.getApplicationName(), model.getModuleParent());
    BoolProperty isModuleNameSynced = new BoolValueProperty(true);
    myBindings.bind(moduleNameText, computedModuleName, isModuleNameSynced);
    myBindings.bind(model.getModuleName(), moduleNameText);
    myListeners.listen(moduleNameText, value -> isModuleNameSynced.set(value.equals(computedModuleName.get())));

    myBindings.bindTwoWay(new TextProperty(myAppName), model.getApplicationName());

    myValidatorPanel = new ValidatorPanel(this, myPanel);

    myValidatorPanel.registerValidator(model.getApplicationName(), value -> {
      if (value.isEmpty()) {
        return new Validator.Result(Validator.Severity.ERROR, message("android.wizard.validate.empty.application.name"));
      }
      else if (!model.isLibrary().get() && !Character.isUpperCase(value.charAt(0))) {
        return new Validator.Result(Validator.Severity.INFO,  message("android.wizard.validate.lowercase.application.name"));
      }
      return Validator.Result.OK;
    });

    myValidatorPanel.registerValidator(model.getModuleName(), new ModuleValidator(project));
    myValidatorPanel.registerValidator(model.getPackageName(),
                                       value -> Validator.Result.fromNullableMessage(WizardUtils.validatePackageName(value)));

    myRenderModel =
      RenderTemplateModel.fromModuleModel(moduleModel, null, message("android.wizard.activity.add", myFormFactor.id));

    myBindings.bind(model.getAndroidSdkInfo(), new SelectedItemProperty<>(myApiLevelCombo));
    myValidatorPanel.registerValidator(model.getAndroidSdkInfo(), value -> {
      if (!value.isPresent()) {
        return new Validator.Result(Validator.Severity.ERROR, message("select.target.dialog.text"));
      }

      if ((value.get().getMinApiLevel() >= VersionCodes.Q || myFormFactor == FormFactor.WEAR) && !isAndroidx(project)) {
        return new Validator.Result(Validator.Severity.ERROR, message("android.wizard.validate.module.needs.androidx"));
      }

      return Validator.Result.OK;
    });

    myBindings.bindTwoWay(new SelectedItemProperty<>(myLanguageCombo), getModel().getLanguage());

    myRootPanel = new StudioWizardStepPanel(myValidatorPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    // Note: MultiTemplateRenderer needs that all Models constructed (ie myRenderModel) are inside a Step, so handleSkipped() is called
    ChooseActivityTypeStep chooseActivityStep = new ChooseActivityTypeStep(getModel(), myRenderModel, myFormFactor, Lists.newArrayList());
    chooseActivityStep.setShouldShow(!getModel().isLibrary().get());

    LicenseAgreementStep licenseAgreementStep =
      new LicenseAgreementStep(new LicenseAgreementModel(getSdkManagerLocalPath()), myInstallLicenseRequests);

    InstallSelectedPackagesStep installPackagesStep =
      new InstallSelectedPackagesStep(myInstallRequests, new HashSet<>(), AndroidSdks.getInstance().tryToChooseSdkHandler(), false);

    return Lists.newArrayList(chooseActivityStep, licenseAgreementStep, installPackagesStep);
  }

  @Override
  protected void onEntering() {
    // TODO: The old version only loaded the list of version once, and kept everything on a static field
    // Possible solutions: Move AndroidVersionsInfo/load to the class that instantiates this step?
    myAndroidVersionsInfo.loadLocalVersions();
    myApiLevelCombo.init(myFormFactor, myAndroidVersionsInfo.getKnownTargetVersions(myFormFactor, myMinSdkLevel)); // Pre-populate
    myAndroidVersionsInfo.loadRemoteTargetVersions(myFormFactor, myMinSdkLevel, items -> myApiLevelCombo.init(myFormFactor, items));
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    NewModuleModel moduleModel = getModel();
    moduleModel.getModuleTemplateValues().put(myFormFactor.id + ATTR_INCLUDE_FORM_FACTOR, true);

    // At this point, the validator panel should have no errors, and the user has typed a valid Module Name
    getModel().getModuleName().set(myModuleName.getText());
    Project project = moduleModel.getProject().getValue();
    getModel().getTemplate().set(createDefaultTemplateAt(project.getBasePath(), moduleModel.getModuleName().get()));

    myInstallRequests.clear();
    myInstallLicenseRequests.clear();

    List<AndroidVersionsInfo.VersionItem> installItems = Collections.singletonList(moduleModel.getAndroidSdkInfo().getValue());
    myInstallRequests.addAll(myAndroidVersionsInfo.loadInstallPackageList(installItems));
    myInstallLicenseRequests.addAll(myInstallRequests.stream().map(UpdatablePackage::getRemote).collect(Collectors.toList()));
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

  @TestOnly
  @NotNull
  RenderTemplateModel getRenderModel() {
    return myRenderModel;
  }

  private void createUIComponents() {
    myApiLevelCombo = new AndroidApiLevelComboBox();
    myLanguageCombo = new LanguageComboProvider().createComponent();
    myModuleNameLabel = ContextHelpLabel.create(message("android.wizard.module.help.name"));
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }
}
