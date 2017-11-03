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
package com.android.tools.idea.wizard.model;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Steps that extend this class can have their visibility set directly using the {@link #setShouldShow(boolean)} method.
 * This is useful for allowing a parent step to toggle the visibility of steps that follow it. For example, one step might present
 * options to select to the user, and, depending on what options were chosen, influences what following steps will appear.
 */
public abstract class SkippableWizardStep<M extends WizardModel> extends ModelWizardStep<M> {
  private boolean myShow = true;

  protected SkippableWizardStep(@NotNull M model, @NotNull String title) {
    super(model, title);
  }

  protected SkippableWizardStep(@NotNull M model, @NotNull String title, @NotNull Icon icon) {
    super(model, title, icon);
  }

  /**
   * Sets whether this step should show or not when it is entered.
   * Note that setting this to <code>false</code> while on the step won't hide it, nor will setting it to <code>false</code> from a
   * followup step mean this will be skipped when the user presses the back button. This value is ONLY checked when the step is entered.
   */
  public final void setShouldShow(boolean show) {
    myShow = show;
  }

  @Override
  protected final boolean shouldShow() {
    return myShow;
  }
}
