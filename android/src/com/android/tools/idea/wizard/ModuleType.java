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
package com.android.tools.idea.wizard;

import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
* A entry used by {@link com.android.tools.idea.wizard.ChooseModuleTypeStep} to represent an option for a module
 * type to create
*/
class ModuleType {
  public final TemplateMetadata templateMetadata;
  public final FormFactorUtils.FormFactor formFactor;
  private String myName;
  private boolean myDoesCreate;
  public Map<ScopedStateStore.Key<?>, Object> customValues = Maps.newHashMap();

  public ModuleType(@NotNull TemplateMetadata metadata, boolean creates) {
    this(metadata, null, metadata.getTitle(), creates);
  }

  public ModuleType(@Nullable TemplateMetadata metadata, @Nullable FormFactorUtils.FormFactor formFactor,
                    @Nullable String name, boolean creates) {
    myDoesCreate = creates;
    templateMetadata = metadata;
    myName = name;
    this.formFactor = formFactor;
  }

  public ModuleType(@NotNull String name, boolean creates) {
    this(null, null, name, creates);
  }

  public boolean createsModule() {
    return myDoesCreate;
  }

  public boolean importsModule() {
    return !myDoesCreate;
  }

  @Override
  public String toString() {
    return myName;
  }
}
