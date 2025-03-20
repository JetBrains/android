/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.run.editor;

import com.android.tools.idea.execution.common.debug.AndroidDebuggerContext;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.AndroidRunConfigurationModule;
import com.android.tools.idea.run.ConfigurationSpecificEditor;
import com.android.tools.idea.run.ValidationError;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.Ordering;
import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidRunConfigurationEditor<T extends AndroidRunConfigurationBase> extends SettingsEditor<T> implements PanelWithAnchor,
                                                                                                                       ActionListener {
  private JPanel myPanel;
  protected JBTabbedPane myTabbedPane;
  private JBLabel myModuleJBLabel;
  private ModulesComboBox myModulesComboBox;

  // application run parameters or test run parameters
  private JPanel myConfigurationSpecificPanel;

  private final DeploymentTargetOptions myDeploymentTargetOptions;

  // Misc. options tab
  private JCheckBox myClearLogCheckBox;
  private JCheckBox myShowLogcatCheckBox;
  private JComponent anchor;

  private final ConfigurationModuleSelector myModuleSelector;
  private ConfigurationSpecificEditor<T> myConfigurationSpecificEditor;

  private AndroidDebuggerPanel myAndroidDebuggerPanel;
  private final AndroidProfilersPanel myAndroidProfilersPanel;

  public AndroidRunConfigurationEditor(Project project,
                                       Predicate<Module> moduleValidator,
                                       T config,
                                       boolean showLogcatCheckbox,
                                       boolean isAndroidTest,
                                       Function<ConfigurationModuleSelector, ConfigurationSpecificEditor<T>> configurationSpecificEditorFactory) {
    Disposer.register(project, this);
    setupUI();
    myModuleSelector = new ConfigurationModuleSelector(project, myModulesComboBox) {
      @Override
      public boolean isModuleAccepted(Module module) {
        if (module == null || !super.isModuleAccepted(module)) {
          return false;
        }
        return moduleValidator.apply(module);
      }

      @Override
      public JavaRunConfigurationModule getConfigurationModule() {
        // ConfigurationModuleSelector creates JavaRunConfigurationModule the same way, but we need to replace it with
        // our enhanced version for things to work correctly.
        AndroidRunConfigurationModule configurationModule = new AndroidRunConfigurationModule(getProject(), config.isTestConfiguration());
        configurationModule.setModule(getModule());
        return configurationModule;
      }
    };
    myModulesComboBox.addActionListener(this);

    List<DeployTargetProvider> providers = config.getApplicableDeployTargetProviders();

    switch (providers.size()) {
      case 0:
      case 1:
        myDeploymentTargetOptions = null;
        break;
      default:
        myDeploymentTargetOptions = new DeploymentTargetOptions(providers, this, project);
        myDeploymentTargetOptions.addTo((Container)myTabbedPane.getComponentAt(0));
        break;
    }

    AndroidDebuggerContext androidDebuggerContext = config.getAndroidDebuggerContext();

    if (androidDebuggerContext.getAndroidDebuggers().size() > 1) {
      myAndroidDebuggerPanel = new AndroidDebuggerPanel(config, androidDebuggerContext);
      myTabbedPane.add("Debugger", myAndroidDebuggerPanel.getComponent());
    }

    myAndroidProfilersPanel = new AndroidProfilersPanel(project, config.getProfilerState());
    if (!StudioFlags.PROFILER_TASK_BASED_UX.get()) {
      myTabbedPane.add("Profiling", myAndroidProfilersPanel.getComponent());
    }

    myConfigurationSpecificEditor = configurationSpecificEditorFactory.apply(myModuleSelector);
    Disposer.register(this, myConfigurationSpecificEditor);
    myConfigurationSpecificPanel.add(myConfigurationSpecificEditor.getComponent());

    myShowLogcatCheckBox.setVisible(showLogcatCheckbox);

    checkValidationResults(config.validate(null, this::fireEditorStateChanged));
  }

  /**
   * Allows the editor UI to response based on any validation errors.
   * The {@link ValidationError.Category} with the most severe errors should be responded to first.
   */
  private void checkValidationResults(@NotNull List<ValidationError> errors) {
    if (errors.isEmpty()) {
      return;
    }

    ValidationError topError = Ordering.natural().max(errors);
    if (ValidationError.Category.PROFILER.equals(topError.getCategory())) {
      myTabbedPane.setSelectedComponent(myAndroidProfilersPanel.getComponent());
    }
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myModuleJBLabel.setAnchor(anchor);
  }

  @Override
  protected void resetEditorFrom(@NotNull T configuration) {
    // Set configurations before resetting the module selector to avoid premature calls to setFacet.
    myModuleSelector.reset(configuration);

    if (myDeploymentTargetOptions != null) {
      myDeploymentTargetOptions.resetFrom(configuration);
    }

    myClearLogCheckBox.setSelected(configuration.CLEAR_LOGCAT);
    myShowLogcatCheckBox.setSelected(configuration.SHOW_LOGCAT_AUTOMATICALLY);

    myConfigurationSpecificEditor.resetFrom(configuration);

    if (myAndroidDebuggerPanel != null) {
      myAndroidDebuggerPanel.resetFrom(configuration.getAndroidDebuggerContext());
    }
    myAndroidProfilersPanel.resetFrom(configuration.getProfilerState());
  }

  @Override
  protected void applyEditorTo(@NotNull T configuration) {
    myModuleSelector.applyTo(configuration);

    if (myDeploymentTargetOptions != null) {
      myDeploymentTargetOptions.applyTo(configuration);
    }

    configuration.CLEAR_LOGCAT = myClearLogCheckBox.isSelected();
    configuration.SHOW_LOGCAT_AUTOMATICALLY = myShowLogcatCheckBox.isSelected();

    myConfigurationSpecificEditor.applyTo(configuration);

    if (myAndroidDebuggerPanel != null) {
      myAndroidDebuggerPanel.applyTo(configuration.getAndroidDebuggerContext());
    }
    myAndroidProfilersPanel.applyTo(configuration.getProfilerState());
  }

  @Override
  @NotNull
  protected JComponent createEditor() {
    return myPanel;
  }

  public ConfigurationModuleSelector getModuleSelector() {
    return myModuleSelector;
  }

  @NotNull
  JComboBox getModuleComboBox() {
    return myModulesComboBox;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == myModulesComboBox) {
      if (myConfigurationSpecificEditor instanceof ApplicationRunParameters) {
        ((ApplicationRunParameters)myConfigurationSpecificEditor).onModuleChanged();
      }
    }
  }

  @Nullable
  @VisibleForTesting
  DeploymentTargetOptions getDeploymentTargetOptions() {
    return myDeploymentTargetOptions;
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(46, 14), null, 0, false));
    myTabbedPane = new JBTabbedPane();
    myPanel.add(myTabbedPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                  new Dimension(200, 200), null, 0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(6, 3, new Insets(0, 0, 0, 0), -1, -1));
    myTabbedPane.addTab(getMessageFromBundle("messages/AndroidBundle", "android.run.configuration.general.tab.title"), panel1);
    myModuleJBLabel = new JBLabel();
    myModuleJBLabel.setText("Module:");
    myModuleJBLabel.setDisplayedMnemonic('M');
    myModuleJBLabel.setDisplayedMnemonicIndex(0);
    panel1.add(myModuleJBLabel,
               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    panel1.add(spacer2, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                            GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myModulesComboBox = new ModulesComboBox();
    panel1.add(myModulesComboBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                      null, 0, false));
    myConfigurationSpecificPanel = new JPanel();
    myConfigurationSpecificPanel.setLayout(new BorderLayout(0, 0));
    panel1.add(myConfigurationSpecificPanel, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final Spacer spacer3 = new Spacer();
    panel1.add(spacer3, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                            GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    myTabbedPane.addTab(getMessageFromBundle("messages/AndroidBundle", "android.run.configuration.misc.tab.title"), panel2);
    final Spacer spacer4 = new Spacer();
    panel2.add(spacer4, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                            GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final JPanel panel3 = new JPanel();
    panel3.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
    panel2.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                           false));
    panel3.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(BorderFactory.createEtchedBorder(), "Logcat",
                                                                              TitledBorder.DEFAULT_JUSTIFICATION,
                                                                              TitledBorder.DEFAULT_POSITION, null, null));
    final Spacer spacer5 = new Spacer();
    panel3.add(spacer5, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                            GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    myClearLogCheckBox = new JCheckBox();
    loadButtonText(myClearLogCheckBox, getMessageFromBundle("messages/AndroidBundle",
                                                                                  "android.run.configuration.logcat.skip.content.label"));
    panel3.add(myClearLogCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myShowLogcatCheckBox = new JCheckBox();
    myShowLogcatCheckBox.setText("Show logcat automatically");
    myShowLogcatCheckBox.setMnemonic('S');
    myShowLogcatCheckBox.setDisplayedMnemonicIndex(0);
    panel3.add(myShowLogcatCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myModuleJBLabel.setLabelFor(myModulesComboBox);
  }

  private static Method cachedGetBundleMethod = null;

  private String getMessageFromBundle(String path, String key) {
    ResourceBundle bundle;
    try {
      Class<?> thisClass = this.getClass();
      if (cachedGetBundleMethod == null) {
        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
        cachedGetBundleMethod = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
      }
      bundle = (ResourceBundle)cachedGetBundleMethod.invoke(null, path, thisClass);
    }
    catch (Exception e) {
      bundle = ResourceBundle.getBundle(path);
    }
    return bundle.getString(key);
  }

  private void loadButtonText(AbstractButton component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }
}
