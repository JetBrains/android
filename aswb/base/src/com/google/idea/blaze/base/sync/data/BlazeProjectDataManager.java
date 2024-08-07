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
package com.google.idea.blaze.base.sync.data;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** Stores a cache of blaze project data. */
public interface BlazeProjectDataManager {
  static BlazeProjectDataManager getInstance(Project project) {
    return project.getService(BlazeProjectDataManager.class);
  }

  /** Returns project data that was previously loaded. */
  @Nullable
  BlazeProjectData getBlazeProjectData();

  /** Loads the project data from disk and returns it. */
  @Nullable
  BlazeProjectData loadProject(BlazeImportSettings importSettings);

  /** Updated the project data, and persists it to disk. */
  void saveProject(final BlazeImportSettings importSettings, final BlazeProjectData projectData);
}
