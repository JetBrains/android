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
package com.google.idea.blaze.base.sync.workspace;

import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifactWithoutDigest;
import com.google.idea.blaze.base.command.buildresult.SourceArtifact;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.sync.FakeRemoteOutputArtifact;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import java.io.File;
import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;

/** Resolves all artifacts to local source files. */
public class MockArtifactLocationDecoder implements ArtifactLocationDecoder {
  @Nullable private final File workspaceRoot;
  private final boolean isRemote;

  public MockArtifactLocationDecoder(@Nullable File workspaceRoot, boolean isRemote) {
    this.workspaceRoot = workspaceRoot;
    this.isRemote = isRemote;
  }

  public MockArtifactLocationDecoder() {
    this(null, false);
  }

  @Override
  public File decode(ArtifactLocation artifactLocation) {
    return new File(workspaceRoot, artifactLocation.getRelativePath());
  }

  @Override
  public File resolveSource(ArtifactLocation artifact) {
    return decode(artifact);
  }

  @Override
  public BlazeArtifact resolveOutput(ArtifactLocation artifact) {
    if (artifact.isSource()) {
      return new SourceArtifact(decode(artifact));
    }

    File file = decode(artifact);
    if (isRemote && file.exists()) {
      return new FakeRemoteOutputArtifact(file, workspaceRoot.toPath().relativize(file.toPath()));
    }
    return new LocalFileOutputArtifactWithoutDigest(
        decode(artifact), Path.of(artifact.getExecutionRootRelativePath()), artifact.getExecutionRootRelativePath());
  }
}
