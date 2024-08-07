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
import com.intellij.openapi.application.ApplicationManager;

/** Provides options during the import process. */
public interface BlazeWizardOptionProvider {

  /** Returns an ordered list of client/workspace type options for the user to choose from. */
  ImmutableList<TopLevelSelectWorkspaceOption> getSelectWorkspaceOptions(
      BlazeNewProjectBuilder builder, Disposable parentDisposable);

  ImmutableList<BlazeSelectProjectViewOption> getSelectProjectViewOptions(
      BlazeNewProjectBuilder builder);

  static BlazeWizardOptionProvider getInstance() {
    return ApplicationManager.getApplication().getService(BlazeWizardOptionProvider.class);
  }
}
