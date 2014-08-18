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
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * A entry used by {@link com.android.tools.idea.wizard.ChooseModuleTypeStep} to represent an option for a module
 * type to create
 */
public class CreateModuleTemplate implements ModuleTemplate {
  public final TemplateMetadata templateMetadata;
  public final FormFactorUtils.FormFactor formFactor;
  private final boolean myGallery;
  private String myName;
  private boolean myDoesCreate;
  private final Map<ScopedStateStore.Key<?>, Object> myCustomValues = Maps.newHashMap();

  public CreateModuleTemplate(@Nullable TemplateMetadata metadata,
                              @Nullable FormFactorUtils.FormFactor formFactor,
                              @Nullable String name,
                              boolean creates,
                              boolean gallery) {
    myDoesCreate = creates;
    templateMetadata = metadata;
    myName = name;
    this.formFactor = formFactor;
    myGallery = gallery;
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

  @Override
  public boolean isGalleryModuleType() {
    return myGallery;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return formFactor != null ? formFactor.getIcon() : null;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public String getDescription() {
    return templateMetadata != null ? templateMetadata.getDescription() : null;
  }

  @Override
  public void updateWizardStateOnSelection(ScopedStateStore state) {
    for (ScopedStateStore.Key<?> k : myCustomValues.keySet()) {
      state.unsafePut(k, myCustomValues.get(k));
    }
  }

  @Nullable
  @Override
  public FormFactorUtils.FormFactor getFormFactor() {
    return formFactor;
  }

  public <T> void setCustomValue(ScopedStateStore.Key<? super T> key, T value) {
    myCustomValues.put(key, value);
  }
}
