/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartRequest;
import com.android.tools.profilers.StudioProfilers;
import com.google.common.collect.Iterables;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * This class is responsible for managing the profiling configuration for the CPU profiler. It is shared between
 * {@link CpuProfilerStage} and CpuProfilingConfigurationsDialog.
 */
public class CpuProfilerConfigModel {
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
   * {@link ProfilingConfiguration} object that stores the data (profiler type, profiling mode, sampling interval and buffer size limit)
   * that should be used in the next stopProfiling call. This field is required because stopProfiling should receive the same profiler type
   * as the one passed to startProfiling. Also, in stopProfiling we track the configurations used to capture.
   * We can't use {@link #myProfilingConfiguration} because it can be changed when we exit the Stage or change its value using the combobox.
   * Using a separate field, we can retrieve the relevant data from device in {@link #updateProfilingState()}.
   */
  private ProfilingConfiguration myActiveConfig;

  @NotNull
  private final StudioProfilers myProfilers;

  private AspectObserver myAspectObserver;

  public CpuProfilerConfigModel(@NotNull StudioProfilers profilers, CpuProfilerStage profilerStage) {
    myProfilers = profilers;
    myCustomProfilingConfigurations = new ArrayList<>();
    myCustomProfilingConfigurationsDeviceFiltered = new ArrayList<>();
    myDefaultProfilingConfigurations = new ArrayList<>();
    myAspectObserver = new AspectObserver();

    profilerStage.getAspect().addDependency(myAspectObserver)
      .onChange(CpuProfilerAspect.PROFILING_CONFIGURATION, this::updateProfilingConfigurations);
  }

  public void setActiveConfig(CpuProfilerType profilerType, CpuProfilingAppStartRequest.Mode mode,
                              int bufferSizeLimitMb, int samplingIntervalUs) {
    // The configuration name field is not actually used when retrieving the active configuration. The reason behind that is configurations,
    // including their name, can be edited when a capture is still in progress. We only need to store the parameters used when starting
    // the capture (i.e. buffer size, profiler type, profiling mode and sampling interval). The capture name is not used on the server side
    // when starting a capture, so we simply ignore it here as well.
    String anyConfigName = "Current config";
    myActiveConfig = new ProfilingConfiguration(anyConfigName, profilerType, mode);
    myActiveConfig.setProfilingBufferSizeInMb(bufferSizeLimitMb);
    myActiveConfig.setProfilingSamplingIntervalUs(samplingIntervalUs);
  }

  public ProfilingConfiguration getActiveConfig() {
    return myActiveConfig;
  }

  @NotNull
  public ProfilingConfiguration getProfilingConfiguration() {
    return myProfilingConfiguration;
  }

  public void setProfilingConfiguration(@NotNull ProfilingConfiguration mode) {
    myProfilingConfiguration = mode;
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

  public void updateProfilingConfigurations() {
    List<ProfilingConfiguration> savedConfigs = myProfilers.getIdeServices().getCpuProfilingConfigurations();
    myCustomProfilingConfigurations = filterConfigurations(savedConfigs, false);
    myCustomProfilingConfigurationsDeviceFiltered = filterConfigurations(savedConfigs, true);

    List<ProfilingConfiguration> defaultConfigs = ProfilingConfiguration.getDefaultProfilingConfigurations();
    myDefaultProfilingConfigurations = filterConfigurations(defaultConfigs, true);

    Common.Device selectedDevice = myProfilers.getDevice();
    // Anytime before we check the device feature level we need to validate we have a device. The device will be null in test
    // causing a null pointer exception here.
    boolean isSimplePerfEnabled = selectedDevice != null && selectedDevice.getFeatureLevel() >= AndroidVersion.VersionCodes.O &&
                                  myProfilers.getIdeServices().getFeatureConfig().isSimplePerfEnabled();
    if (myProfilingConfiguration == null) {
      // TODO (b/68691584): Remember the last selected configuration and suggest it instead of default configurations.
      // If there is a preference for a native configuration, we select simpleperf.
      if (myProfilers.getIdeServices().isNativeProfilingConfigurationPreferred() && isSimplePerfEnabled) {
        myProfilingConfiguration =
          Iterables.find(defaultConfigs, pref -> pref != null && pref.getProfilerType() == CpuProfilerType.SIMPLEPERF);
      }
      // Otherwise we select ART sampled.
      else {
        myProfilingConfiguration =
          Iterables.find(defaultConfigs, pref -> pref != null && pref.getProfilerType() == CpuProfilerType.ART
                                                 && pref.getMode() == CpuProfilingAppStartRequest.Mode.SAMPLED);
      }
    }
  }

  private List<ProfilingConfiguration> filterConfigurations(List<ProfilingConfiguration> configurations, boolean filterOnDevice) {
    // Simpleperf/Atrace profiling is not supported by devices older than O
    Common.Device selectedDevice = myProfilers.getDevice();
    Predicate<ProfilingConfiguration> filter = pref -> {
      if (selectedDevice != null && pref.getRequiredDeviceLevel() > selectedDevice.getFeatureLevel() && filterOnDevice) {
        return false;
      }
      if (pref.getProfilerType() == CpuProfilerType.SIMPLEPERF) {
        return myProfilers.getIdeServices().getFeatureConfig().isSimplePerfEnabled();
      }
      if (pref.getProfilerType() == CpuProfilerType.ATRACE) {
        return myProfilers.getIdeServices().getFeatureConfig().isAtraceEnabled();
      }
      return true;
    };
    return configurations.stream().filter(filter).collect(Collectors.toList());
  }
}