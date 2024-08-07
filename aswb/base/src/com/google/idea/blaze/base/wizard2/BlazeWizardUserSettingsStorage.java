/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.wizard2;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import javax.annotation.Nullable;

/** Stores wizard user settings between runs. */
@State(name = "BlazeWizardUserSettings", storages = @Storage("blaze.wizard.settings.xml"))
public class BlazeWizardUserSettingsStorage
    implements PersistentStateComponent<BlazeWizardUserSettings> {
  private BlazeWizardUserSettings state = new BlazeWizardUserSettings();

  static BlazeWizardUserSettingsStorage getInstance() {
    return ApplicationManager.getApplication().getService(BlazeWizardUserSettingsStorage.class);
  }

  @Nullable
  @Override
  public BlazeWizardUserSettings getState() {
    return state;
  }

  @Override
  public void loadState(BlazeWizardUserSettings state) {
    this.state = state;
  }

  BlazeWizardUserSettings copyUserSettings() {
    return new BlazeWizardUserSettings(state);
  }

  void commit(BlazeWizardUserSettings userSettings) {
    this.state = userSettings;
  }
}
