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
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata;
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.AarResPackage;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Extracts the package name from the {@code AndroidManifest.xml} file within an AAR file. */
public class AarPackageNameExtractor implements ArtifactMetadata.Extractor<AarResPackage> {

  private final AndroidManifestParser manifestParser;

  public AarPackageNameExtractor(AndroidManifestParser manifestParser) {
    this.manifestParser = manifestParser;
  }

  @Override
  public AarResPackage extractFrom(CachedArtifact buildArtifact, Object nameForLogs)
      throws BuildException {
    try (ZipFile zip = buildArtifact.openAsZipFile()) {
      ZipEntry entry = zip.getEntry("AndroidManifest.xml");
      if (entry != null) {
        return new AarResPackage(manifestParser.readPackageNameFrom(zip.getInputStream(entry)));
      }
    } catch (IOException e) {
      throw new BuildException(String.format("Failed to read aar file %s", buildArtifact), e);
    }
    throw new BuildException(
        String.format("Failed to find AndroidManifest.xml in  %s", nameForLogs));
  }

  @Override
  public Class<AarResPackage> metadataClass() {
    return AarResPackage.class;
  }

}
