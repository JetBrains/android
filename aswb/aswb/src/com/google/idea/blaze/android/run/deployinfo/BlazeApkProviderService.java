/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.idea.blaze.android.run.deployinfo;

import com.android.tools.idea.run.ApkProvider;
import com.google.idea.blaze.android.run.runner.ApkBuildStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

/** Service to provide ApkProviders that source APKs from {@link ApkBuildStep}. */
public interface BlazeApkProviderService {
  static BlazeApkProviderService getInstance() {
    return ApplicationManager.getApplication().getService(BlazeApkProviderService.class);
  }

  /** Returns an APK provider that sources APKs from the given {@link ApkBuildStep}. */
  ApkProvider getApkProvider(Project project, ApkBuildStep deployInfo);

  /** Default implementation using {@link BlazeApkProvider}. */
  class DefaultImpl implements BlazeApkProviderService {
    @Override
    public ApkProvider getApkProvider(Project project, ApkBuildStep deployInfo) {
      return new BlazeApkProvider(project, deployInfo);
    }
  }
}
