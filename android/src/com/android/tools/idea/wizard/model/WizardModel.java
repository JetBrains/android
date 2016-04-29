/*
 * Copyright (C) 2015 The Android Open Source Project
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
   */
  protected abstract void handleFinished();

  @Override
  public void dispose() {
  }
}
