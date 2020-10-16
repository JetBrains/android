/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.cpu.config;

import com.android.tools.adtui.stdui.menu.CommonSeparatorUI;
import com.android.tools.adtui.util.SwingUtil;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.JComboBoxView;
import com.android.tools.profilers.ProfilerCombobox;
import com.android.tools.profilers.ProfilerComboboxCellRenderer;
import com.android.tools.profilers.cpu.CpuProfilerAspect;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.google.common.annotations.VisibleForTesting;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JSeparator;
import org.jetbrains.annotations.NotNull;

public class CpuProfilingConfigurationView {
  /**
   * Fake configuration to represent "Edit configurations..." entry on the profiling configurations combobox.
   */
  static final ProfilingConfiguration EDIT_CONFIGURATIONS_ENTRY = new UnspecifiedConfiguration("Edit Configurations...");

  /**
   * Fake configuration to represent a separator on the profiling configurations combobox.
   */
  static final ProfilingConfiguration CONFIG_SEPARATOR_ENTRY = new UnspecifiedConfiguration("Configuration Separator Entry");

  /**
   * A fake configuration shown when an API-initiated tracing is in progress. It exists for UX purpose only and isn't something
   * we want to preserve across stages. Therefore, it exists inside {@link CpuProfilerStage}.
   */
  private final ProfilingConfiguration API_INITIATED_TRACING_PROFILING_CONFIG =
    new ArtInstrumentedConfiguration("Debug API (Java)");

  @NotNull private final CpuProfilerStage myStage;
  @NotNull private final IdeProfilerComponents myIdeProfilerComponents;
  @NotNull private final JComboBox<ProfilingConfiguration> myComboBox;

  public CpuProfilingConfigurationView(@NotNull CpuProfilerStage stage, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myStage = stage;
    myIdeProfilerComponents = ideProfilerComponents;
    myComboBox = new ProfilerCombobox<>(new DefaultComboBoxModel<ProfilingConfiguration>() {
      @Override
      public void setSelectedItem(Object item) {
        if (item == CONFIG_SEPARATOR_ENTRY) {
          return;
        }
        super.setSelectedItem(item);
      }
    });
    configureProfilingConfigCombo();
  }

  @NotNull
  public JComponent getComponent() {
    return myComboBox;
  }

  @VisibleForTesting
  @NotNull
  public ProfilingConfiguration getProfilingConfiguration() {
    // Show fake, short-lived API_INITIATED_TRACING_PROFILING_CONFIG while API-initiated tracing is in progress.
    if (myStage.isApiInitiatedTracingInProgress()) {
      return API_INITIATED_TRACING_PROFILING_CONFIG;
    }
    return myStage.getProfilerConfigModel().getProfilingConfiguration();
  }

  @VisibleForTesting
  void setProfilingConfiguration(@NotNull ProfilingConfiguration mode) {
    if (mode == EDIT_CONFIGURATIONS_ENTRY) {
      openProfilingConfigurationsDialog();
    }
    else if (mode != CONFIG_SEPARATOR_ENTRY) {
      myStage.getProfilerConfigModel().setProfilingConfiguration(mode);
    }
  }

  @VisibleForTesting
  void openProfilingConfigurationsDialog() {
    Consumer<ProfilingConfiguration> dialogCallback = (configuration) -> {
      // Update the config list to pick up any potential changes.
      myStage.getProfilerConfigModel().updateProfilingConfigurations();
      // If there was a configuration selected when the dialog was closed,
      // make sure to select it in the combobox
      if (configuration != null) {
        setProfilingConfiguration(configuration);
      }
    };
    Common.Device selectedDevice = myStage.getStudioProfilers().getDevice();
    int deviceFeatureLevel = selectedDevice != null ? selectedDevice.getFeatureLevel() : 0;
    myIdeProfilerComponents.openCpuProfilingConfigurationsDialog(myStage.getProfilerConfigModel(), deviceFeatureLevel, dialogCallback);
    myStage.getStudioProfilers().getIdeServices().getFeatureTracker().trackOpenProfilingConfigDialog();
  }

  /**
   * Query custom/default configs from the model and assemble the list that will be displayed in the combo box (including the fake entries
   * created for UI purposes).
   *
   * @return a {@link List} of configuration entries for {@link JComboBoxView}'s model.
   */
  @NotNull
  @VisibleForTesting
  List<ProfilingConfiguration> getProfilingConfigurations() {
    ArrayList<ProfilingConfiguration> configs = new ArrayList<>();
    // Show fake, short-lived API_INITIATED_TRACING_PROFILING_CONFIG while API-initiated tracing is in progress.
    if (myStage.isApiInitiatedTracingInProgress()) {
      configs.add(API_INITIATED_TRACING_PROFILING_CONFIG);
      return configs;
    }
    configs.add(EDIT_CONFIGURATIONS_ENTRY);

    List<ProfilingConfiguration> customEntries = myStage.getProfilerConfigModel().getCustomProfilingConfigurationsDeviceFiltered();
    if (!customEntries.isEmpty()) {
      configs.add(CONFIG_SEPARATOR_ENTRY);
      configs.addAll(customEntries);
    }
    configs.add(CONFIG_SEPARATOR_ENTRY);
    configs.addAll(myStage.getProfilerConfigModel().getDefaultProfilingConfigurations());
    return configs;
  }

  private void configureProfilingConfigCombo() {
    JComboBoxView<ProfilingConfiguration, CpuProfilerAspect> profilingConfiguration =
      new JComboBoxView<>(myComboBox, myStage.getAspect(), CpuProfilerAspect.PROFILING_CONFIGURATION,
                          this::getProfilingConfigurations, this::getProfilingConfiguration, this::setProfilingConfiguration);
    profilingConfiguration.bind();
    SwingUtil.doNotSelectItems(myComboBox, e -> e == CONFIG_SEPARATOR_ENTRY);
    myComboBox.setRenderer(new ProfilingConfigurationRenderer());
  }

  private static class ProfilingConfigurationRenderer extends ProfilerComboboxCellRenderer<ProfilingConfiguration> {

    @Override
    public Component getListCellRendererComponent(JList<? extends ProfilingConfiguration> list,
                                                  ProfilingConfiguration value,
                                                  int index,
                                                  boolean selected,
                                                  boolean hasFocus) {
      if (value == CONFIG_SEPARATOR_ENTRY) {
        JSeparator separator = new JSeparator();
        separator.setUI(new CommonSeparatorUI());
        return separator;
      }
      return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends ProfilingConfiguration> list,
                                         ProfilingConfiguration value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      append(value.getName());
    }
  }
}