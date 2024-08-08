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

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;

/** Provides bazel options for the wizard. */
public class BazelWizardOptionProvider implements BlazeWizardOptionProvider {

  @Override
  public ImmutableList<TopLevelSelectWorkspaceOption> getSelectWorkspaceOptions(
      BlazeNewProjectBuilder builder, Disposable parentDisposable) {
    return ImmutableList.of(new UseExistingBazelWorkspaceOption(builder));
  }

  @Override
  public ImmutableList<BlazeSelectProjectViewOption> getSelectProjectViewOptions(
      BlazeNewProjectBuilder builder) {
    return ImmutableList.of(
        new CreateFromScratchProjectViewOption(),
        new ImportFromWorkspaceProjectViewOption(builder),
        new GenerateFromBuildFileSelectProjectViewOption(builder),
        new CopyExternalProjectViewOption(builder));
  }
}
