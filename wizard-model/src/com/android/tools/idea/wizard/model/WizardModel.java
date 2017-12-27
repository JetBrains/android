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

import com.intellij.openapi.Disposable;

/**
 * A model is a set of data which will be filled out by the steps of a wizard. Multiple steps may
 * share the same model and, therefore, each claim responsibility for some subset of it.
 * <p/>
 * Note: Wizard models should never be aware of the UI that's filling them out, or expose data
 * properties which exist just for UI convenience. A good rule of thumb is: if your model's
 * {@link #handleFinished()} doesn't need it, it probably shouldn't be in here.
 */
public abstract class WizardModel implements Disposable {
  /**
   * Method which is executed when a wizard is completed because the user hit "Finish".
   *
   * Note that if this model is only associated with a step / steps that were skipped, this
   * method will not be called. Instead, {@link #handleSkipped()} will be called, instead.
   */
  protected abstract void handleFinished();

  /**
   * Method which is executed on models that were skipped over by the wizard. This can happen if
   * all steps this model was associated with returned {@link ModelWizardStep#shouldShow()} with
   * {@code false}.
   *
   * In other words, either {@link #handleFinished()} will get called or this method will.
   *
   * Most models won't care about handling this event, but occasionally a model may want to resolve
   * some logic set up in its constructor even if {@link #handleFinished()} doesn't get called.
   */
  protected void handleSkipped() {
  }

  @Override
  public void dispose() {
  }
}
