/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.run.cloud;

import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.cloud.CloudConfiguration.Kind;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import org.jdesktop.swingx.combobox.ListComboBoxModel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;

/**
 * {@link CloudConfigurationComboBox} displays the list of cloud configurations obtained from the cloud provider
 * in a ComboboxWithBrowseButton.
 *
 * When the browse action is invoked, it delegates to the provider to display a more detailed dialog where the list of device matrices
 * can be updated. The model is updated on return from that dialog, since users could have changed the list of configurations. This
 * change is also propagated to other instances of this class via the {@link CloudConfigurationCoordinator}.
 */
public class CloudConfigurationComboBox extends ComboboxWithBrowseButton {
  private final Kind myConfigurationKind;
  private AndroidRunConfigurationBase myCurrentAndroidConfiguration;
  private Module myCurrentModule;
  private AndroidFacet myCurrentFacet;
  private List<? extends CloudConfiguration> myTestingConfigurations;
  private ActionListener myActionListener;
  private final CloudConfigurationProvider myConfigurationProvider;

  // Used to keep track of user choices when run config and/or module are not available.
  private static Map<Kind, CloudConfiguration> myLastChosenCloudConfigurationPerKind = Maps.newHashMapWithExpectedSize(5);

  /** A cache of matrix configurations selected by <kind, module> per android run configuration, so that if
   * the android configuration and/or module selections change back and forth, we retain the appropriate selected matrix configuration.
   */
  private static Map<AndroidRunConfigurationBase, Map<Pair<Kind, Module>, CloudConfiguration>>
    myMatrixConfigurationByAndroidConfigurationAndModuleCache = Maps.newHashMapWithExpectedSize(5);

  public CloudConfigurationComboBox(@NotNull Kind configurationKind) {
    myConfigurationKind = configurationKind;
    myConfigurationProvider = CloudConfigurationProvider.getCloudConfigurationProvider();
    setMinimumSize(new Dimension(JBUI.scale(100), getMinimumSize().height));

    getComboBox().setRenderer(new TestConfigurationRenderer());
    getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Object item = getComboBox().getSelectedItem();
        if (item instanceof CloudConfiguration) {
          CloudConfiguration cloudConfiguration = (CloudConfiguration)item;
          myLastChosenCloudConfigurationPerKind.put(myConfigurationKind, cloudConfiguration);
          if (myCurrentAndroidConfiguration != null && myCurrentModule != null) {
            Map<Pair<Kind, Module>, CloudConfiguration> matrixConfigurationByModuleCache =
              myMatrixConfigurationByAndroidConfigurationAndModuleCache.get(myCurrentAndroidConfiguration);
            if (matrixConfigurationByModuleCache == null) {
              matrixConfigurationByModuleCache = Maps.newHashMapWithExpectedSize(5);
              myMatrixConfigurationByAndroidConfigurationAndModuleCache.put(myCurrentAndroidConfiguration, matrixConfigurationByModuleCache);
            }
            matrixConfigurationByModuleCache.put(Pair.create(myConfigurationKind, myCurrentModule), cloudConfiguration);
          }
        }
      }
    });

    CloudConfigurationCoordinator.getInstance(myConfigurationKind).addComboBox(this);
  }

  public void setFacet(@NotNull AndroidFacet facet) {
    if (!CloudConfigurationProvider.isEnabled()) {
      return; // Running tests in cloud is not enabled!
    }

    myCurrentFacet = facet;
    myCurrentModule = myCurrentFacet.getModule();
    myTestingConfigurations = myConfigurationProvider.getCloudConfigurations(myCurrentFacet, myConfigurationKind);

    // Since setFacet can be called multiple times, make sure to remove any previously registered listeners.
    removeActionListener(myActionListener);

    myActionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        CloudConfiguration selectedConfig = myConfigurationProvider
          .openMatrixConfigurationDialog(myCurrentFacet, (CloudConfiguration)getComboBox().getSelectedItem(), myConfigurationKind);
        // Update the comboboxes' contents even if selectedConfig is null since it might mean that a user deleted
        // all configurations in the Matrix Configuration dialog.
        List<? extends CloudConfiguration> cloudConfigurations =
          myConfigurationProvider.getCloudConfigurations(myCurrentFacet, myConfigurationKind);
        CloudConfigurationCoordinator.getInstance(myConfigurationKind)
          .updateComboBoxesWithNewCloudConfigurations(cloudConfigurations, myCurrentModule);
        if (cloudConfigurations.isEmpty() || selectedConfig != null) {
          getComboBox().setSelectedItem(selectedConfig);
        }
        getComboBox().updateUI();
      }
    };
    addActionListener(myActionListener);

    updateContent();
  }

  public void setConfiguration(@NotNull AndroidRunConfigurationBase configuration) {
    myCurrentAndroidConfiguration = configuration;
  }

  @Override
  public void dispose() {
    CloudConfigurationCoordinator.getInstance(myConfigurationKind).removeComboBox(this);
    super.dispose();
  }

  public void selectConfiguration(int id) {
    if (myTestingConfigurations != null) {
      for (CloudConfiguration configuration : myTestingConfigurations) {
        if (configuration.getId() == id) {
          getComboBox().setSelectedItem(configuration);
          return;
        }
      }
    }
  }

  private void updateContent() {
    if (myCurrentModule == null || myCurrentModule.isDisposed()) {
      return;
    }

    getComboBox().setModel(new ListComboBoxModel(myTestingConfigurations));

    if (myCurrentAndroidConfiguration == null) {
      CloudConfiguration cloudConfiguration = myLastChosenCloudConfigurationPerKind.get(myConfigurationKind);
      if (cloudConfiguration != null && myTestingConfigurations.contains(cloudConfiguration)) {
        getComboBox().setSelectedItem(cloudConfiguration);
      }
    } else {
      Map<Pair<Kind, Module>, CloudConfiguration> matrixConfigurationByModuleCache =
        myMatrixConfigurationByAndroidConfigurationAndModuleCache.get(myCurrentAndroidConfiguration);
      if (matrixConfigurationByModuleCache != null) {
        CloudConfiguration selectedConfig = matrixConfigurationByModuleCache.get(Pair.create(myConfigurationKind, myCurrentModule));
        if (selectedConfig != null && myTestingConfigurations.contains(selectedConfig)) {
          getComboBox().setSelectedItem(selectedConfig);
        }
      }
    }
  }

  public void updateCloudConfigurations(List<? extends CloudConfiguration> cloudConfigurations, Module module) {
    if (myCurrentFacet != null && myCurrentModule.equals(module)) {
      myTestingConfigurations = cloudConfigurations;
      int selectedConfigurationId = -1;
      Object selectedItem = getComboBox().getSelectedItem();
      if (selectedItem != null) {
        selectedConfigurationId = ((CloudConfiguration) selectedItem).getId();
      }
      updateContent();
      selectConfiguration(selectedConfigurationId);
    }
  }

  private static class TestConfigurationRenderer extends ColoredListCellRenderer {
    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value == null) {
        append("[none]", SimpleTextAttributes.ERROR_ATTRIBUTES);
      } else if (value instanceof CloudConfiguration) {
        CloudConfiguration config = (CloudConfiguration)value;
        append(config.getDisplayName(),
               config.getDeviceConfigurationCount() < 1 ? SimpleTextAttributes.ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        setIcon(config.getIcon());
      }
    }
  }
}
