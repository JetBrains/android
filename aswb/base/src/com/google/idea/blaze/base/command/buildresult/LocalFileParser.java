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
package com.google.idea.blaze.base.command.buildresult;

import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.idea.blaze.base.command.buildresult.bepparser.OutputArtifactParser;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.intellij.util.io.URLUtil;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * The default implementation of {@link OutputArtifactParser}, for local, absolute file paths.
 */
public class LocalFileParser implements OutputArtifactParser {
  @Override
  @Nullable
  public OutputArtifact parse(
    BuildEventStreamProtos.File file, long syncStartTimeMillis) {
    String uri = file.getUri();
    if (!uri.startsWith(URLUtil.FILE_PROTOCOL)) {
      return null;
    }
    try {
      File f = new File(new URI(uri));
      Path artifactNamePath = OutputArtifactParser.bazelFileToArtifactPath(file);
      int prefixCount = file.getPathPrefixCount();
      Path localArtifactPath = f.toPath();
      artifactNamePath = maybeWorkaroundMissingExternalRepoPrefixInBazel(artifactNamePath, prefixCount, localArtifactPath);
      return new LocalFileOutputArtifact(
        f,
        artifactNamePath,
        prefixCount,
        file.getDigest());
    }
    catch (URISyntaxException | IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * Bazel reports source artifacts like external/repo~/artifact/path from external repositories as artifact/path and it is not enough
   * to match them with artifact paths visible to aspects. This method adjusts the artifact name based on the local artifact path.
   */
  // TODO: b/403296316 - Replace with any better solution.
  private static Path maybeWorkaroundMissingExternalRepoPrefixInBazel(Path artifactNamePath, int prefixCount, Path localArtifactPath) {
    if (prefixCount == 0 && localArtifactPath.getNameCount() >= artifactNamePath.getNameCount() + 2){
      int probableExternalNameIndex = localArtifactPath.getNameCount() - artifactNamePath.getNameCount() - 2;
      if (localArtifactPath.getName(probableExternalNameIndex).toString().equals("external") && localArtifactPath.endsWith(artifactNamePath)) {
        artifactNamePath = localArtifactPath.subpath(probableExternalNameIndex, localArtifactPath.getNameCount());
      }
    }
    return artifactNamePath;
  }
}
