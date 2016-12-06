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
import com.android.tools.adtui.TabularLayout;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.module.NewModuleModel;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.template.ChooseActivityTypeStep;
import com.android.tools.idea.npw.template.RenderTemplateModel;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.core.ObjectValueProperty;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import com.android.tools.idea.ui.properties.swing.SelectedItemProperty;
import com.android.tools.idea.ui.properties.swing.SelectedProperty;
import com.android.tools.idea.ui.validation.Validator;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jetbrains.android.util.AndroidBundle.message;

/**
 * ConfigureAndroidModuleStep is the first page in the New Project wizard that sets project/module name, location, and other project-global
 * parameters.
 */
public class ConfigureFormFactorStep extends ModelWizardStep<NewProjectModel> {
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
    myValidatorPanel.registerValidator(myInvalidParameterMessage, message ->
      (message.isEmpty() ? Validator.Result.OK : new Validator.Result(Validator.Severity.ERROR, message)));

    myRootPanel = new StudioWizardStepPanel(myValidatorPanel, message("android.wizard.project.select.form"));
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

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    List<ModelWizardStep> allSteps = Lists.newArrayList();

    Set<FormFactor> formFactors = myFormFactors.keySet();
    for (final FormFactor formFactor : formFactors) {
      FormFactorInfo formFactorInfo = myFormFactors.get(formFactor);

      List<TemplateHandle> templateList = TemplateManager.getInstance().getTemplateList(formFactor);
      AndroidSourceSet dummySourceSet = new AndroidSourceSet("main", new AndroidProjectPaths(new File("")));

      NewModuleModel moduleModel = new NewModuleModel(getModel(), formFactorInfo.templateFile);
      RenderTemplateModel renderModel = new RenderTemplateModel(moduleModel, templateList.get(0), dummySourceSet,
                                                                message("android.wizard.activity.add"));

      moduleModel.getRenderTemplateValues().setValue(renderModel.getTemplateValues());
      formFactorInfo.newModuleModel = moduleModel;

      formFactorInfo.step = new ChooseActivityTypeStep(moduleModel, renderModel, templateList, Lists.newArrayList());
      allSteps.add(formFactorInfo.step);

      FormFactorSdkControls controls = formFactorInfo.controls;
      myBindings.bindTwoWay(new SelectedProperty(controls.getInstantAppCheckbox()), moduleModel.isInstAppEnabled());

      SelectedItemProperty<AndroidVersionsInfo.VersionItem> minSdk = new SelectedItemProperty<>(controls.getMinSdkCombobox());
      myListeners.receiveAndFire(minSdk, value ->  checkValidity());
      myBindings.bindTwoWay(minSdk, renderModel.androidSdkInfo());

      // Some changes on the ProjectModel trigger changes on the ModuleModel/RenderModel
      myListeners.listenAll(getModel().projectLocation(), myEnabledFormFactors).withAndFire(() -> {
        String moduleName = myEnabledFormFactors.get() <= 1 ? SdkConstants.APP_PREFIX : getModuleName(formFactor);
        String projectPath = getModel().projectLocation().get();

        moduleModel.moduleName().set(moduleName);
        renderModel.getSourceSet().set(new AndroidSourceSet("main", new AndroidProjectPaths(new File(projectPath, moduleName))));
      });
    }

    return allSteps;
  }

  @Override
  protected void onProceeding() {
    Set<NewModuleModel> newModuleModels = getModel().getNewModuleModels();
    newModuleModels.clear();

    for (FormFactorInfo formFactorInfo : myFormFactors.values()) {
      if (formFactorInfo.controls.getInclusionCheckBox().isSelected()) {
        newModuleModels.add(formFactorInfo.newModuleModel);
      }
    }

    assert newModuleModels.size() > 0;
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

    AndroidVersionsInfo androidVersionsInfo = new AndroidVersionsInfo();
    androidVersionsInfo.load();

    Set<FormFactor> formFactors = myFormFactors.keySet();
    AtomicInteger loadingCounter = new AtomicInteger(formFactors.size());
    int row = 0;
    for (final FormFactor formFactor : formFactors) {
      FormFactorInfo formFactorInfo = myFormFactors.get(formFactor);
      FormFactorSdkControls controls = new FormFactorSdkControls(this, formFactor);
      formFactorInfo.controls = controls;
      myFormFactorPanel.add(controls.getComponent(), new TabularLayout.Constraint(row, 0));
      row++;

      androidVersionsInfo.loadTargetVersions(formFactor, formFactorInfo.minSdk, items -> {
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
    ChooseActivityTypeStep step;

    FormFactorInfo(File templateFile, int minSdk) {
      this.templateFile = templateFile;
      this.minSdk = minSdk;
    }
  }
}
