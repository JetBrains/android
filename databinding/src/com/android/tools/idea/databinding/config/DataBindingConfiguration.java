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
package com.android.tools.idea.databinding.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
  name = "DataBindingConfiguration",
  storages = @Storage(value = "databinding.xml", roamingType = RoamingType.DISABLED)
)
public final class DataBindingConfiguration implements PersistentStateComponent<DataBindingConfiguration> {
  public CodeGenMode CODE_GEN_MODE = CodeGenMode.IN_MEMORY;

  /**
   * Legacy field. Do not use.
   *
   * @deprecated Use {@link #CODE_GEN_MODE} instead.
   */
  @Deprecated
  public CodeNavigationMode CODE_NAVIGATION_MODE = CodeNavigationMode.DEFAULT;

  @NotNull
  public static DataBindingConfiguration getInstance() {
    return ServiceManager.getService(DataBindingConfiguration.class);
  }

  @Nullable
  @Override
  public DataBindingConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull DataBindingConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);

    // The following logic allows migrating from the legacy CODE_NAVIGATION_MODE value, if present,
    // to CODE_GEN_MODE. CODE_NAVIGATION_MODE will only be non-default if we're loading a settings
    // file saved by a previous version of Studio.
    switch (CODE_NAVIGATION_MODE) {
      case CODE:
        CODE_GEN_MODE = CodeGenMode.ON_DISK;
        break;
      case XML:
        CODE_GEN_MODE = CodeGenMode.IN_MEMORY;
        break;
      default:
        break;
    }
    CODE_NAVIGATION_MODE = CodeNavigationMode.DEFAULT;
  }

  @Deprecated
  public enum CodeNavigationMode {
    // Not explicitly set
    DEFAULT,
    // don't generate in memory classes
    CODE,
    // don't allow going to the generated code
    XML
  }

  public enum CodeGenMode {
    /**
     * Generated code should be written to disk.
     */
    ON_DISK,

    /**
     * Generated code should be created in memory and not backed by disk.
     */
    IN_MEMORY,
  }

}
