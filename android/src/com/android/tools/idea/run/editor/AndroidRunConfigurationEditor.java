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

import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.ConfigurationSpecificEditor;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.ex.ConfigurableCardPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class AndroidRunConfigurationEditor<T extends AndroidRunConfigurationBase> extends SettingsEditor<T> implements PanelWithAnchor {
  private JPanel myPanel;
  protected JBTabbedPane myTabbedPane;
  private JBLabel myModuleJBLabel;
  private JComboBox myModulesComboBox;

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

  private JComponent anchor;

  private final ConfigurationModuleSelector myModuleSelector;
  private ConfigurationSpecificEditor<T> myConfigurationSpecificEditor;

  private final ImmutableMap<String, DeployTargetConfigurableWrapper> myDeployTargetConfigurables;
  private final List<DeployTarget> myApplicableDeployTargets;

  public AndroidRunConfigurationEditor(final Project project, final Predicate<AndroidFacet> libraryProjectValidator, T config) {
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

    myApplicableDeployTargets = ImmutableList.copyOf(config.getApplicableDeployTargets());
    DeployTargetConfigurableContext context = new RunConfigurationEditorContext(myModuleSelector, myModulesComboBox);
    ImmutableMap.Builder<String, DeployTargetConfigurableWrapper> builder = ImmutableMap.builder();
    for (DeployTarget target : myApplicableDeployTargets) {
      builder.put(target.getId(), new DeployTargetConfigurableWrapper(project, this, context, target));
    }
    myDeployTargetConfigurables = builder.build();

    myDeploymentTargetCombo.setModel(new CollectionComboBoxModel(myApplicableDeployTargets));
    myDeploymentTargetCombo.setRenderer(new DeployTarget.Renderer());
    myDeploymentTargetCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        DeployTarget target = (DeployTarget)myDeploymentTargetCombo.getSelectedItem();
        myDeployTargetConfigurableCardPanel.select(myDeployTargetConfigurables.get(target.getId()), true);
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
  }

  public void setConfigurationSpecificEditor(ConfigurationSpecificEditor<T> configurationSpecificEditor) {
    myConfigurationSpecificEditor = configurationSpecificEditor;
    myConfigurationSpecificPanel.add(configurationSpecificEditor.getComponent());
    setAnchor(myConfigurationSpecificEditor.getAnchor());
    myShowLogcatCheckBox.setVisible(configurationSpecificEditor instanceof ApplicationRunParameters);
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
  protected void resetEditorFrom(T configuration) {
    // Set configurations before resetting the module selector to avoid premature calls to setFacet.
    myModuleSelector.reset(configuration);

    myDeploymentTargetCombo.setSelectedItem(configuration.getCurrentDeployTarget());
    for (DeployTarget target : myApplicableDeployTargets) {
      DeployTargetState state = configuration.getDeployTargetState(target);
      myDeployTargetConfigurables.get(target.getId()).resetFrom(state, configuration.getUniqueID());
    }

    myClearLogCheckBox.setSelected(configuration.CLEAR_LOGCAT);
    myShowLogcatCheckBox.setSelected(configuration.SHOW_LOGCAT_AUTOMATICALLY);
    mySkipNoOpApkInstallation.setSelected(configuration.SKIP_NOOP_APK_INSTALLATIONS);
    myForceStopRunningApplicationCheckBox.setSelected(configuration.FORCE_STOP_RUNNING_APP);

    myConfigurationSpecificEditor.resetFrom(configuration);
  }

  @Override
  protected void applyEditorTo(T configuration) throws ConfigurationException {
    myModuleSelector.applyTo(configuration);

    configuration.setTargetSelectionMode((DeployTarget)myDeploymentTargetCombo.getSelectedItem());
    for (DeployTarget target : myApplicableDeployTargets) {
      DeployTargetState state = configuration.getDeployTargetState(target);
      myDeployTargetConfigurables.get(target.getId()).applyTo(state, configuration.getUniqueID());
    }

    configuration.CLEAR_LOGCAT = myClearLogCheckBox.isSelected();
    configuration.SHOW_LOGCAT_AUTOMATICALLY = myShowLogcatCheckBox.isSelected();
    configuration.SKIP_NOOP_APK_INSTALLATIONS = mySkipNoOpApkInstallation.isSelected();
    configuration.FORCE_STOP_RUNNING_APP = myForceStopRunningApplicationCheckBox.isSelected();

    myConfigurationSpecificEditor.applyTo(configuration);
  }

  @Override
  @NotNull
  protected JComponent createEditor() {
    return myPanel;
  }

  public ConfigurationModuleSelector getModuleSelector() {
    return myModuleSelector;
  }
}
