/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.targetmapbuilder;

import com.google.idea.blaze.base.sync.BlazeSyncIntegrationTestCase;

/** Environment containing the common Blaze attributes shared across a target map. */
public class BlazeInfoData {
  /**
   * The default value is taken from {@link BlazeSyncIntegrationTestCase} to keep target maps
   * default consistent with existing blaze integration tests.
   */
  private static final String DEFAULT_EXEC_ROOT_REL_BIN_PATH =
      "bazel-out/gcc-4.X.Y-crosstool-v17-hybrid-grtev3-k8-fastbuild/bin";

  public static final BlazeInfoData DEFAULT = new BlazeInfoData(DEFAULT_EXEC_ROOT_REL_BIN_PATH);
  private String blazeExecutablesRootPath;

  public BlazeInfoData(String blazeExecutablesRootPath) {
    this.blazeExecutablesRootPath = blazeExecutablesRootPath;
  }

  /** The default value to set in ArtifactLocation#rootExecutionPathFragment. */
  public String getRootExecutionPathFragment() {
    return blazeExecutablesRootPath;
  }
}
