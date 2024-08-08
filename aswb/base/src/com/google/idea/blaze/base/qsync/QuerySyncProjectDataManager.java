/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** Implementation of {@link BlazeProjectDataManager} specific to querysync. */
public class QuerySyncProjectDataManager implements BlazeProjectDataManager {

  private final Project project;

  public QuerySyncProjectDataManager(Project project) {
    this.project = project;
  }

  @Nullable
  @Override
  public QuerySyncProjectData getBlazeProjectData() {
    return QuerySyncManager.getInstance(project)
        .getLoadedProject()
        .map(QuerySyncProject::getProjectData)
        .orElse(null);
  }

  @Nullable
  @Override
  public BlazeProjectData loadProject(BlazeImportSettings importSettings) {
    // this is only call from legacy sync codepaths (so should never be called, since this class
    // is not used in that case).
    // TODO(mathewi) Tidy up the interface to remove this unnecessary stuff.
    throw new UnsupportedOperationException();
  }

  @Override
  public void saveProject(BlazeImportSettings importSettings, BlazeProjectData projectData) {
    // this is only call from legacy sync codepaths (so should never be called, since this class
    // is not used in that case).
    // TODO(mathewi) Tidy up the interface to remove this unnecessary stuff.
    throw new UnsupportedOperationException();
  }

}
