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
package com.google.idea.blaze.base.model;

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.Collection;

/**
 * An extension point for individual languages to provide the list of output artifacts which should
 * be tracked and locally cached in {@link RemoteOutputsCache}.
 */
public interface OutputsProvider {

  ExtensionPointName<OutputsProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.OutputsProvider");

  /**
   * Returns the artifacts in this {@link TargetIdeInfo} which are relevant to this provider, and
   * should be tracked between syncs.
   *
   * <p>Any remote artifacts not returned here by some {@link OutputsProvider} will be dropped from
   * {@link RemoteOutputArtifacts} and entirely inaccessible.
   */
  Collection<ArtifactLocation> selectAllRelevantOutputs(TargetIdeInfo target);

  /**
   * Returns the artifacts in this {@link TargetIdeInfo} which should be locally cached via {@link
   * RemoteOutputsCache}.
   *
   * <p>By default this is all artifacts from {@link #selectAllRelevantOutputs(TargetIdeInfo)}. One
   * exception is when the artifacts are cached separately, for example in {@link JarCache}.
   */
  default Collection<ArtifactLocation> selectOutputsToCache(TargetIdeInfo target) {
    return selectAllRelevantOutputs(target);
  }

  boolean isActive(WorkspaceLanguageSettings languageSettings);
}
