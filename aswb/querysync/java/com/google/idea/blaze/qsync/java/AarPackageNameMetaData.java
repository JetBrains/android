/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.java;

import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactMetadata;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import java.io.IOException;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Package name taken from the {@code AndroidManifest.xml} file within an AAR file. */
public class AarPackageNameMetaData implements ArtifactMetadata {

  private final AndroidManifestParser manifestParser;

  public AarPackageNameMetaData(AndroidManifestParser manifestParser) {
    this.manifestParser = manifestParser;
  }

  @Override
  public String key() {
    return "AarPackageName";
  }

  @Override
  public String extract(CachedArtifact buildArtifact, Object nameForLogs) throws BuildException {
    try (ZipFile zip = buildArtifact.openAsZipFile()) {
      ZipEntry entry = zip.getEntry("AndroidManifest.xml");
      if (entry != null) {
        return manifestParser.readPackageNameFrom(zip.getInputStream(entry));
      }
    } catch (IOException e) {
      throw new BuildException(String.format("Failed to read aar file %s", buildArtifact), e);
    }
    throw new BuildException(
        String.format("Failed to find AndroidManifest.xml in  %s", nameForLogs));
  }

  public Optional<String> from(TargetBuildInfo target, BuildArtifact artifact) {
    return Optional.ofNullable(target.getMetadata(artifact, key()));
  }
}
