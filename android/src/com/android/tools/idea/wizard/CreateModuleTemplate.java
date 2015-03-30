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

import javax.swing.*;
import java.util.Map;

/**
 * A entry used by {@link com.android.tools.idea.wizard.ChooseModuleTypeStep} to represent an option for a module
 * type to create
 */
public class CreateModuleTemplate extends NewModuleTemplate {
  public final TemplateMetadata templateMetadata;
  private String myName;
  private final Map<ScopedStateStore.Key<?>, Object> myCustomValues = Maps.newHashMap();

  public CreateModuleTemplate(@Nullable TemplateMetadata metadata,
                              @Nullable FormFactorUtils.FormFactor formFactor,
                              @NotNull String name,
                              boolean gallery,
                              @NotNull Icon icon) {
    super(name, metadata != null ? metadata.getDescription() : null, formFactor, gallery, icon);
    templateMetadata = metadata;
    myName = name;
  }

  @Override
  public String toString() {
    return myName;
  }

  @Override
  public void updateWizardStateOnSelection(ScopedStateStore state) {
    for (ScopedStateStore.Key<?> k : myCustomValues.keySet()) {
      state.unsafePut(k, myCustomValues.get(k));
    }
  }

  public <T> void setCustomValue(ScopedStateStore.Key<? super T> key, T value) {
    myCustomValues.put(key, value);
  }
}
