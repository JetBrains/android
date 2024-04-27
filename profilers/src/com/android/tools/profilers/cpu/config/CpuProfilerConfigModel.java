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

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.CpuProfilerAspect;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

/**
 * This class is responsible for managing the profiling configuration for the CPU profiler. It is shared between
 * {@link CpuProfilerStage} and CpuProfilingConfigurationsDialog.
 */
public class CpuProfilerConfigModel {
  private static final String LAST_SELECTED_CONFIGURATION_NAME = "last.selected.configuration.name";

  /**
   * Represents the selected configuration or the configuration being used in the case of ongoing trace recording.
   * While a trace recording in progress, it is not possible to change {@link #myProfilingConfiguration}, i.e it is achieved
   * by disabling the dropdown in UI. Thus, in most cases the selected configuration is the same as
   * the configuration used for the ongoing recording.
   */
  private ProfilingConfiguration myProfilingConfiguration;

  /**
   * The list of all custom configurations. The list is filtered to configurations that are available, however it does not
   * filter on device support.
   */
  @NotNull
  private List<ProfilingConfiguration> myCustomProfilingConfigurations;

  /**
   * Same as {@link #myCustomProfilingConfigurations} except we also filter on device. This is needed, for instance, to filter custom
   * configurations on the CPU profiling configurations combobox.
   */
  @NotNull
  private List<ProfilingConfiguration> myCustomProfilingConfigurationsDeviceFiltered;

  /**
   * The list of all default configurations. This list is filtered on device compatibility as well as feature availability.
   */
  @NotNull
  private List<ProfilingConfiguration> myDefaultProfilingConfigurations;

  /**
   * The list of all default/modified configurations in task based ux. The list is filtered to configurations that are available,
   * however it does not filter on device support.
   */
  @NotNull
  private List<ProfilingConfiguration> myTaskProfilingConfigurations;

  @NotNull
  private final StudioProfilers myProfilers;

  @NotNull
  private CpuProfilerStage myProfilerStage;

  private AspectObserver myAspectObserver;

  public CpuProfilerConfigModel(@NotNull StudioProfilers profilers, @NotNull CpuProfilerStage profilerStage) {
    myProfilers = profilers;
    myProfilerStage = profilerStage;
    myCustomProfilingConfigurations = new ArrayList<>();
    myCustomProfilingConfigurationsDeviceFiltered = new ArrayList<>();
    myDefaultProfilingConfigurations = new ArrayList<>();
    myTaskProfilingConfigurations = new ArrayList<>();
    myAspectObserver = new AspectObserver();
    myProfilerStage.getAspect().addDependency(myAspectObserver)
      .onChange(CpuProfilerAspect.PROFILING_CONFIGURATION, this::updateProfilingConfigurations);
  }

  @NotNull
  public ProfilingConfiguration getProfilingConfiguration() {
    return myProfilingConfiguration;
  }

  public void setProfilingConfiguration(@NotNull ProfilingConfiguration configuration) {
    myProfilingConfiguration = configuration;
    myProfilerStage.getAspect().changed(CpuProfilerAspect.PROFILING_CONFIGURATION);
    myProfilers.getIdeServices().getTemporaryProfilerPreferences().setValue(LAST_SELECTED_CONFIGURATION_NAME, configuration.getName());
  }

  @NotNull
  public List<ProfilingConfiguration> getCustomProfilingConfigurations() {
    return myCustomProfilingConfigurations;
  }

  @NotNull
  public List<ProfilingConfiguration> getCustomProfilingConfigurationsDeviceFiltered() {
    return myCustomProfilingConfigurationsDeviceFiltered;
  }

  @NotNull
  public List<ProfilingConfiguration> getDefaultProfilingConfigurations() {
    return myDefaultProfilingConfigurations;
  }

  @NotNull
  public List<ProfilingConfiguration> getTaskProfilingConfigurations() {
    return myTaskProfilingConfigurations;
  }

  public void updateProfilingConfigurations() {
    Common.Device selectedDevice = myProfilers.getDevice();
    int featureLevel = selectedDevice != null ? selectedDevice.getFeatureLevel() : 0;

    List<ProfilingConfiguration> savedConfigs = myProfilers.getIdeServices().getUserCpuProfilerConfigs(featureLevel);
    myCustomProfilingConfigurations = filterConfigurations(savedConfigs, false);
    myCustomProfilingConfigurationsDeviceFiltered = filterConfigurations(savedConfigs, true);

    List<ProfilingConfiguration> savedTaskConfigs = myProfilers.getIdeServices().getTaskCpuProfilerConfigs(featureLevel);
    myTaskProfilingConfigurations = filterConfigurations(savedTaskConfigs, false);

    List<ProfilingConfiguration> defaultConfigs = myProfilers.getIdeServices().getDefaultCpuProfilerConfigs(featureLevel);
    myDefaultProfilingConfigurations = filterConfigurations(defaultConfigs, true);

    // Anytime before we check the device feature level we need to validate we have a device. The device will be null in test
    // causing a null pointer exception here.
    boolean isSimpleperfEnabled = selectedDevice != null && selectedDevice.getFeatureLevel() >= AndroidVersion.VersionCodes.O;
    if (myProfilingConfiguration == null) {
      // First we try to get the last selected config.
      String selectedConfigName =
        myProfilers.getIdeServices().getTemporaryProfilerPreferences().getValue(LAST_SELECTED_CONFIGURATION_NAME, "");
      ProfilingConfiguration selectedConfig =
        Stream.concat(defaultConfigs.stream(), savedConfigs.stream())
              .filter(c -> c.getName().equals(selectedConfigName))
              .findFirst()
              .orElse(null);
      if (selectedConfig != null) {
        myProfilingConfiguration = selectedConfig;
      }
      else if (isSimpleperfEnabled) {
        // If simpleperf is supported, we select it.
        myProfilingConfiguration =
          defaultConfigs.stream().filter(pref -> pref instanceof SimpleperfConfiguration).findFirst().orElse(null);
      }
      else {
        // Otherwise, we select ART sampled.
        myProfilingConfiguration =
          defaultConfigs.stream().filter(pref -> pref instanceof ArtSampledConfiguration).findFirst().orElse(null);
      }
    }
  }

  private List<ProfilingConfiguration> filterConfigurations(List<ProfilingConfiguration> configurations, boolean filterOnDevice) {
    Common.Device selectedDevice = myProfilers.getDevice();
    Predicate<ProfilingConfiguration> filter = pref -> {
      if (selectedDevice != null && pref.getRequiredDeviceLevel() > selectedDevice.getFeatureLevel() && filterOnDevice) {
        return false;
      }
      return true;
    };
    return configurations.stream().filter(filter).collect(Collectors.toList());
  }
}