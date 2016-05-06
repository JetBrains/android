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

import com.android.tools.idea.editors.gfxtrace.gapi.GapiPaths;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import java.util.Properties;

import static com.android.tools.idea.startup.GradleSpecificInitializer.ENABLE_EXPERIMENTAL_PROFILING;

/**
 * Holds all the project persisted state variables for the profilers.
 */
public class ProfilerState {
  public static final String GAPID_LAUNCH_ACTIVITY = "com.google.android.gfxtracer.GfxTracer";

  /** Whether to apply the profiling plugin. */
  public boolean ENABLE_ADVANCED_PROFILING = true;
  public static final String ENABLE_ADVANCED_PROFILING_NAME = "android.profiler.enabled";

  /** Enable GAPID (GPU) tracing. */
  public boolean GAPID_ENABLED = false;
  public static final String GAPID_ENABLED_NAME = "android.profiler.gapid.enabled";

  /** GAPID disable pre-compiled shader support. */
  public boolean GAPID_DISABLE_PCS = false;
  public static final String GAPID_DISABLE_PCS_NAME = "android.profiler.gapid.disable_pcs";

  public static final String GAPID_AAR_PATH_NAME = "android.profiler.gapid.tracer_aar";  // Hidden setting.

  public boolean SUPPORT_LIB_ENABLED = true;
  private static final String SUPPORT_LIB_ENABLED_NAME = "android.profiler.supportLib.enabled";

  public boolean INSTRUMENTATION_ENABLED = true;
  private static final String INSTRUMENTATION_ENABLED_NAME = "android.profiler.instrumentation.enabled";

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
    result.setProperty(SUPPORT_LIB_ENABLED_NAME, String.valueOf(SUPPORT_LIB_ENABLED));
    result.setProperty(INSTRUMENTATION_ENABLED_NAME, String.valueOf(INSTRUMENTATION_ENABLED));
    result.setProperty(ENABLE_ADVANCED_PROFILING_NAME, String.valueOf(ENABLE_ADVANCED_PROFILING));
    result.setProperty(GAPID_ENABLED_NAME, String.valueOf(GAPID_ENABLED));
    result.setProperty(GAPID_DISABLE_PCS_NAME, String.valueOf(GAPID_DISABLE_PCS));
    result.setProperty(GAPID_AAR_PATH_NAME, GapiPaths.findTracerAar().getAbsolutePath());
    return result;
  }

  public boolean isGapidEnabled() {
    return System.getProperty(ENABLE_EXPERIMENTAL_PROFILING) != null && GAPID_ENABLED;
  }
}
