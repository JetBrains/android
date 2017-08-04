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
package com.android.tools.idea.npw.project;

import com.android.SdkConstants;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.UpdatablePackage;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.gradle.npw.project.GradleAndroidProjectPaths;
import com.android.tools.idea.instantapp.InstantApps;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.instantapp.ConfigureInstantModuleStep;
import com.android.tools.idea.npw.module.NewModuleModel;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.template.ChooseActivityTypeStep;
import com.android.tools.idea.npw.template.RenderTemplateModel;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.wizard.InstallSelectedPackagesStep;
import com.android.tools.idea.sdk.wizard.LicenseAgreementModel;
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.android.tools.idea.templates.TemplateMetadata.ATTR_INCLUDE_FORM_FACTOR;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MODULE_NAME;
import static org.jetbrains.android.util.AndroidBundle.message;

/**
 * ConfigureAndroidModuleStep is the first page in the New Project wizard that sets project/module name, location, and other project-global
 * parameters.
 */
public class ConfigureFormFactorStep extends ModelWizardStep<NewProjectModel> {
  private final AndroidVersionsInfo myAndroidVersionsInfo = new AndroidVersionsInfo();
  private final List<UpdatablePackage> myInstallRequests = new ArrayList<>();
  private final List<RemotePackage> myInstallLicenseRequests = new ArrayList<>();
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();
  private final ObjectValueProperty<Integer> myEnabledFormFactors = new ObjectValueProperty<>(0);
  private final Map<FormFactor, FormFactorInfo> myFormFactors = Maps.newTreeMap();
  private final StudioWizardStepPanel myRootPanel;
  private final ValidatorPanel myValidatorPanel;
  private final StringProperty myInvalidParameterMessage = new StringValueProperty();

  private JPanel myPanel;
  private JPanel myFormFactorPanel;
  private JPanel myLoadingPanel;

  public ConfigureFormFactorStep(@NotNull NewProjectModel model) {
    super(model, message("android.wizard.project.target"));

    populateAdditionalFormFactors();

    myValidatorPanel = new ValidatorPanel(this, myPanel);
    myValidatorPanel.registerMessageSource(myInvalidParameterMessage);

    myRootPanel = new StudioWizardStepPanel(myValidatorPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myFormFactorPanel;
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    List<ModelWizardStep> allSteps = Lists.newArrayList();

    Set<FormFactor> formFactors = myFormFactors.keySet();
    for (final FormFactor formFactor : formFactors) {
      FormFactorInfo formFactorInfo = myFormFactors.get(formFactor);

      AndroidSourceSet dummySourceSet = GradleAndroidProjectPaths.createDummySourceSet();

      NewModuleModel moduleModel = new NewModuleModel(getModel(), formFactorInfo.templateFile);
      RenderTemplateModel renderModel = new RenderTemplateModel(moduleModel, null, dummySourceSet,
                                                                message("android.wizard.activity.add", formFactor.id));

      moduleModel.getRenderTemplateValues().setValue(renderModel.getTemplateValues());
      formFactorInfo.newModuleModel = moduleModel;
      formFactorInfo.newRenderModel = renderModel;

      if (formFactorInfo.controls.getInstantAppCheckbox().isVisible()) {
        allSteps.add(new ConfigureInstantModuleStep(moduleModel, getModel().projectLocation()));
        myListeners.receive(moduleModel.instantApp(), value -> checkValidity());
      }
      formFactorInfo.step = new ChooseActivityTypeStep(moduleModel, renderModel, formFactor, Lists.newArrayList());
      allSteps.add(formFactorInfo.step);

      FormFactorSdkControls controls = formFactorInfo.controls;
      myBindings.bindTwoWay(new SelectedProperty(controls.getInstantAppCheckbox()), moduleModel.instantApp());

      myListeners.receive(renderModel.androidSdkInfo(), value -> checkValidity());
      myBindings.bindTwoWay(renderModel.androidSdkInfo(), new SelectedItemProperty<>(controls.getMinSdkCombobox()));

      myListeners.listenAll(myEnabledFormFactors, moduleModel.instantApp()).withAndFire(() -> {
        String moduleName = myEnabledFormFactors.get() <= 1 ? SdkConstants.APP_PREFIX : getModuleName(formFactor);
        moduleModel.moduleName().set(moduleName);
      });

      // Some changes on the Project/Module Model trigger changes on the Render Model
      myListeners.listenAll(getModel().projectLocation(), moduleModel.moduleName()).withAndFire(() -> {
        File moduleRoot = new File(getModel().projectLocation().get(), moduleModel.moduleName().get());
        renderModel.getSourceSet().set(GradleAndroidProjectPaths.createDefaultSourceSetAt(moduleRoot));
      });
    }

    allSteps.add(new LicenseAgreementStep(new LicenseAgreementModel(AndroidVersionsInfo.getSdkManagerLocalPath()), myInstallLicenseRequests));
    allSteps.add(new InstallSelectedPackagesStep(myInstallRequests, new HashSet<>(), AndroidSdks.getInstance().tryToChooseSdkHandler(), false));

    return allSteps;
  }

  @Override
  protected void onProceeding() {
    Map<String, Object> projectTemplateValues = getModel().getTemplateValues();
    projectTemplateValues.put("NumberOfEnabledFormFactors", myEnabledFormFactors.get());

    myFormFactors.forEach(((formFactor, formFactorInfo) -> {
      projectTemplateValues.put(formFactor.id + ATTR_INCLUDE_FORM_FACTOR, formFactorInfo.controls.getInclusionCheckBox().isSelected());
      projectTemplateValues.put(formFactor.id + ATTR_MODULE_NAME, formFactorInfo.newModuleModel.moduleName().get());
    }));

    Set<NewModuleModel> newModuleModels = getModel().getNewModuleModels();
    newModuleModels.clear();

    List<AndroidVersionsInfo.VersionItem> installItems = new ArrayList<>();
    for (FormFactorInfo formFactorInfo : myFormFactors.values()) {
      if (formFactorInfo.controls.getInclusionCheckBox().isSelected()) {
        newModuleModels.add(formFactorInfo.newModuleModel);
        installItems.add(formFactorInfo.getAndroidSdkInfo());
      }
    }

    assert !newModuleModels.isEmpty();

    myInstallRequests.clear();
    myInstallLicenseRequests.clear();

    myInstallRequests.addAll(myAndroidVersionsInfo.loadInstallPackageList(installItems));
    myInstallLicenseRequests.addAll(myInstallRequests.stream().map(UpdatablePackage::getRemote).collect(Collectors.toList()));
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    for (FormFactorInfo formFactorInfo : myFormFactors.values()) {
      myListeners.receiveAndFire(new SelectedProperty(formFactorInfo.controls.getInclusionCheckBox()),
                                 value -> updateStepVisibility(wizard, formFactorInfo.step, value));
    }
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  private void updateStepVisibility(@NotNull ModelWizard.Facade wizardFacade, @NotNull ChooseActivityTypeStep step, boolean isVisible) {
    step.setShouldShow(isVisible);

    int enabledFormFactors = 0;
    for (FormFactorInfo formFactorInfo : myFormFactors.values()) {
      if (formFactorInfo.controls.getInclusionCheckBox().isSelected()) {
        enabledFormFactors++;
      }
    }
    myEnabledFormFactors.set(enabledFormFactors);

    checkValidity();
    wizardFacade.updateNavigationProperties();
  }

  private void checkValidity() {
    String message = "";
    if (myEnabledFormFactors.get() == 0) {
      message = message("android.wizard.project.no.selected.form");
    }
    else {
      for (FormFactor formFactor : myFormFactors.keySet()) {
        FormFactorInfo formFactorInfo = myFormFactors.get(formFactor);
        if (!formFactorInfo.controls.getInclusionCheckBox().isSelected()) {
          continue;
        }

        int minTargetSdk = formFactorInfo.getMinTargetSdk();
        FormFactor baseFormFactor = formFactor.baseFormFactor;
        if (baseFormFactor != null) {
          FormFactorInfo baseFormFactorInfo = myFormFactors.get(baseFormFactor);
          if (baseFormFactorInfo == null || !baseFormFactorInfo.controls.getInclusionCheckBox().isSelected()) {
            message = message("android.wizard.project.missing.form.factor", formFactor, baseFormFactor);
            break;
          }
          // Check if min target SDK of the base is valid:
          if (minTargetSdk > baseFormFactorInfo.getMinTargetSdk()) {
            message = message("android.wizard.project.invalid.base.min.sdk", minTargetSdk, baseFormFactor, formFactor);
            break;
          }
        }

        if (formFactorInfo.newModuleModel.instantApp().get() && minTargetSdk < InstantApps.getMinTargetSdk()) {
          message = message("android.wizard.project.invalid.iapp.min.sdk", formFactor, InstantApps.getMinTargetSdk());
          break;
        }
      }
    }

    myInvalidParameterMessage.set(message);
  }

  private void createUIComponents() {
    myLoadingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    AsyncProcessIcon refreshIcon = new AsyncProcessIcon(message("android.wizard.module.help.loading"));
    JLabel refreshingLabel = new JLabel(message("android.wizard.project.loading.sdks"));
    refreshingLabel.setForeground(JBColor.GRAY);
    myLoadingPanel.add(refreshIcon);
    myLoadingPanel.add(refreshingLabel);
  }

  private void populateAdditionalFormFactors() {
    TemplateManager manager = TemplateManager.getInstance();
    List<File> applicationTemplates = manager.getTemplatesInCategory(Template.CATEGORY_APPLICATION);

    for (File templateFile : applicationTemplates) {
      TemplateMetadata metadata = manager.getTemplateMetadata(templateFile);
      if (metadata == null || metadata.getFormFactor() == null) {
        continue;
      }
      FormFactor formFactor = FormFactor.get(metadata.getFormFactor());
      if (formFactor == FormFactor.GLASS && !AndroidSdkUtils.isGlassInstalled()) {
        // Only show Glass if you've already installed the SDK
        continue;
      }
      FormFactorInfo prevFormFactorInfo = myFormFactors.get(formFactor);
      int templateMinSdk = metadata.getMinSdk();

      if (prevFormFactorInfo == null) {
        myFormFactors.put(formFactor, new FormFactorInfo(templateFile, Math.max(templateMinSdk, formFactor.getMinOfflineApiLevel())));
      }
      else if (templateMinSdk > prevFormFactorInfo.minSdk) {
        prevFormFactorInfo.minSdk = templateMinSdk;
        prevFormFactorInfo.templateFile = templateFile;
      }
    }

    // One row for each form factor
    myFormFactorPanel.setLayout(new TabularLayout("*").setVGap(5));

    myAndroidVersionsInfo.load();

    Set<FormFactor> formFactors = myFormFactors.keySet();
    AtomicInteger loadingCounter = new AtomicInteger(formFactors.size());
    int row = 0;
    for (final FormFactor formFactor : formFactors) {
      FormFactorInfo formFactorInfo = myFormFactors.get(formFactor);
      FormFactorSdkControls controls = new FormFactorSdkControls(this, formFactor);
      formFactorInfo.controls = controls;
      myFormFactorPanel.add(controls.getComponent(), new TabularLayout.Constraint(row, 0));
      row++;

      myAndroidVersionsInfo.loadTargetVersions(formFactor, formFactorInfo.minSdk, items -> {
        controls.init(items);
        controls.getInclusionCheckBox().setSelected(FormFactor.MOBILE.equals(formFactor));
        if (loadingCounter.decrementAndGet() == 0) {
          myLoadingPanel.setVisible(false);
        }
      });
    }
  }

  @NotNull
  private static String getModuleName(@NotNull FormFactor formFactor) {
    if (formFactor.baseFormFactor != null) {
      // Form factors like Android Auto build upon another form factor
      formFactor = formFactor.baseFormFactor;
    }
    String name = formFactor.id;
    return name.replaceAll("\\s", "_").toLowerCase(Locale.US);
  }

  private static class FormFactorInfo {
    File templateFile;
    int minSdk;
    FormFactorSdkControls controls;
    NewModuleModel newModuleModel;
    RenderTemplateModel newRenderModel;
    ChooseActivityTypeStep step;

    FormFactorInfo(File templateFile, int minSdk) {
      this.templateFile = templateFile;
      this.minSdk = minSdk;
    }

    int getMinTargetSdk() {
      AndroidVersionsInfo.VersionItem androidVersion = newRenderModel.androidSdkInfo().getValueOrNull();
      return androidVersion == null ? 0 : androidVersion.getApiLevel();
    }

    @NotNull
    AndroidVersionsInfo.VersionItem getAndroidSdkInfo() {
      return newRenderModel.androidSdkInfo().getValue();
    }
  }
}
