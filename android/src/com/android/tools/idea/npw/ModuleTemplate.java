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

import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Properties useful for displaying a module in the various Android Studio module wizards.
 */
public interface ModuleTemplate {
  /**
   * @return icon to be used in the gallery.
   */
  @Nullable
  Icon getIcon();

  /**
   * @return module template name.
   */
  @NotNull
  String getName();

  /**
   * @return description of the template or {@code null} if none.
   */
  @Nullable
  String getDescription();

  /**
   * @return form factor associated with this template. If {@code null}, this template does not
   * represent an Android device module.
   */
  @Nullable
  FormFactorUtils.FormFactor getFormFactor();

  /**
   * It can be useful to update the Wizard's state upon confirmation of selecting a module.
   *
   * @param state of the wizard to update.
   */
  void updateWizardState(@NotNull ScopedStateStore state);
}
