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
package com.android.tools.idea.run;

import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.SimpleTextAttributes;
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
 * {@link CloudMatrixConfigurationComboBox} displays the list of device matrix configurations obtained from the cloud provider
 * in a ComboboxWithBrowseButton.
 *
 * When the browse action is invoked, it delegates to the provider to display a more detailed dialog where the list of device matrices
 * can be updated. The model is updated on return from that dialog, since users could have changed the list of configurations. This
 * change is also propagated to other instances of this class via the {@link CloudMatrixConfigurationCoordinator}.
 */
public class CloudMatrixConfigurationComboBox extends ComboboxWithBrowseButton {
  private Module myCurrentModule;
  private AndroidFacet myCurrentFacet;
  private List<? extends CloudTestConfiguration> myTestingConfigurations;
  private ActionListener myActionListener;
  private final CloudTestConfigurationProvider myConfigurationProvider;

  // a cache of configuration selected by module, so that if the module selection changes back and forth, we retain
  // the appropriate selected test configuration
  private Map<Module, CloudTestConfiguration> myConfigurationByModuleCache = Maps.newHashMapWithExpectedSize(5);

  public CloudMatrixConfigurationComboBox() {
    myConfigurationProvider = CloudTestConfigurationProvider.getCloudTestingProvider();
    setMinimumSize(new Dimension(100, getMinimumSize().height));

    getComboBox().setRenderer(new TestConfigurationRenderer());
    getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Object item = getComboBox().getSelectedItem();
        if (item instanceof CloudTestConfiguration) {
          myConfigurationByModuleCache.put(myCurrentModule, (CloudTestConfiguration)item);
        }
      }
    });

    CloudMatrixConfigurationCoordinator.getInstance().addComboBox(this);
  }

  public void setFacet(@NotNull AndroidFacet facet) {
    if (!CloudTestConfigurationProvider.SHOW_CLOUD_TESTING_OPTION) {
      return; // Running tests in cloud is not enabled!
    }

    myCurrentFacet = facet;
    myCurrentModule = myCurrentFacet.getModule();
    myTestingConfigurations = myConfigurationProvider.getTestingConfigurations(myCurrentFacet);

    // Since setFacet can be called multiple times, make sure to remove any previously registered listeners.
    removeActionListener(myActionListener);

    myActionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        CloudTestConfiguration selectedConfig = myConfigurationProvider
          .openMatrixConfigurationDialog(myCurrentFacet, (CloudTestConfiguration)getComboBox().getSelectedItem());
        if (selectedConfig != null) {
          CloudMatrixConfigurationCoordinator.getInstance().
            updateComboBoxesWithNewTestingConfigurations(myConfigurationProvider.getTestingConfigurations(myCurrentFacet), myCurrentModule);
          getComboBox().setSelectedItem(selectedConfig);
          getComboBox().updateUI();
        }
      }
    };
    addActionListener(myActionListener);

    updateContent();
  }

  @Override
  public void dispose() {
    CloudMatrixConfigurationCoordinator.getInstance().removeComboBox(this);
    super.dispose();
  }

  public void selectConfiguration(int id) {
    if (myTestingConfigurations != null) {
      for (CloudTestConfiguration configuration : myTestingConfigurations) {
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
    CloudTestConfiguration selectedConfig = myConfigurationByModuleCache.get(myCurrentModule);
    if (selectedConfig != null && myTestingConfigurations.contains(selectedConfig)) {
      getComboBox().setSelectedItem(selectedConfig);
    }
  }

  public void updateTestingConfigurations(List<? extends CloudTestConfiguration> testingConfigurations, Module module) {
    if (myCurrentFacet != null && myCurrentModule.equals(module)) {
      myTestingConfigurations = testingConfigurations;
      int selectedConfigurationId = -1;
      Object selectedItem = getComboBox().getSelectedItem();
      if (selectedItem != null) {
        selectedConfigurationId = ((CloudTestConfiguration) selectedItem).getId();
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
      } else if (value instanceof CloudTestConfiguration) {
        CloudTestConfiguration config = (CloudTestConfiguration)value;
        append(config.getDisplayName(),
               config.getDeviceConfigurationCount() < 1 ? SimpleTextAttributes.ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        setIcon(config.getIcon());
      }
    }
  }
}
