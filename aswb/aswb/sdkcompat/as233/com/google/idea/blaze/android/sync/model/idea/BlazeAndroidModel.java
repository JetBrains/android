/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.model.idea;

import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.Namespacing;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.project.Project;
import java.io.File;

/** Blaze model for an android project. #api42. */
public class BlazeAndroidModel extends BlazeAndroidModelBase {
  private final NamedIdeaSourceProvider sourceProvider;

  public BlazeAndroidModel(
      Project project,
      File rootDirPath,
      NamedIdeaSourceProvider sourceProvider,
      ListenableFuture<String> applicationId,
      int minSdkVersion) {
    super(project, rootDirPath, applicationId, minSdkVersion);
    this.sourceProvider = sourceProvider;
  }

  public NamedIdeaSourceProvider getDefaultSourceProvider() {
    return sourceProvider;
  }

  @Override
  public Namespacing getNamespacing() {
    return Namespacing.DISABLED;
  }

  @Override
  protected String uninitializedApplicationId() {
    return AndroidModel.UNINITIALIZED_APPLICATION_ID;
  }
}
