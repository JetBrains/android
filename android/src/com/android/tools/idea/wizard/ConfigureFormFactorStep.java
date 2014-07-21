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
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.templates.TemplateUtils;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
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
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.WizardConstants.INSTALL_REQUESTS_KEY;
import static com.android.tools.idea.wizard.FormFactorUtils.*;
import static com.android.tools.idea.wizard.FormFactorUtils.FormFactor.MOBILE;
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
  private static final Key<String> API_FEEDBACK_KEY = createKey("API Feedback", STEP, String.class);
  public static final Key<Integer> NUM_ENABLED_FORM_FACTORS_KEY = createKey("NumberOfEnabledFormFactors", WIZARD, Integer.class);


  private JPanel myPanel;
  private JPanel myFormFactorPanel;
  private JBLabel myHelpMeChooseLabel = new JBLabel("Help Me Choose");
  private List<AndroidTargetComboBoxItem> myTargets = Lists.newArrayList();
  private Set<AndroidVersion> myInstalledVersions = Sets.newHashSet();
  private List<FormFactor> myFormFactors = Lists.newArrayList();
  private ChooseApiLevelDialog myChooseApiLevelDialog = new ChooseApiLevelDialog(null, -1);
  private Disposable myDisposable;
  private Map<FormFactor, JComboBox> myFormFactorApiSelectors = Maps.newHashMap();
  private IAndroidTarget myHighestInstalledApiTarget;
  private Map<FormFactor, IPkgDesc> myInstallRequests = Maps.newHashMap();

  public ConfigureFormFactorStep(@NotNull Disposable disposable) {
    super("Select the form factors your app will run on", "Different platforms require separate SDKs",
          null, disposable);
    myDisposable = disposable;
    Disposer.register(disposable, myChooseApiLevelDialog.getDisposable());
    setBodyComponent(myPanel);
  }

  @Override
  public void init() {
    super.init();
    initializeTargets();

    myHelpMeChooseLabel.addMouseListener(new MouseInputAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        myChooseApiLevelDialog = new ChooseApiLevelDialog(null, myState.get(getMinApiLevelKey(MOBILE)));
        Disposer.register(myDisposable, myChooseApiLevelDialog.getDisposable());
        myChooseApiLevelDialog.show();
        if (myChooseApiLevelDialog.isOK()) {
          int minApiLevel = myChooseApiLevelDialog.getSelectedApiLevel();
          setSelectedItem(myFormFactorApiSelectors.get(MOBILE), Integer.toString(minApiLevel));
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
        return makeSetOf(getTargetComboBoxKey(MOBILE));
      }

      @Nullable
      @Override
      public String deriveValue(ScopedStateStore state, Key changedKey, @Nullable String currentValue) {
        AndroidTargetComboBoxItem selectedItem = state.get(getTargetComboBoxKey(MOBILE));
        if (selectedItem == null) {
          return currentValue;
        }
        Integer selectedApi = selectedItem.apiLevel;
        return String.format(Locale.getDefault(), "<html>Lower API levels target more devices, but have fewer features available. " +
                                                  "By targeting API %d and later, your app will run on approximately <b>%.1f%%</b> of the " +
                                                  "devices that are active on the Google Play Store. " +
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
    gridLayoutManager.setVGap(5);
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
      FormFactor formFactor = FormFactor.get(metadata.getFormFactor());
      if (formFactor == null) {
        continue;
      }
      myFormFactors.add(formFactor);
      c.setRow(row);
      c.setColumn(0);
      c.setFill(GridConstraints.FILL_NONE);
      c.setAnchor(GridConstraints.ANCHOR_WEST);
      JCheckBox inclusionCheckBox = new JCheckBox(formFactor.toString());
      if (row == 0) {
        myState.put(FormFactorUtils.getInclusionKey(formFactor), true);
      }
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

      // Here we add all the targets that are appropriate to this form factor and this template.
      // All targets with API level < minSdk are filtered out, as well as any templates that are
      // marked as invalid by the formFactor's white/black lists. {@see FormFactorUtils.FormFactor}
      populateComboBox(minSdkComboBox, formFactor, metadata.getMinSdk());

      // Check for a saved value for the min api level
      Key<String> minApiKey = getMinApiKey(formFactor);
      String savedApiLevel = PropertiesComponent.getInstance().getValue(FormFactorUtils.getPropertiesComponentMinSdkKey(formFactor),
                                                                        Integer.toString(formFactor.defaultApi));
      if (savedApiLevel == null) {
        savedApiLevel = Integer.toString(metadata.getMinSdk());
      }
      myState.put(minApiKey, savedApiLevel);
      register(getTargetComboBoxKey(formFactor), minSdkComboBox, TARGET_COMBO_BINDING);

      setSelectedItem(minSdkComboBox, savedApiLevel);
      // If the savedApiLevel is not available, just pick the first target in the list
      // which is guaranteed to be a valid target because of the filtering done by populateComboBox()
      if (minSdkComboBox.getSelectedIndex() < 0 && minSdkComboBox.getItemCount() > 0) {
        minSdkComboBox.setSelectedIndex(0);
      }

      // If we don't have any valid targets for the given form factor, disable that form factor
      if (minSdkComboBox.getItemCount() == 0) {
        inclusionCheckBox.setSelected(false);
        inclusionCheckBox.setEnabled(false);
        inclusionCheckBox.setText(inclusionCheckBox.getText() + " (Not Installed)");
      }

      myFormFactorPanel.add(minSdkComboBox, c);
      myFormFactorApiSelectors.put(formFactor, minSdkComboBox);
      if (formFactor.equals(MOBILE)) {
        c.setRow(++row);
        c.setAnchor(GridConstraints.ANCHOR_NORTHWEST);
        c.setFill(GridConstraints.FILL_NONE);
        myFormFactorPanel.add(myHelpMeChooseLabel, c);
      }
      row++;
    }
  }

  private void populateComboBox(@NotNull JComboBox comboBox, @NotNull FormFactor formFactor, int minSdk) {
    for (AndroidTargetComboBoxItem target :
         Iterables.filter(myTargets, FormFactorUtils.getMinSdkComboBoxFilter(formFactor, minSdk))) {
      if (target.apiLevel >= minSdk || (target.target != null && target.target.getVersion().isPreview())) {
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
    }

    myHighestInstalledApiTarget = null;
    for (IAndroidTarget target : targets) {
      if (myHighestInstalledApiTarget == null ||
          target.getVersion().getFeatureLevel() > myHighestInstalledApiTarget.getVersion().getFeatureLevel() &&
          !target.getVersion().isPreview()) {
        myHighestInstalledApiTarget = target;
      }
      if (target.getVersion().isPreview() || target.getOptionalLibraries() != null && target.getOptionalLibraries().length > 0) {
        AndroidTargetComboBoxItem targetInfo = new AndroidTargetComboBoxItem(target);
        myTargets.add(targetInfo);
        myInstalledVersions.add(targetInfo.target.getVersion());
      }
    }
  }

  private void populateApiLevels(@NotNull FormFactor formFactor, int apiLevel, @Nullable IAndroidTarget apiTarget) {
    Key<String> buildApiKey = FormFactorUtils.getBuildApiKey(formFactor);
    Key<Integer> buildApiLevelKey = FormFactorUtils.getBuildApiLevelKey(formFactor);
    Key<Integer> targetApiLevelKey = FormFactorUtils.getTargetApiLevelKey(formFactor);
    Key<String> targetApiStringKey = FormFactorUtils.getTargetApiStringKey(formFactor);
    if (apiLevel >= 1) {
      if (apiTarget == null) {
        myState.put(buildApiKey, Integer.toString(apiLevel));
      } else if (apiTarget.getOptionalLibraries() != null) {
        myState.put(buildApiKey, AndroidTargetHash.getTargetHashString(apiTarget));
      } else {
        myState.put(buildApiKey, TemplateMetadata.getBuildApiString(apiTarget.getVersion()));
      }
      myState.put(buildApiLevelKey, apiLevel);
      if (apiLevel >= SdkVersionInfo.HIGHEST_KNOWN_API || (apiTarget != null && apiTarget.getVersion().isPreview())) {
        myState.put(targetApiLevelKey, apiLevel);
        if (apiTarget != null) {
          myState.put(targetApiStringKey, apiTarget.getVersion().getApiString());
        } else {
          myState.put(targetApiStringKey, Integer.toString(apiLevel));
        }
      } else if (myHighestInstalledApiTarget != null) {
        myState.put(targetApiLevelKey, myHighestInstalledApiTarget.getVersion().getApiLevel());
        myState.put(targetApiStringKey, myHighestInstalledApiTarget.getVersion().getApiString());
      }
    }
  }

  @Override
  public void deriveValues(Set<Key> modified) {
    super.deriveValues(modified);
    // Persist the min API level choices on a per-form factor basis
    int enabledFormFactors = 0;
    for (FormFactor formFactor : myFormFactors) {
      Key<AndroidTargetComboBoxItem> key = getTargetComboBoxKey(formFactor);
      if (modified.contains(key)) {
        AndroidTargetComboBoxItem targetItem = myState.get(key);
        if (targetItem == null) {
          continue;
        }
        myState.put(getMinApiKey(formFactor), targetItem.id.toString());
        myState.put(getMinApiLevelKey(formFactor), targetItem.apiLevel);
        IAndroidTarget target = targetItem.target;
        if (target != null && (target.getVersion().isPreview() || !target.isPlatform())) {
          // Make sure we set target and build to the preview version as well
          populateApiLevels(formFactor, targetItem.apiLevel, target);
        } else {
          int targetApiLevel;
          if (myHighestInstalledApiTarget != null) {
            targetApiLevel = myHighestInstalledApiTarget.getVersion().getFeatureLevel();
          } else {
            targetApiLevel = 0;
          }
          populateApiLevels(formFactor, targetApiLevel, myHighestInstalledApiTarget);
        }
        // Check to see if this is installed. If not, request that we install it
        if (myInstallRequests.containsKey(formFactor)) {
          // First remove the last request, no need to install more than one platform
          myState.listRemove(INSTALL_REQUESTS_KEY, myInstallRequests.get(formFactor));
        }
        if (target != null && !myInstalledVersions.contains(target.getVersion())) {
          IPkgDesc platformDescription = PkgDesc.Builder.newPlatform(target.getVersion(), new MajorRevision(target.getRevision()),
                                                                    target.getBuildToolInfo().getRevision()).create();
          myState.listPush(INSTALL_REQUESTS_KEY, platformDescription);
          myInstallRequests.put(formFactor, platformDescription);
        }
        PropertiesComponent.getInstance().setValue(getPropertiesComponentMinSdkKey(formFactor), targetItem.id.toString());
      }
      Boolean included = myState.get(getInclusionKey(formFactor));
      // Disable api selection for non-enabled form factors and check to see if only one is selected
      if (included != null) {
        if (myFormFactorApiSelectors.containsKey(formFactor)) {
          myFormFactorApiSelectors.get(formFactor).setEnabled(included);
        }
        if (included) {
          enabledFormFactors++;
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

  private static final ComponentBinding<AndroidTargetComboBoxItem, JComboBox> TARGET_COMBO_BINDING =
    new ComponentBinding<AndroidTargetComboBoxItem, JComboBox>() {
    @Override
    public void setValue(@Nullable AndroidTargetComboBoxItem newValue, @NotNull JComboBox component) {
      component.setSelectedItem(newValue);
    }

    @Nullable
    @Override
    public AndroidTargetComboBoxItem getValue(@NotNull JComboBox component) {
      return (AndroidTargetComboBoxItem)component.getItemAt(component.getSelectedIndex());
    }

    @Override
    public void addActionListener(@NotNull ActionListener listener, @NotNull JComboBox component) {
      component.addActionListener(listener);
    }
  };

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel;
  }

  public static class AndroidTargetComboBoxItem extends ComboBoxItem {
    public int apiLevel = -1;
    public IAndroidTarget target = null;

    public AndroidTargetComboBoxItem(@NotNull String label, int apiLevel) {
      super(Integer.toString(apiLevel), label, 1, 1);
      this.apiLevel = apiLevel;
    }

    public AndroidTargetComboBoxItem(@NotNull IAndroidTarget target) {
      super(getId(target), getLabel(target), 1, 1);
      this.target = target;
      apiLevel = target.getVersion().getFeatureLevel();
    }

    @NotNull
    @VisibleForTesting
    static String getLabel(@NotNull IAndroidTarget target) {
      if (target.isPlatform()
          && target.getVersion().getApiLevel() <= SdkVersionInfo.HIGHEST_KNOWN_API) {
        if (target.getVersion().isPreview()) {
          return "API " + Integer.toString(target.getVersion().getApiLevel()) + "+: " + target.getName();
        }
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
    private static String getId(@NotNull IAndroidTarget target) {
      return target.getVersion().getApiString();
    }

    @Override
    public String toString() {
      return label;
    }
  }

  @Nullable
  @Override
  protected JComponent getHeader() {
    return ConfigureAndroidProjectPath.buildConfigurationHeader();
  }

  @Override
  @Nullable
  protected JBColor getTitleTextColor() {
    return WizardConstants.ANDROID_NPW_TITLE_COLOR;
  }
}
