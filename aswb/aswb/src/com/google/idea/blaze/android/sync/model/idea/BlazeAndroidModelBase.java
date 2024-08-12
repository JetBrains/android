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
package com.google.idea.blaze.android.sync.model.idea;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.ClassJarProvider;
import com.android.tools.lint.detector.api.Desugaring;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.LintCollector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

/**
 * Contains Android-Blaze related state necessary for configuring an IDEA project based on a
 * user-selected build variant.
 */
abstract class BlazeAndroidModelBase implements AndroidModel {
  protected final Project project;
  private final ListenableFuture<String> applicationId;
  private final int minSdkVersion;

  protected BlazeAndroidModelBase(
      Project project,
      File rootDirPath,
      ListenableFuture<String> applicationId,
      int minSdkVersion) {
    this.project = project;
    this.applicationId = applicationId;
    this.minSdkVersion = minSdkVersion;
  }

  @Override
  public String getApplicationId() {
    try {
      return applicationId.get(1, SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (TimeoutException | ExecutionException e) {
      Logger.getInstance(BlazeAndroidModelBase.class).warn("Application Id not initialized yet", e);
    }
    return uninitializedApplicationId();
  }

  protected abstract String uninitializedApplicationId();

  @Override
  public Set<String> getAllApplicationIds() {
    return ImmutableSet.of(getApplicationId());
  }

  @Override
  public boolean overridesManifestPackage() {
    return false;
  }

  @Override
  public Boolean isDebuggable() {
    return true;
  }

  @Override
  @Nullable
  public AndroidVersion getMinSdkVersion() {
    return new AndroidVersion(minSdkVersion, null);
  }

  @Nullable
  @Override
  public AndroidVersion getRuntimeMinSdkVersion() {
    return getMinSdkVersion();
  }

  @Nullable
  @Override
  public AndroidVersion getTargetSdkVersion() {
    return null;
  }

  // @Override #api212, moved in #api213
  public ClassJarProvider getClassJarProvider() {
    return new BlazeClassJarProvider(project);
  }

  @Override
  public Set<Desugaring> getDesugaring() {
    return Desugaring.FULL;
  }

  @Override
  @Nullable
  public Iterable<File> getLintRuleJarsOverride() {
    if (Blaze.getProjectType(project) != ProjectType.ASPECT_SYNC) {
      return ImmutableList.of();
    }
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    return LintCollector.getLintJars(project, blazeProjectData);
  }
}
