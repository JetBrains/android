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

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.sdk.SdkVersionInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.templates.TemplateUtils;
import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.FormFactorUtils.*;
import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.STEP;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

/**
 * ConfigureAndroidModuleStep is the first page in the New Project wizard that sets project/module name, location, and other project-global
 * parameters.
 */
public class ConfigureFormFactorStep extends DynamicWizardStepWithHeaderAndDescription {

  public static final String MIN_SDK_STRING = "Minimum SDK";
  public static final Key<Integer> TARGET_API_LEVEL_KEY = createKey(ATTR_TARGET_API, WIZARD, Integer.class);
  public static final Key<Integer> BUILD_API_LEVEL_KEY = createKey(ATTR_BUILD_API, WIZARD, Integer.class);
  private static final Key<String> API_FEEDBACK_KEY = createKey("API Feedback", STEP, String.class);

  private JPanel myPanel;
  private JPanel myFormFactorPanel;
  private JBLabel myHelpMeChooseLabel = new JBLabel("Help Me Choose");
  private List<AndroidTargetComboBoxItem> myTargets = Lists.newArrayList();
  private List<String> myFormFactors = Lists.newArrayList();
  private ChooseApiLevelDialog myChooseApiLevelDialog = new ChooseApiLevelDialog(null, -1);

  public ConfigureFormFactorStep() {
    super("Choose Platforms for your APKs", "Different platforms have varying requirements and will require separate APKs", null);
    setBodyComponent(myPanel);
  }

  @Override
  public void init() {
    super.init();
    initializeTargets();

    myHelpMeChooseLabel.addMouseListener(new MouseInputAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        myChooseApiLevelDialog = new ChooseApiLevelDialog(null, myState.get(getMinApiLevelKey(PHONE_TABLET_FORM_FACTOR_NAME)));
        myChooseApiLevelDialog.show();
        if (myChooseApiLevelDialog.isOK()) {
          int minApiLevel = myChooseApiLevelDialog.getSelectedApiLevel();
          myState.put(getMinApiLevelKey(PHONE_TABLET_FORM_FACTOR_NAME), minApiLevel);
        }
      }
    });
    myHelpMeChooseLabel.setMaximumSize(new Dimension(250, 200));
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
        return makeSetOf(getMinApiLevelKey(PHONE_TABLET_FORM_FACTOR_NAME));
      }

      @Nullable
      @Override
      public String deriveValue(ScopedStateStore state, Key changedKey, @Nullable String currentValue) {
        Integer selectedApi = state.get(getMinApiLevelKey(PHONE_TABLET_FORM_FACTOR_NAME));
        if (selectedApi == null) {
          return currentValue;
        }
        return String.format(Locale.getDefault(), "<html>Lower API levels target more devices, but have fewer features available. " +
                                                  "By targeting API %d and later, your app will run on approximately %.2f%% of the " +
                                                  "devices that have checked into the Play Store in recent history. " +
                                                  "<span color=\"#%s\">Help me choose.</span></html>", selectedApi,
                             myChooseApiLevelDialog.getSupportedDistributionForApiLevel(selectedApi) * 100,
                             Integer.toHexString(JBColor.blue.getRGB()).substring(2)
        );
      }
    });

    populateAdditionalFormFactors();
  }

  private void populateAdditionalFormFactors() {
    TemplateManager manager = TemplateManager.getInstance();
    List<File> applicationTemplates = manager.getTemplatesInCategory(Template.CATEGORY_APPLICATION);
    GridLayoutManager gridLayoutManager = new GridLayoutManager(applicationTemplates.size() * 2 + 1, 2);
    gridLayoutManager.setVGap(10);
    gridLayoutManager.setHGap(10);
    myFormFactorPanel.setLayout(gridLayoutManager);

    GridConstraints c = new GridConstraints();
    c.setVSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
    int row = 0;
    for (File templateFile : applicationTemplates) {
      TemplateMetadata metadata = manager.getTemplate(templateFile);
      if (metadata == null || metadata.getFormFactor() == null) {
        continue;
      }
      String formFactor = metadata.getFormFactor();
      myFormFactors.add(formFactor);
      c.setRow(row);
      c.setColumn(0);
      c.setFill(GridConstraints.FILL_NONE);
      c.setAnchor(GridConstraints.ANCHOR_WEST);
      JCheckBox inclusionCheckBox = new JCheckBox(formFactor);
      myFormFactorPanel.add(inclusionCheckBox, c);
      register(FormFactorUtils.getInclusionKey(formFactor), inclusionCheckBox);
      c.setRow(++row);
      JBLabel minSdkLabel = new JBLabel(MIN_SDK_STRING);
      c.setIndent(inclusionCheckBox.getIconTextGap());
      myFormFactorPanel.add(minSdkLabel, c);
      c.setIndent(0);
      c.setColumn(1);
      c.setFill(GridConstraints.FILL_HORIZONTAL);
      ComboBox minSdkComboBox = new ComboBox();
      populateComboBox(minSdkComboBox, metadata.getMinSdk());
      Key<Integer> minApiKey = FormFactorUtils.getMinApiLevelKey(formFactor);
      Integer savedApiLevel = PropertiesComponent.getInstance().getOrInitInt(
        FormFactorUtils.getPropertiesComponentMinSdkKey(formFactor), 15);
      myState.put(minApiKey, savedApiLevel);
      register(minApiKey, minSdkComboBox);
      myFormFactorPanel.add(minSdkComboBox, c);
      if (formFactor.equals(PHONE_TABLET_FORM_FACTOR_NAME)) {
        c.setRow(++row);
        c.setAnchor(GridConstraints.ANCHOR_NORTHWEST);
        c.setFill(GridConstraints.FILL_NONE);
        myFormFactorPanel.add(myHelpMeChooseLabel, c);
      }
      row++;
    }
  }

  private void populateComboBox(@NotNull JComboBox comboBox, int minSdk) {
    for (AndroidTargetComboBoxItem target : myTargets) {
      if (target.apiLevel >= minSdk) {
        comboBox.addItem(target);
      }
    }
  }

  private void initializeTargets() {
    IAndroidTarget[] targets = getCompilationTargets();

    if (AndroidSdkUtils.isAndroidSdkAvailable()) {
      String[] knownVersions = TemplateUtils.getKnownVersions();

      for (int i = 0; i < knownVersions.length; i++) {
        AndroidTargetComboBoxItem targetInfo = new AndroidTargetComboBoxItem(knownVersions[i], i + 1);
        myTargets.add(targetInfo);
      }
      myState.put(TARGET_API_LEVEL_KEY, SdkVersionInfo.HIGHEST_KNOWN_API);
    }

    int highestApi = -1;
    for (IAndroidTarget target : targets) {
      highestApi = Math.max(highestApi, target.getVersion().getApiLevel());
      AndroidTargetComboBoxItem targetInfo = new AndroidTargetComboBoxItem(target);
      if (target.getVersion().isPreview()) {
        myTargets.add(targetInfo);
      }
    }
    if (highestApi >= 1) {
      myState.put(BUILD_API_LEVEL_KEY, highestApi);
      if (highestApi > SdkVersionInfo.HIGHEST_KNOWN_API) {
        myState.put(TARGET_API_LEVEL_KEY, highestApi);
      }
    }
  }

  @Override
  public void deriveValues(Set<Key> modified) {
    super.deriveValues(modified);
    // Persist the min API level choices on a per-form factor basis
    for (String formFactor : myFormFactors) {
      Key<Integer> key = getMinApiLevelKey(formFactor);
      if (modified.contains(key)) {
        Integer minApi = myState.get(key);
        myState.put(getMinApiKey(formFactor), minApi);
        if (minApi != null) {
          PropertiesComponent.getInstance().setValue(getPropertiesComponentMinSdkKey(formFactor), minApi.toString());
        }
      }
    }
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Configure Form Factors";
  }

  @NotNull
  @VisibleForTesting
  IAndroidTarget[] getCompilationTargets() {
    AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (sdkData == null) {
      return new IAndroidTarget[0];
    }
    return getCompilationTargets(sdkData);
  }

  @NotNull
  public static IAndroidTarget[] getCompilationTargets(@NotNull AndroidSdkData sdkData) {
    IAndroidTarget[] targets = sdkData.getTargets();
    List<IAndroidTarget> list = new ArrayList<IAndroidTarget>();

    for (IAndroidTarget target : targets) {
      if (!target.isPlatform() &&
          (target.getOptionalLibraries() == null ||
           target.getOptionalLibraries().length == 0)) {
        continue;
      }
      list.add(target);
    }
    return list.toArray(new IAndroidTarget[list.size()]);
  }

  @Nullable
  public String getHelpText(@NotNull String param) {
    if (param.equals(ATTR_MIN_API)) {
      return "Choose the lowest version of Android that your application will support. Lower API levels target more devices, " +
             "but means fewer features are available. By targeting API 8 and later, you reach approximately 95% of the market.";
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

  public static class AndroidTargetComboBoxItem extends ComboBoxItem {
    public int apiLevel = -1;
    public IAndroidTarget target = null;

    public AndroidTargetComboBoxItem(@NotNull String label, int apiLevel) {
      super(apiLevel, label, 1, 1);
      this.apiLevel = apiLevel;
    }

    public AndroidTargetComboBoxItem(@NotNull IAndroidTarget target) {
      super(getId(target), getLabel(target), 1, 1);
      this.target = target;
      apiLevel = target.getVersion().getApiLevel();
    }

    @NotNull
    @VisibleForTesting
    static String getLabel(@NotNull IAndroidTarget target) {
      if (target.isPlatform()
          && target.getVersion().getApiLevel() <= SdkVersionInfo.HIGHEST_KNOWN_API) {
        String name = SdkVersionInfo.getAndroidName(target.getVersion().getApiLevel());
        if (name == null) {
          return "API " + Integer.toString(target.getVersion().getApiLevel());
        } else {
          return name;
        }
      } else {
        return TemplateUtils.getTargetLabel(target);
      }
    }

    @NotNull
    private static Object getId(@NotNull IAndroidTarget target) {
      if (target.getVersion().isPreview()) {
        return target.getVersion().getCodename();
      } else {
        return target.getVersion().getApiLevel();
      }
    }

    @Override
    public String toString() {
      return label;
    }
  }
}
