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

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
  name = "DataBindingConfiguration",
  storages = @Storage(value = "databinding.xml", roamingType = RoamingType.DISABLED)
)
public class DataBindingConfiguration implements PersistentStateComponent<DataBindingConfiguration> {
  public CodeNavigationMode CODE_NAVIGATION_MODE = CodeNavigationMode.XML;
  public static DataBindingConfiguration getInstance() {
    return ServiceManager.getService(DataBindingConfiguration.class);
  }

  public static DataBindingConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, DataBindingConfiguration.class);
  }
  @Nullable
  @Override
  public DataBindingConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull DataBindingConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public enum  CodeNavigationMode {
    // don't generate in memory classes
    CODE,
    // don't allow going to the generated code
    XML
  }
}
