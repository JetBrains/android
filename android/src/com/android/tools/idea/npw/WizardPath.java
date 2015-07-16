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

import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;

import java.util.Collection;

import static com.android.tools.idea.npw.ChooseTemplateStep.MetadataListItem;

/**
 * New module wizard supports several "paths". This is an interface for delegates that manage each path.
 * Deprecated in favor of {@link DynamicWizardPath}
 */
@Deprecated
public interface WizardPath {
  /**
   * @return wizard steps (aka pages) that will be shown on this path.
   */
  Collection<ModuleWizardStep> getSteps();

  /**
   * Update state from given wizard state object
   */
  void update();

  /**
   * Create modules on wizard completion
   */
  void createModule();

  /**
   * Tells if the wizard page should be shown.
   */
  boolean isStepVisible(ModuleWizardStep step);

  /**
   * "Internal" templates this path uses that user shouldn't see.
   */
  Collection<String> getExcludedTemplates();

  /**
   * @return built-in templates, the ones that are not loaded by TemplateManager
   */
  Collection<MetadataListItem> getBuiltInTemplates();

  /**
   * @return <code>true</code> if this path also works in a new project wizard.
   */
  boolean supportsGlobalWizard();
}
