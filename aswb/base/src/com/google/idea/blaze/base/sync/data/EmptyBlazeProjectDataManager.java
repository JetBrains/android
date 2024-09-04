/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
import javax.annotation.Nullable;

/**
 * Implementation of {@link BlazeProjectDataManager} with null data. Load and save operations are
 * no-ops.
 */
public class EmptyBlazeProjectDataManager implements BlazeProjectDataManager {

  @Nullable
  @Override
  public BlazeProjectData getBlazeProjectData() {
    return null;
  }

  @Nullable
  @Override
  public BlazeProjectData loadProject(BlazeImportSettings importSettings) {
    return null;
  }

  @Override
  public void saveProject(BlazeImportSettings importSettings, BlazeProjectData projectData) {}
}
