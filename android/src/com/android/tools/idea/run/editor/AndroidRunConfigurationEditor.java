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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.fd.InstantRunConfigurable;
import com.android.tools.idea.fd.gradle.InstantRunGradleUtils;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.ConfigurationSpecificEditor;
import com.android.tools.idea.run.ValidationError;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.ex.ConfigurableCardPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.PlatformColors;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class AndroidRunConfigurationEditor<T extends AndroidRunConfigurationBase> extends SettingsEditor<T> implements PanelWithAnchor,
                                                                                                                       HyperlinkListener,
                                                                                                                       ActionListener,
                                                                                                                       GradleSyncListener {
  private JPanel myPanel;
  protected JBTabbedPane myTabbedPane;
  private JBLabel myModuleJBLabel;
  private ModulesComboBox myModulesComboBox;

  // application run parameters or test run parameters
  private JPanel myConfigurationSpecificPanel;

  // deploy options
  private ConfigurableCardPanel myDeployTargetConfigurableCardPanel;
  private ComboBox myDeploymentTargetCombo;

  // Misc. options tab
  private JCheckBox myClearLogCheckBox;
  private JCheckBox myShowLogcatCheckBox;
  private JCheckBox mySkipNoOpApkInstallation;
  private JCheckBox myForceStopRunningApplicationCheckBox;
  private HyperlinkLabel myOldVersionLabel;

  private JComponent anchor;

  private final ConfigurationModuleSelector myModuleSelector;
  private ConfigurationSpecificEditor<T> myConfigurationSpecificEditor;

  private final ImmutableMap<String, DeployTargetConfigurableWrapper> myDeployTargetConfigurables;
  private final List<DeployTargetProvider> myApplicableDeployTargetProviders;

  private AndroidDebuggerPanel myAndroidDebuggerPanel;
  private final AndroidProfilersPanel myAndroidProfilersPanel;

  public AndroidRunConfigurationEditor(final Project project, final Predicate<AndroidFacet> libraryProjectValidator, T config) {
    Disposer.register(project, this);
    myModuleSelector = new ConfigurationModuleSelector(project, myModulesComboBox) {
      @Override
      public boolean isModuleAccepted(Module module) {
        if (module == null || !super.isModuleAccepted(module)) {
          return false;
        }

        final AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet == null) {
          return false;
        }

        return !facet.isLibraryProject() || libraryProjectValidator.apply(facet);
      }
    };
    myModulesComboBox.addActionListener(this);

    myApplicableDeployTargetProviders = ImmutableList.copyOf(config.getApplicableDeployTargetProviders());
    DeployTargetConfigurableContext context = new RunConfigurationEditorContext(myModuleSelector, myModulesComboBox);
    ImmutableMap.Builder<String, DeployTargetConfigurableWrapper> builder = ImmutableMap.builder();
    for (DeployTargetProvider target : myApplicableDeployTargetProviders) {
      builder.put(target.getId(), new DeployTargetConfigurableWrapper(project, this, context, target));
    }
    myDeployTargetConfigurables = builder.build();

    myDeploymentTargetCombo.setModel(new CollectionComboBoxModel(myApplicableDeployTargetProviders));
    myDeploymentTargetCombo.setRenderer(new DeployTargetProvider.Renderer());
    myDeploymentTargetCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        DeployTargetProvider target = (DeployTargetProvider)myDeploymentTargetCombo.getSelectedItem();
        if (target != null) {
          myDeployTargetConfigurableCardPanel.select(myDeployTargetConfigurables.get(target.getId()), true);
        }
      }
    });

    ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (mySkipNoOpApkInstallation == e.getSource()) {
          myForceStopRunningApplicationCheckBox.setEnabled(mySkipNoOpApkInstallation.isSelected());
        }
      }
    };
    mySkipNoOpApkInstallation.addActionListener(actionListener);

    AndroidDebuggerContext androidDebuggerContext = config.getAndroidDebuggerContext();
    myModulesComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        androidDebuggerContext.setDebuggeeModule(myModulesComboBox.getSelectedModule());
      }
    });

    if (androidDebuggerContext.getAndroidDebuggers().size() > 1) {
      myAndroidDebuggerPanel = new AndroidDebuggerPanel(config, androidDebuggerContext);
      myTabbedPane.add("Debugger", myAndroidDebuggerPanel.getComponent());
    }

    myAndroidProfilersPanel = new AndroidProfilersPanel(project, config.getProfilerState());
    myTabbedPane.add("Profiling", myAndroidProfilersPanel.getComponent());

    checkValidationResults(config.validate(null));
  }

  public void setConfigurationSpecificEditor(ConfigurationSpecificEditor<T> configurationSpecificEditor) {
    myConfigurationSpecificEditor = configurationSpecificEditor;
    myConfigurationSpecificPanel.add(configurationSpecificEditor.getComponent());
    setAnchor(myConfigurationSpecificEditor.getAnchor());
    myShowLogcatCheckBox.setVisible(configurationSpecificEditor instanceof ApplicationRunParameters);
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

    DeployTargetContext deployTargetContext = configuration.getDeployTargetContext();
    myDeploymentTargetCombo.setSelectedItem(deployTargetContext.getCurrentDeployTargetProvider());
    for (DeployTargetProvider target : myApplicableDeployTargetProviders) {
      DeployTargetState state = deployTargetContext.getDeployTargetState(target);
      myDeployTargetConfigurables.get(target.getId()).resetFrom(state, configuration.getUniqueID());
    }

    myClearLogCheckBox.setSelected(configuration.CLEAR_LOGCAT);
    myShowLogcatCheckBox.setSelected(configuration.SHOW_LOGCAT_AUTOMATICALLY);
    mySkipNoOpApkInstallation.setSelected(configuration.SKIP_NOOP_APK_INSTALLATIONS);
    myForceStopRunningApplicationCheckBox.setSelected(configuration.FORCE_STOP_RUNNING_APP);

    myConfigurationSpecificEditor.resetFrom(configuration);

    if (myAndroidDebuggerPanel != null) {
      myAndroidDebuggerPanel.resetFrom(configuration.getAndroidDebuggerContext());
    }
    myAndroidProfilersPanel.resetFrom(configuration.getProfilerState());
  }

  @Override
  protected void applyEditorTo(@NotNull T configuration) throws ConfigurationException {
    myModuleSelector.applyTo(configuration);

    DeployTargetContext deployTargetContext = configuration.getDeployTargetContext();
    deployTargetContext.setTargetSelectionMode((DeployTargetProvider)myDeploymentTargetCombo.getSelectedItem());
    for (DeployTargetProvider target : myApplicableDeployTargetProviders) {
      DeployTargetState state = deployTargetContext.getDeployTargetState(target);
      if (target != null) {
        myDeployTargetConfigurables.get(target.getId()).applyTo(state, configuration.getUniqueID());
      }
    }

    configuration.CLEAR_LOGCAT = myClearLogCheckBox.isSelected();
    configuration.SHOW_LOGCAT_AUTOMATICALLY = myShowLogcatCheckBox.isSelected();
    configuration.SKIP_NOOP_APK_INSTALLATIONS = mySkipNoOpApkInstallation.isSelected();
    configuration.FORCE_STOP_RUNNING_APP = myForceStopRunningApplicationCheckBox.isSelected();

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

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == myModulesComboBox) {
      updateLinkState();
      if (myConfigurationSpecificEditor instanceof ApplicationRunParameters) {
        ((ApplicationRunParameters)myConfigurationSpecificEditor).onModuleChanged();
      }
    }
  }

  private void createUIComponents() {
    // JBColor keeps a strong reference to its parameter func, so, using a lambda avoids this reference and fixes a leak
    myOldVersionLabel = new HyperlinkLabel();

    setSyncLinkMessage("");
    myOldVersionLabel.addHyperlinkListener(this);
  }

  private void setSyncLinkMessage(@NotNull String syncMessage) {
    myOldVersionLabel.setHyperlinkText("Instant Run requires a newer version of the Gradle plugin. ", "Update Project", " " + syncMessage);
    myOldVersionLabel.repaint();
  }

  @Override
  public void hyperlinkUpdate(HyperlinkEvent e) {
    Project project = getModuleSelector().getModule().getProject();
    if (!InstantRunConfigurable.updateProjectToInstantRunTools(project, this)) {
      setSyncLinkMessage("Error updating to new Gradle version");
    }
  }

  @Override
  public void syncStarted(@NotNull Project project) {
    setSyncLinkMessage("(Syncing)");
  }

  @Override
  public void setupStarted(@NotNull Project project) {
  }

  @Override
  public void syncSucceeded(@NotNull Project project) {
    syncFinished();
  }

  @Override
  public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
    setSyncLinkMessage("(Sync Failed)");
    syncFinished();
  }

  @Override
  public void syncSkipped(@NotNull Project project) {
    syncFinished();
  }

  private void syncFinished() {
    updateLinkState();
  }

  private void updateLinkState() {
    Module module = getModuleSelector().getModule();
    if (module == null) {
      myOldVersionLabel.setVisible(false);
      return;
    }

    AndroidModuleModel model = AndroidModuleModel.get(module);
    if (model == null || InstantRunGradleUtils.modelSupportsInstantRun(model)) {
      myOldVersionLabel.setVisible(false);
      return;
    }

    myOldVersionLabel.setVisible(true);
  }

  @VisibleForTesting
  public ConfigurationSpecificEditor<T> getConfigurationSpecificEditor() {
    return myConfigurationSpecificEditor;
  }
}
