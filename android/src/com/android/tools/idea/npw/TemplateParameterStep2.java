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
package com.android.tools.idea.npw;

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.npw.template.ConfigureTemplateParametersStep;
import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithDescription;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;

/**
 * Wizard step for specifying template-specific parameters.
 * This class is used for configuring Android Activities AND non-Android lib modules.
 *
 * @deprecated Replaced by {@link ConfigureTemplateParametersStep}
 */
public class TemplateParameterStep2 extends DynamicWizardStepWithDescription {

  /**
   * Creates a new template parameters wizard step.
   *
   * @param presetParameters some parameter values may be predefined outside of this step.
   *                         User will not be allowed to change their values.
   */
  public TemplateParameterStep2(@Nullable FormFactor formFactor, Map<String, Object> presetParameters,
                                @Nullable Disposable disposable, @NotNull Key<String> packageNameKey,
                                SourceProvider[] sourceProviders, String stepTitle) {
    super(disposable);
    throw new RuntimeException("Deprecated code called");
  }

  public void setPresetValue(@NotNull String key, @Nullable Object value) {
    throw new RuntimeException("Deprecated code called");
  }

  @NotNull
  public Key<?> getParameterKey(@NotNull Parameter parameter) {
    throw new RuntimeException("Deprecated code called");
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Template parameters";
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    throw new RuntimeException("Deprecated code called");
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    throw new RuntimeException("Deprecated code called");
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }
}
