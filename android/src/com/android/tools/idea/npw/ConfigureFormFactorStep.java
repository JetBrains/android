/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithHeaderAndDescription;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.Disposable;
import com.intellij.ui.JBColor;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.npw.FormFactorApiComboBox.AndroidTargetComboBoxItem;
import static com.android.tools.idea.npw.FormFactorUtils.*;
import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.WizardConstants.NEWLY_INSTALLED_API_KEY;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.createKey;

/**
 * ConfigureAndroidModuleStep is the first page in the New Project wizard that sets project/module name, location, and other project-global
 * parameters.
 */
public class ConfigureFormFactorStep extends DynamicWizardStepWithHeaderAndDescription {
  public static final String MIN_SDK_STRING = "Minimum SDK";
  public static final Key<Integer> NUM_ENABLED_FORM_FACTORS_KEY = createKey("NumberOfEnabledFormFactors", WIZARD, Integer.class);

  private JPanel myPanel;
  private JPanel myFormFactorPanel;
  private JPanel myLoadingPanel;
  private final List<FormFactorSdkControls> myControls = Lists.newArrayList();

  private Map<FormFactor, Integer> myFormFactors = Maps.newTreeMap();
  private ChooseApiLevelDialog myChooseApiLevelDialog = new ChooseApiLevelDialog(null, -1);
  private Disposable myDisposable;
  private Map<FormFactor, FormFactorSdkControls> myFormFactorApiSelectors = Maps.newHashMap();

  public ConfigureFormFactorStep(@NotNull Disposable disposable) {
    super("Select the form factors your app will run on", "Different platforms may require separate SDKs", disposable);
    myDisposable = disposable;
    setBodyComponent(myPanel);
    populateAdditionalFormFactors();
  }

  @Override
  public void init() {
    super.init();
    final Set<FormFactorSdkControls> pendingControls = Sets.newHashSet(myControls);

    for (final FormFactorSdkControls controls : myControls) {
      controls.init(myState, new Runnable() {
        @Override
        public void run() {
          pendingControls.remove(controls);
          if (pendingControls.isEmpty()) {
            myLoadingPanel.setVisible(false);
          }
        }
      });
    }
    myState.put(myControls.get(0).getInclusionKey(), true);
  }

  private void populateAdditionalFormFactors() {
    TemplateManager manager = TemplateManager.getInstance();
    List<File> applicationTemplates = manager.getTemplatesInCategory(Template.CATEGORY_APPLICATION);
    myFormFactors.clear();
    myFormFactorPanel.removeAll();

    int row = 0;
    for (File templateFile : applicationTemplates) {
      TemplateMetadata metadata = manager.getTemplate(templateFile);
      if (metadata == null || metadata.getFormFactor() == null) {
        continue;
      }
      FormFactor formFactor = FormFactor.get(metadata.getFormFactor());
      if (formFactor == null) {
        continue;
      }
      Integer prevMinSdk = myFormFactors.get(formFactor);
      int templateMinSdk = metadata.getMinSdk();
      if (prevMinSdk == null || templateMinSdk > prevMinSdk) {
        myFormFactors.put(formFactor, templateMinSdk);
      }
    }

    // One row for each form factor
    GridLayoutManager gridLayoutManager = new GridLayoutManager(myFormFactors.size(), 1);
    gridLayoutManager.setVGap(5);
    gridLayoutManager.setHGap(10);
    myFormFactorPanel.setLayout(gridLayoutManager);
    for (final FormFactor formFactor : myFormFactors.keySet()) {
      GridConstraints c = new GridConstraints();
      c.setRow(row);
      c.setColumn(0);
      c.setFill(GridConstraints.FILL_HORIZONTAL);
      c.setAnchor(GridConstraints.ANCHOR_WEST);
      FormFactorSdkControls controls = new FormFactorSdkControls(formFactor, myFormFactors.get(formFactor), myDisposable, this);
      myControls.add(controls);
      myFormFactorPanel.add(controls.getComponent(), c);
      myFormFactorApiSelectors.put(formFactor, controls);
      row++;
    }
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    if (myState.containsKey(NEWLY_INSTALLED_API_KEY)) {
      FormFactorApiComboBox.loadInstalledVersions();
    }
  }

  @Override
  public void deriveValues(Set<Key> modified) {
    super.deriveValues(modified);
    // Persist the min API level choices on a per-form factor basis
    int enabledFormFactors = 0;
    for (FormFactor formFactor : myFormFactors.keySet()) {
      Boolean included = myState.get(getInclusionKey(formFactor));
      // Disable api selection for non-enabled form factors and check to see if only one is selected
      if (included != null && included) {
        enabledFormFactors++;
        FormFactorSdkControls controls = myFormFactorApiSelectors.get(formFactor);
        if (controls != null) {
          controls.deriveValues(myState, modified);
        }
      }
    }
    myState.put(NUM_ENABLED_FORM_FACTORS_KEY, enabledFormFactors);
  }

  @Override
  public boolean validate() {
    setErrorHtml("");
    Integer enabledFormFactors = myState.get(NUM_ENABLED_FORM_FACTORS_KEY);
    if (enabledFormFactors == null || enabledFormFactors < 1) {
      // Don't allow an empty project
      setErrorHtml("At least one form factor must be selected.");
      return false;
    }
    for (FormFactor formFactor : myFormFactors.keySet()) {
      Boolean included = myState.get(getInclusionKey(formFactor));
      // Disable api selection for non-enabled form factors and check to see if only one is selected
      if (included != null && included) {
        if (!isBaseEnabled(formFactor)) {
          return false;
        }
        if (formFactor.baseFormFactor == null && myState.get(getMinApiKey(formFactor)) == null) {
          // Don't allow the user to continue unless all minAPIs of base form factors are chosen
          setErrorHtml(formFactor + " must have a Minimum SDK level selected.");
          return false;
        }
      }
    }
    return true;
  }

  private boolean isBaseEnabled(FormFactor formFactor) {
    if (formFactor.baseFormFactor == null) {
      return true;
    }
    Boolean isBaseEnabled = myState.get(getInclusionKey(formFactor.baseFormFactor));
    if (isBaseEnabled == null || !isBaseEnabled) {
      setErrorHtml("In order to support " + formFactor + " you need to enable " + formFactor.baseFormFactor);
      return false;
    }
    // Check if minSDK of the base is valid:
    AndroidTargetComboBoxItem baseMinSdk = myState.get(getTargetComboBoxKey(formFactor.baseFormFactor));
    if (!FormFactorUtils.getMinSdkComboBoxFilter(formFactor, myFormFactors.get(formFactor)).apply(baseMinSdk)) {
      // Don't allow the user to continue unless all minAPIs of base form factors are chosen
      // TODO: add valid minimum SDK levels to the error message.
      setErrorHtml("Set a minimum SDK level on " + formFactor.baseFormFactor + " that is compatible with " + formFactor);
      return false;
    }
    return true;
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Configure Form Factors";
  }

  @Nullable
  public String getHelpText(@NotNull String param) {
    if (param.equals(ATTR_MIN_API)) {
      return "Choose the lowest version of Android that your application will support. Lower API levels target more devices, " +
             "but means fewer features are available. By targeting API 10 and later, you reach approximately 99% of the market.";
    } else if (param.equals(ATTR_TARGET_API)) {
      return "Choose the highest API level that the application is known to work with. This attribute informs the system that you have " +
             "tested against the target version and the system should not enable any compatibility behaviors to maintain your app's " +
             "forward-compatibility with the target version. The application is still able to run on older versions (down to " +
             "minSdkVersion). Your application may look dated if you are not targeting the current version.";
    } else if (param.equals(ATTR_BUILD_API)) {
      return "Choose a target API to compile your code against, from your installed SDKs. This is typically the most recent version, " +
             "or the first version that supports all the APIs you want to directly access without reflection.";
    } else {
      return null;
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel;
  }

  @NotNull
  @Override
  protected WizardStepHeaderSettings getStepHeader() {
    return WizardStepHeaderSettings.createTitleOnlyHeader("Target Android Devices");
  }

  private void createUIComponents() {
    myLoadingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    AsyncProcessIcon refreshIcon = new AsyncProcessIcon("loading");
    JLabel refreshingLabel = new JLabel("Looking for SDKs available for download...");
    refreshingLabel.setForeground(JBColor.GRAY);
    myLoadingPanel.add(refreshIcon);
    myLoadingPanel.add(refreshingLabel);
  }
}
