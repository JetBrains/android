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

import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.tools.idea.sdk.SdkLoadedCallback;
import com.android.tools.idea.sdk.SdkPackages;
import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.wizard.SdkQuickfixWizard;
import com.android.tools.idea.stats.DistributionService;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.dynamic.DialogWrapperHost;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithHeaderAndDescription;
import com.android.tools.idea.wizard.dynamic.ScopedDataBinder;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.npw.FormFactorApiComboBox.AndroidTargetComboBoxItem;
import static com.android.tools.idea.npw.FormFactorUtils.*;
import static com.android.tools.idea.npw.FormFactorUtils.FormFactor.MOBILE;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.STEP;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.createKey;
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
  private JBLabel myHelpMeChooseLabel = new JBLabel(getApiHelpText(0, ""));
  private HyperlinkLabel myHelpMeChooseLink = new HyperlinkLabel("Help me choose");
  private List<Pair<Key<Boolean>, JCheckBox>> myCheckboxKeys = Lists.newArrayList();

  private Set<FormFactor> myFormFactors = Sets.newTreeSet();
  private ChooseApiLevelDialog myChooseApiLevelDialog = new ChooseApiLevelDialog(null, -1);
  private Disposable myDisposable;
  private Map<FormFactor, FormFactorSdkControls> myFormFactorApiSelectors = Maps.newHashMap();

  private static final String DOWNLOAD_LINK_CARD = "link";
  private static final String DOWNLOAD_PROGRESS_CARD = "progress";

  public ConfigureFormFactorStep(@NotNull Disposable disposable) {
    super("Select the form factors your app will run on", "Different platforms may require separate SDKs", disposable);
    myDisposable = disposable;
    Disposer.register(disposable, myChooseApiLevelDialog.getDisposable());
    setBodyComponent(myPanel);
    populateAdditionalFormFactors();
  }

  @Override
  public void init() {
    super.init();

    myHelpMeChooseLink.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        Integer minApiLevel = myState.get(getMinApiLevelKey(MOBILE));
        myChooseApiLevelDialog = new ChooseApiLevelDialog(null, minApiLevel == null ? 0 : minApiLevel);
        Disposer.register(myDisposable, myChooseApiLevelDialog.getDisposable());
        if (myChooseApiLevelDialog.showAndGet()) {
          int selectedApiLevel = myChooseApiLevelDialog.getSelectedApiLevel();
          myFormFactorApiSelectors.get(MOBILE).setSelectedItem(Integer.toString(selectedApiLevel));
        }
      }
    });
    register(API_FEEDBACK_KEY, myHelpMeChooseLabel, new ComponentBinding<String, JBLabel>() {
      @Override
      public void setValue(@Nullable String newValue, @NotNull JBLabel label) {
        final JBLabel referenceLabel = label;
        final String referenceString = newValue;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
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
      public String deriveValue(@NotNull ScopedStateStore state, Key changedKey, @Nullable String currentValue) {
        AndroidTargetComboBoxItem selectedItem = state.get(getTargetComboBoxKey(MOBILE));
        String name = Integer.toString(selectedItem == null ? 0 : selectedItem.apiLevel);
        if (selectedItem != null && selectedItem.target != null) {
          name = selectedItem.target.getVersion().getApiString();
        }
        return getApiHelpText(selectedItem == null ? 0 : selectedItem.apiLevel, name);
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

  private static String getApiHelpText(int selectedApi, String selectedApiName) {
    float percentage = (float)(DistributionService.getInstance().getSupportedDistributionForApiLevel(selectedApi) * 100);
    return String.format(Locale.getDefault(), "<html>Lower API levels target more devices, but have fewer features available. " +
                                              "By targeting API %1$s<br>and later, your app will run on %2$s of the devices that are " +
                                              "active on the<br>Google Play Store.</html>",
                         selectedApiName,
                         percentage < 1 ? "&lt; 1%" : String.format(Locale.getDefault(), "approximately <b>%.1f%%</b>", percentage));
  }

  private void populateAdditionalFormFactors() {
    TemplateManager manager = TemplateManager.getInstance();
    List<File> applicationTemplates = manager.getTemplatesInCategory(Template.CATEGORY_APPLICATION);
    myFormFactors.clear();
    myFormFactorPanel.removeAll();

    int row = 0;
    final Map<FormFactor, Integer> minSdks = Maps.newHashMap();
    for (File templateFile : applicationTemplates) {
      TemplateMetadata metadata = manager.getTemplate(templateFile);
      if (metadata == null || metadata.getFormFactor() == null) {
        continue;
      }
      FormFactor formFactor = FormFactor.get(metadata.getFormFactor());
      if (formFactor == null) {
        continue;
      }
      if (!myFormFactors.contains(formFactor)) {
        myFormFactors.add(formFactor);
        minSdks.put(formFactor, metadata.getMinSdk());
      }
    }

    // Number of rows is two for each active form factor (one for checkbox and one for popup) + one for each of the
    // message about % of devices covered and the "help me choose" link.
    GridLayoutManager gridLayoutManager = new GridLayoutManager(myFormFactors.size() * 2 + 2, 2);
    gridLayoutManager.setVGap(5);
    gridLayoutManager.setHGap(10);
    myFormFactorPanel.setLayout(gridLayoutManager);
    for (final FormFactor formFactor : myFormFactors) {
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

      // Only show SDK selector for base form factors.
      if (formFactor.baseFormFactor == null) {
        FormFactorSdkControls controls = new FormFactorSdkControls(formFactor, minSdks.get(formFactor));
        FormFactorApiComboBox minSdkComboBox = controls.getMinSdkCombo();
        minSdkComboBox.setName(formFactor.id + ".minSdk");

        controls.layout(myFormFactorPanel, ++row, inclusionCheckBox.getIconTextGap());
        myFormFactorApiSelectors.put(formFactor, controls);
        minSdkComboBox.register(this);

        // If we don't have any valid targets for the given form factor, disable that form factor
        if (minSdkComboBox.getItemCount() == 0) {
          inclusionCheckBox.setSelected(false);
          inclusionCheckBox.setEnabled(false);
          inclusionCheckBox.setText(inclusionCheckBox.getText() + " (Not Installed)");

          JBCardLayout layout = new JBCardLayout();
          final JPanel downloadCardPanel = new JPanel(layout);
          downloadCardPanel.add(DOWNLOAD_PROGRESS_CARD, createDownloadingMessage());
          final HyperlinkLabel link = new HyperlinkLabel("Download");
          downloadCardPanel.add(DOWNLOAD_LINK_CARD, link);
          layout.show(downloadCardPanel, DOWNLOAD_PROGRESS_CARD);
          c.setColumn(1);
          myFormFactorPanel.add(downloadCardPanel, c);
          findCompatibleSdk(formFactor, minSdks.get(formFactor), link, downloadCardPanel);
        }
      }

      if (formFactor.equals(MOBILE)) {
        c.setRow(++row);
        c.setColumn(1);
        c.setAnchor(GridConstraints.ANCHOR_NORTHWEST);
        c.setFill(GridConstraints.FILL_HORIZONTAL);
        myFormFactorPanel.add(myHelpMeChooseLabel, c);
        c.setRow(++row);
        myFormFactorPanel.add(myHelpMeChooseLink, c);
      }
      row++;
    }
  }

  private static JComponent createDownloadingMessage() {
    JPanel downloadPanel = new JPanel(new FlowLayout());
    AsyncProcessIcon refreshIcon = new AsyncProcessIcon("loading");
    JLabel refreshingLabel = new JLabel("Looking for SDK...");
    refreshingLabel.setForeground(JBColor.GRAY);
    downloadPanel.add(refreshIcon);
    downloadPanel.add(refreshingLabel);
    return downloadPanel;
  }

  private void findCompatibleSdk(final FormFactor formFactor, final int minSdkLevel, final HyperlinkLabel link, final JPanel cardPanel) {
    SdkLoadedCallback onComplete = new SdkLoadedCallback(true) {
      @Override
      public void doRun(@NotNull SdkPackages packages) {
        List<RemotePkgInfo> packageList = Lists.newArrayList(packages.getNewPkgs());
        Collections.sort(packageList);
        Iterator<RemotePkgInfo> result =
          Iterables.filter(packageList, FormFactorUtils.getMinSdkPackageFilter(formFactor, minSdkLevel)).iterator();
        if (result.hasNext()) {
          showDownloadLink(link, result.next(), cardPanel);
        }
        else {
          cardPanel.setVisible(false);
        }
      }
    };

    Runnable onError = new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            cardPanel.setVisible(false);
          }
        }, ModalityState.any());
      }
    };

    SdkState.getInstance(AndroidSdkUtils.tryToChooseAndroidSdk()).loadAsync(SdkState.DEFAULT_EXPIRATION_PERIOD_MS, false, null, onComplete,
                                                                            onError, false);
  }

  private void showDownloadLink(final HyperlinkLabel link, final RemotePkgInfo remote, final JPanel cardPanel) {
    link.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        showDownloadWizard(remote.getPkgDesc());
        populateAdditionalFormFactors();
        myFormFactorPanel.validate();
      }
    });
    ((JBCardLayout)cardPanel.getLayout()).show(cardPanel, DOWNLOAD_LINK_CARD);
  }

  private static void showDownloadWizard(IPkgDesc pack) {
    List<IPkgDesc> requestedPackages = Lists.newArrayList(pack);
    SdkQuickfixWizard sdkQuickfixWizard = new SdkQuickfixWizard(null, null, requestedPackages,
                                                                new DialogWrapperHost(null, DialogWrapper.IdeModalityType.PROJECT));
    sdkQuickfixWizard.init();
    sdkQuickfixWizard.show();
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
      setErrorHtml("In order to support " + formFactor + " you need to enable " + formFactor.baseFormFactor + "");
      return false;
    }
    // Check if minSDK of the base is valid:
    AndroidTargetComboBoxItem baseMinSdk = myState.get(getTargetComboBoxKey(formFactor.baseFormFactor));
    if (!FormFactorUtils.getMinSdkComboBoxFilter(formFactor, 0).apply(baseMinSdk)) {
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
