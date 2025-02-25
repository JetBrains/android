/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.runner;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.manifest.ManifestParser.ParsedManifest;
import com.google.idea.blaze.common.artifact.OutputArtifact;

@AutoValue
abstract class DeployData {
  static DeployData create(ParsedManifest manifest, ImmutableList<OutputArtifact> apks) {
    return new com.google.idea.blaze.android.run.runner.AutoValue_DeployData(manifest, apks);
  }

  abstract ParsedManifest mergedManifest();

  abstract ImmutableList<OutputArtifact> apks();
}
