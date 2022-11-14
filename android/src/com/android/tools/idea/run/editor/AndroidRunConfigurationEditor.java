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

import com.android.tools.idea.projectsystem.ModuleSystemUtil;
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
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.function.Function;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import org.jetbrains.android.facet.AndroidFacet;
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
  private JCheckBox myEnableLayoutInspectionWithoutActivityRestart;
  private JEditorPane myActivityRestartDescription;

  private JComponent anchor;

  private final ConfigurationModuleSelector myModuleSelector;
  private ConfigurationSpecificEditor<T> myConfigurationSpecificEditor;

  private AndroidDebuggerPanel myAndroidDebuggerPanel;
  private final AndroidProfilersPanel myAndroidProfilersPanel;

  public static final String LAYOUT_INSPECTION_WITHOUT_ACTIVITY_RESTART = "Layout Inspection Without Activity Restart";

  public AndroidRunConfigurationEditor(Project project,
                                       Predicate<Module> moduleValidator,
                                       T config,
                                       boolean showLogcatCheckbox,
                                       boolean isAndroidTest,
                                       Function<ConfigurationModuleSelector, ConfigurationSpecificEditor<T>> configurationSpecificEditorFactory) {
    Disposer.register(project, this);
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
    myTabbedPane.add("Profiling", myAndroidProfilersPanel.getComponent());

    myConfigurationSpecificEditor = configurationSpecificEditorFactory.apply(myModuleSelector);
    Disposer.register(this, myConfigurationSpecificEditor);
    myConfigurationSpecificPanel.add(myConfigurationSpecificEditor.getComponent());

    myActivityRestartDescription.setBorder(JBUI.Borders.emptyLeft(24));
    myActivityRestartDescription.setForeground(UIUtil.getContextHelpForeground());
    myActivityRestartDescription.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));

    myShowLogcatCheckBox.setVisible(showLogcatCheckbox);

    checkValidationResults(config.validate(null));
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
    myEnableLayoutInspectionWithoutActivityRestart.setSelected(configuration.INSPECTION_WITHOUT_ACTIVITY_RESTART);
    myEnableLayoutInspectionWithoutActivityRestart.setName(LAYOUT_INSPECTION_WITHOUT_ACTIVITY_RESTART);

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
    configuration.INSPECTION_WITHOUT_ACTIVITY_RESTART = myEnableLayoutInspectionWithoutActivityRestart.isSelected();

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

  private void createUIComponents() {
    myActivityRestartDescription =
      SwingHelper.createHtmlViewer(true, null, UIUtil.getPanelBackground(), UIUtil.getContextHelpForeground());
    myActivityRestartDescription.setText(
      "<html>Enabling this option sets a required global flag on the device at deploy time. This avoids having to later restart the " +
      "activity in order to enable the flag when connecting to the Layout Inspector.<br/>" +
      "An alternative is to activate \"Enable view attribute inspection\" in the developer options on the device. " +
      "<a href=\"https://developer.android.com/r/studio-ui/layout-inspector-activity-restart\">Learn more</a><br/></html>");
    myActivityRestartDescription.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
  }
}
