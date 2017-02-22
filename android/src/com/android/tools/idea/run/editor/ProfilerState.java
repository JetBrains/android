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

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Properties;

/**
 * Holds all the project persisted state variables for the profilers.
 */
public class ProfilerState {
  @NonNls public static final boolean EXPERIMENTAL_PROFILING_FLAG_ENABLED = System.getProperty("enable.experimental.profiling") != null;

  public static final String ANDROID_CUSTOM_CLASS_TRANSFORMS = "android.custom.class.transforms";

  /** Whether to apply the profiling transform. */
  public boolean ENABLE_ADVANCED_PROFILING = true;
  public static final String ENABLE_ADVANCED_PROFILING_NAME = "android.profiler.enabled";

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
    result.setProperty(ENABLE_ADVANCED_PROFILING_NAME, String.valueOf(ENABLE_ADVANCED_PROFILING));
    return result;
  }
}
