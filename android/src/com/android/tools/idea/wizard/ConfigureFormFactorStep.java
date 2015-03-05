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
package com.android.tools.idea.wizard;

import com.android.tools.idea.stats.DistributionService;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.FormFactorApiComboBox.AndroidTargetComboBoxItem;
import static com.android.tools.idea.wizard.FormFactorUtils.*;
import static com.android.tools.idea.wizard.FormFactorUtils.FormFactor.MOBILE;
import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.STEP;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;
import static com.android.tools.idea.wizard.WizardConstants.NEWLY_INSTALLED_API_KEY;

/**
 * ConfigureAndroidModuleStep is the first page in the New Project wizard that sets project/module name, location, and other project-global
 * parameters.
 */
public class ConfigureFormFactorStep extends DynamicWizardStepWithHeaderAndDescription {
  public static final String MIN_SDK_STRING = "Minimum SDK";
  private static final Key<String> API_FEEDBACK_KEY = createKey("API Feedback", STEP, String.class);
  public static final Key<Integer> NUM_ENABLED_FORM_FACTORS_KEY = createKey("NumberOfEnabledFormFactors", WIZARD, Integer.class);

  private JPanel myPanel;
  private JPanel myFormFactorPanel;
  private JBLabel myHelpMeChooseLabel = new JBLabel(getApiHelpText(0)) {
    @Override
    public Dimension getPreferredSize() {
      // Since this contains auto-wrapped text, the preferred height will not be set until repaint(). The below will set it as soon
      // as the actual width is known. This allows the wizard dialog to be set to the correct size even before this step is shown.
      final View view = (View)myHelpMeChooseLabel.getClientProperty("html");
      if (getWidth() > 0) {
        view.setSize(getWidth(), 0);
        setPreferredSize(new Dimension((int)view.getPreferredSpan(View.X_AXIS), (int)view.getPreferredSpan(View.Y_AXIS)));
      }
      return super.getPreferredSize();
    }
  };
  private List<Pair<Key<Boolean>, JCheckBox>> myCheckboxKeys = Lists.newArrayList();

  private List<FormFactor> myFormFactors = Lists.newArrayList();
  private ChooseApiLevelDialog myChooseApiLevelDialog = new ChooseApiLevelDialog(null, -1);
  private Disposable myDisposable;
  private Map<FormFactor, FormFactorSdkControls> myFormFactorApiSelectors = Maps.newHashMap();

  public ConfigureFormFactorStep(@NotNull Disposable disposable) {
    super("Select the form factors your app will run on", "Different platforms require separate SDKs", disposable);
    myDisposable = disposable;
    Disposer.register(disposable, myChooseApiLevelDialog.getDisposable());
    setBodyComponent(myPanel);
    populateAdditionalFormFactors();
  }

  @Override
  public void init() {
    super.init();

    myHelpMeChooseLabel.addMouseListener(new MouseInputAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        myChooseApiLevelDialog = new ChooseApiLevelDialog(null, myState.get(getMinApiLevelKey(MOBILE)));
        Disposer.register(myDisposable, myChooseApiLevelDialog.getDisposable());
        if (myChooseApiLevelDialog.showAndGet()) {
          int minApiLevel = myChooseApiLevelDialog.getSelectedApiLevel();
          myFormFactorApiSelectors.get(MOBILE).setSelectedItem(Integer.toString(minApiLevel));
        }
      }
    });
    register(API_FEEDBACK_KEY, myHelpMeChooseLabel, new ComponentBinding<String, JBLabel>() {
      @Override
      public void setValue(@Nullable String newValue, @NotNull JBLabel label) {
        final JBLabel referenceLabel = label;
        final String referenceString = newValue;
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            referenceLabel.setText(referenceString);
          }
        });
      }
    });
    registerValueDeriver(API_FEEDBACK_KEY, new ValueDeriver<String>() {
      @Nullable
      @Override
      public Set<Key<?>> getTriggerKeys() {
        return makeSetOf(getTargetComboBoxKey(MOBILE));
      }

      @Nullable
      @Override
      public String deriveValue(ScopedStateStore state, Key changedKey, @Nullable String currentValue) {
        AndroidTargetComboBoxItem selectedItem = state.get(getTargetComboBoxKey(MOBILE));
        return getApiHelpText(selectedItem == null ? 0 : selectedItem.apiLevel);
      }
    });

    Set<Key> keys = Sets.newHashSet();
    for (Pair<Key<Boolean>, JCheckBox> item : myCheckboxKeys) {
      keys.add(item.getFirst());
      register(item.getFirst(), item.getSecond());
    }
    myState.put(myCheckboxKeys.get(0).getFirst(), true);
    // Since the controls are created before the state is completely set up (before the step is
    // attached to the wizard) we have to explicitly load the saved state here.
    for (FormFactorSdkControls control : myFormFactorApiSelectors.values()) {
      control.getMinSdkCombo().loadSavedApi();
    }
  }

  private static String getApiHelpText(int selectedApi) {
    float percentage = (float)(DistributionService.getInstance().getSupportedDistributionForApiLevel(selectedApi) * 100);
    return String.format(Locale.getDefault(), "<html>Lower API levels target more devices, but have fewer features available. " +
                                              "By targeting API %1$d and later, your app will run on %2$s of the devices that are " +
                                              "active on the Google Play Store. " +
                                              "<span color=\"#%3$s\">Help me choose.</span>" +
                                              //"Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nam aliquam justo vitae faucibus rhoncus. Integer eget tincidunt dolor. Quisque cursus tempus ipsum. Nunc non felis id ante facilisis mollis. Nulla nunc quam, iaculis ut gravida vitae, ultrices quis nunc. Aenean id turpis condimentum urna finibus dignissim. Maecenas vel tempor elit, vel cursus sem. Integer non magna blandit, auctor nisl vel, venenatis tortor. Vestibulum sagittis porttitor ante at lobortis. Vivamus quis sagittis nunc. Aenean sed risus in ligula blandit placerat" +
                                              ".</html>",
                         selectedApi,
                         percentage < 1 ? "&lt; 1%" : String.format(Locale.getDefault(), "approximately <b>%.1f%%</b>", percentage),
                         Integer.toHexString(JBColor.blue.getRGB()).substring(2));
  }

  private void populateAdditionalFormFactors() {
    TemplateManager manager = TemplateManager.getInstance();
    List<File> applicationTemplates = manager.getTemplatesInCategory(Template.CATEGORY_APPLICATION);
    myFormFactors.clear();

    int row = 0;
    Map<FormFactor, Integer> minSdks = Maps.newHashMap();
    for (File templateFile : applicationTemplates) {
      TemplateMetadata metadata = manager.getTemplate(templateFile);
      if (metadata == null || metadata.getFormFactor() == null) {
        continue;
      }
      FormFactor formFactor = FormFactor.get(metadata.getFormFactor());
      if (formFactor == null) {
        continue;
      }
      myFormFactors.add(formFactor);
      minSdks.put(formFactor, metadata.getMinSdk());
    }

    GridLayoutManager gridLayoutManager = new GridLayoutManager(myFormFactors.size() * 2 + 1, 2);
    gridLayoutManager.setVGap(5);
    gridLayoutManager.setHGap(10);
    myFormFactorPanel.setLayout(gridLayoutManager);
    for (FormFactor formFactor : myFormFactors) {
      GridConstraints c = new GridConstraints();
      c.setRow(row);
      c.setColumn(0);
      c.setFill(GridConstraints.FILL_NONE);
      c.setAnchor(GridConstraints.ANCHOR_WEST);
      JCheckBox inclusionCheckBox = new JCheckBox(formFactor.toString());
      myFormFactorPanel.add(inclusionCheckBox, c);
      // Since we aren't connected to the wizard yet, we can't save this (wizard-scoped) state.
      // Save away the key and component so they can be registered later.
      myCheckboxKeys.add(Pair.create(FormFactorUtils.getInclusionKey(formFactor), inclusionCheckBox));
      FormFactorSdkControls controls = new FormFactorSdkControls(formFactor, minSdks.get(formFactor));
      FormFactorApiComboBox minSdkComboBox = controls.getMinSdkCombo();
      minSdkComboBox.setName(formFactor.id + ".minSdk");
      controls.layout(myFormFactorPanel, ++row, inclusionCheckBox.getIconTextGap());
      minSdkComboBox.register(this);
      myFormFactorApiSelectors.put(formFactor, controls);

      // If we don't have any valid targets for the given form factor, disable that form factor
      if (minSdkComboBox.getItemCount() == 0) {
        inclusionCheckBox.setSelected(false);
        inclusionCheckBox.setEnabled(false);
        inclusionCheckBox.setText(inclusionCheckBox.getText() + " (Not Installed)");
      }

      if (formFactor.equals(MOBILE)) {
        c.setRow(++row);
        c.setColumn(1);
        c.setAnchor(GridConstraints.ANCHOR_NORTHWEST);
        c.setFill(GridConstraints.FILL_HORIZONTAL);
        myFormFactorPanel.add(myHelpMeChooseLabel, c);
      }
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
    for (FormFactor formFactor : myFormFactors) {
      Boolean included = myState.get(getInclusionKey(formFactor));
      // Disable api selection for non-enabled form factors and check to see if only one is selected
      if (included != null) {
        if (myFormFactorApiSelectors.containsKey(formFactor)) {
          myFormFactorApiSelectors.get(formFactor).setEnabled(included);
        }
        if (included) {
          enabledFormFactors++;
          FormFactorSdkControls controls = myFormFactorApiSelectors.get(formFactor);
          if (controls != null) {
            controls.getMinSdkCombo().deriveValues(myState, modified);
          }
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
    for (FormFactor formFactor : myFormFactors) {
      Boolean included = myState.get(getInclusionKey(formFactor));
      // Disable api selection for non-enabled form factors and check to see if only one is selected
      if (included != null && included) {
        if (myState.get(getMinApiKey(formFactor)) == null) {
          // Don't allow the user to continue unless all minAPIs are chosen
          setErrorHtml("Each form factor must have a Minimum SDK level selected.");
          return false;
        }
      }
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

  private static final class FormFactorSdkControls {
    private final JLabel myLabel;
    private final FormFactorApiComboBox myMinSdkCombobox;

    public FormFactorSdkControls(FormFactor formFactor, int minApi) {
      myLabel = new JLabel(MIN_SDK_STRING);
      myMinSdkCombobox = new FormFactorApiComboBox(formFactor, minApi);
    }

    public void setEnabled(boolean enabled) {
      for (JComponent component : ImmutableSet.of(myLabel, myMinSdkCombobox)) {
        component.setEnabled(enabled);
      }
    }

    public void setSelectedItem(String item) {
      ScopedDataBinder.setSelectedItem(myMinSdkCombobox, item);
    }

    public JLabel getLabel() {
      return myLabel;
    }

    public FormFactorApiComboBox getMinSdkCombo() {
      return myMinSdkCombobox;
    }

    private void layout(JPanel panel, int row, int ident) {
      GridConstraints c = new GridConstraints();
      c.setVSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
      c.setRow(row);
      c.setColumn(0);
      c.setFill(GridConstraints.FILL_NONE);
      c.setAnchor(GridConstraints.ANCHOR_WEST);
      c.setRow(row);
      c.setIndent(ident);
      panel.add(myLabel, c);
      c.setIndent(0);
      c.setColumn(1);
      c.setFill(GridConstraints.FILL_HORIZONTAL);
      panel.add(myMinSdkCombobox, c);
    }
  }
}
