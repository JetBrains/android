/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command.buildresult;

import com.google.idea.blaze.common.artifact.OutputArtifact;
import java.io.File;

/**
 * A variant of {@link LocalFileOutputArtifactWithoutDigest} implementing {@link OutputArtifact}.
 */
public class LocalFileOutputArtifact extends LocalFileOutputArtifactWithoutDigest
    implements OutputArtifact {
  private final String digest;

  public LocalFileOutputArtifact(
      File file, String blazeOutRelativePath, String configurationMnemonic, String digest) {
    super(file, blazeOutRelativePath, configurationMnemonic);
    this.digest = digest;
  }

  @Override
  public String getDigest() {
    return digest;
  }
}
