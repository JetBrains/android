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
package com.android.tools.idea.run.editor;

import com.android.tools.idea.run.StartupCpuProfilingConfiguration;
import com.android.tools.idea.run.ValidationError;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * Holds all the project persisted state variables for the profilers.
 */
public class ProfilerState {
  public static final String ANDROID_ADVANCED_PROFILING_TRANSFORMS = "android.advanced.profiling.transforms";

  /**
   * Whether to apply the profiling transform.
   */
  public boolean ADVANCED_PROFILING_ENABLED = false;
  public static final String ENABLE_ADVANCED_PROFILING_NAME = "android.profiler.enabled";

  public boolean STARTUP_CPU_PROFILING_ENABLED = false;
  public String STARTUP_CPU_PROFILING_CONFIGURATION_NAME = StartupCpuProfilingConfiguration.DEFAULT_CONFIGS.get(0).getName();

  private boolean PROFILING_OKHTTP_ENABLED = true;
  public static final String ENABLE_ADVANCED_OKHTTP_PROFILING_NAME = "android.profiler.okhttp.enabled";

  private boolean PROFILING_IO_ENABLED = false;
  public static final String ENABLE_IO_PROFILING_NAME = "android.profiler.io.enabled";

  private boolean myCheckAdvancedProfiling;

  /**
   * Reads the state from the {@link Element}, overwriting all member values.
   */
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  /**
   * Writes the state to the {@link Element}.
   */
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public Properties toProperties() {
    Properties result = new Properties();
    result.setProperty(ENABLE_ADVANCED_PROFILING_NAME, String.valueOf(ADVANCED_PROFILING_ENABLED));
    result.setProperty(ENABLE_ADVANCED_OKHTTP_PROFILING_NAME, String.valueOf(PROFILING_OKHTTP_ENABLED));
    result.setProperty(ENABLE_IO_PROFILING_NAME, String.valueOf(PROFILING_IO_ENABLED));
    return result;
  }

  public void setCheckAdvancedProfiling(boolean checkAdvancedProfiling) {
    myCheckAdvancedProfiling = checkAdvancedProfiling;
  }

  @NotNull
  public List<ValidationError> validate() {
    List<ValidationError> errors = new LinkedList<>();
    if (myCheckAdvancedProfiling) {
      // Currently we do not check against the state of ADVANCED_PROFILING_ENABLED.
      // As far as the profilers are concerned, the Run Configuration dialog needs to navigate to the profiling tab in any case based on
      // this ValidationError, so users can obtain more info related to the profiler's configurations.
      errors.add(ValidationError.info("Check advanced profiling status", ValidationError.Category.PROFILER, null));
    }

    return errors;
  }
}
