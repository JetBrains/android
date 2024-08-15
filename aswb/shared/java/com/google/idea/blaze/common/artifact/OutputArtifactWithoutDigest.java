/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.common.artifact;

import javax.annotation.Nullable;

/** A blaze output artifact, generated during some build action. */
public interface OutputArtifactWithoutDigest extends BlazeArtifact, OutputArtifactInfo {

  /** The path component related to the build configuration. */
  String getConfigurationMnemonic();

  /**
   * Returns the {@link ArtifactState} for this output, used for serialization/diffing purposes. Can
   * require file system operations.
   *
   * <p>Note, this method is kept here to support legacy sync codepaths only. Ideally it would not
   * be here but achieving that now is too much work.
   */
  @Nullable
  ArtifactState toArtifactState();
}
