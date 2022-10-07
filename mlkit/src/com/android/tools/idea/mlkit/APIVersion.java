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
package com.android.tools.idea.mlkit;

import com.android.ide.common.repository.GradleVersion.AgpVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.text.SemVer;
import org.jetbrains.annotations.NotNull;

/**
 * Version for the ML Model Binding generated API, which depends on AGP version.
 */
public class APIVersion implements Comparable<APIVersion> {

  /**
   * Initial release of Ml Model Binding in AGP 4.1 with following features:
   *
   * <ul>
   *   <li>Support RGB image as tensor input.</li>
   *   <li>Support axis label, RGB image as tensor output.</li>
   * </ul>
   */
  @NotNull
  public static final APIVersion API_VERSION_1 = new APIVersion(SemVer.parseFromText("1.0.0"), AgpVersion.parse("4.1.0"));

  /**
   * Second release of Ml Model Binding in AGP 4.2 with following features:
   *
   * <ul>
   *   <li>Support grouping output tensors as concrete class.</li>
   *   <li>Support value label, bounding box as tensor output</li>
   * </ul>
   */
  @NotNull
  public static final APIVersion API_VERSION_2 = new APIVersion(SemVer.parseFromText("1.2.0"), AgpVersion.parse("4.2.0-alpha08"));

  /**
   * Model schema version supported in current API version. If developer's model schema version is larger than this,
   * only fallback API will be generated.
   */
  @NotNull
  private final SemVer myModelParserVersion;

  /**
   * Minimum version of the Android Gradle Plugin needed to support current API version, since
   * real codegen implementation lives in gradle task.
   */
  @NotNull
  private final AgpVersion myAgpVersion;

  /**
   * Returns the ML Model Binding API version from project.
   */
  @NotNull
  public static APIVersion fromProject(@NotNull Project project) {
    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.findFromModel(project);
    if (androidPluginInfo == null) {
      Logger.getInstance(APIVersion.class).warn("AndroidPluginInfo is null in project: " + project);
      return API_VERSION_1;
    }
    AgpVersion agpVersion = androidPluginInfo.getPluginVersion();
    if (agpVersion == null) {
      Logger.getInstance(APIVersion.class).warn("GradleVersion is null in project: " + project);
      return API_VERSION_1;
    }
    if (agpVersion.compareTo(API_VERSION_2.myAgpVersion) >= 0) {
      return API_VERSION_2;
    }
    else {
      return API_VERSION_1;
    }
  }

  private APIVersion(@NotNull SemVer modelParserVersion, @NotNull AgpVersion agpVersion) {
    this.myModelParserVersion = modelParserVersion;
    this.myAgpVersion = agpVersion;
  }

  public boolean isAtLeastVersion(@NotNull APIVersion apiVersion) {
    return compareTo(apiVersion) >= 0;
  }

  /**
   * Returns {@code true} if we only generate fallback API. This happens if model required version is higher than
   * the version existed in AGP.
   */
  public boolean generateFallbackApiOnly(@NotNull String minParserVersion) {
    SemVer semVer = SemVer.parseFromText(minParserVersion);
    if (semVer == null) {
      Logger.getInstance(APIVersion.class).error("Model min parser version is null.");
      return false;
    }
    return !myModelParserVersion.isGreaterOrEqualThan(semVer);
  }

  @Override
  public int compareTo(@NotNull APIVersion o) {
    return myAgpVersion.compareTo(o.myAgpVersion);
  }
}
