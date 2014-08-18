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

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Interface for module templates used in the new module wizard
 */
public interface ModuleTemplate {
  /**
   * @return <code>true</code> if this template will be shown in the gallery at the top of the wizard page.
   */
  boolean isGalleryModuleType();

  /**
   * @return icon to be used in the gallery.
   */
  @Nullable
  Icon getIcon();

  /**
   * @return module template name.
   */
  String getName();

  /**
   * @return description of the teplate or <code>null</code> if none.
   */
  @Nullable
  String getDescription();

  /**
   * @param state called when module template selection page is done. Gives a chance to update the wizard state.
   */
  void updateWizardStateOnSelection(ScopedStateStore state);

  /**
   * @return form factor associated with this template.
   */
  @Nullable
  FormFactorUtils.FormFactor getFormFactor();
}
