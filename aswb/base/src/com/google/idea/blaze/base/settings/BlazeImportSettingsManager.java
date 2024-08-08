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
package com.google.idea.blaze.base.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** Manages storage for the project's {@link BlazeImportSettings}. */
@State(name = "BlazeImportSettings", storages = @Storage(file = StoragePathMacros.WORKSPACE_FILE))
public class BlazeImportSettingsManager implements PersistentStateComponent<BlazeImportSettings> {

  @Nullable private static volatile BlazeImportSettings pendingProjectSettings;

  @Nullable private BlazeImportSettings importSettings;

  private final Project project;

  public BlazeImportSettingsManager(Project project) {
    this.project = project;
  }

  public static BlazeImportSettingsManager getInstance(Project project) {
    return project.getService(BlazeImportSettingsManager.class);
  }

  @Nullable
  @Override
  public BlazeImportSettings getState() {
    return importSettings;
  }

  @Override
  public void loadState(BlazeImportSettings importSettings) {
    this.importSettings = importSettings;
  }

  @Nullable
  public BlazeImportSettings getImportSettings() {
    if (importSettings != null) {
      return importSettings;
    }
    BlazeImportSettings pending = pendingProjectSettings;
    if (pending != null && pending.getProjectName().equals(project.getName())) {
      return pending;
    }
    return null;
  }

  public void setImportSettings(BlazeImportSettings importSettings) {
    this.importSettings = importSettings;
    // also clear any possibly pending settings
    pendingProjectSettings = null;
  }

  /**
   * A hacky way to set BlazeImportSettings for a project which is currently being created. Some
   * project components rely on this being available, so it needs to be set prior to project
   * creation.
   */
  public static void setPendingProjectSettings(BlazeImportSettings importSettings) {
    pendingProjectSettings = importSettings;
  }
}
