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
import javax.annotation.Nullable;

/**
 * The default implementation of {@link OutputArtifactParser}, for local, absolute file paths.
 */
public class LocalFileParser implements OutputArtifactParser {
  @Override
  @Nullable
  public OutputArtifact parse(
    BuildEventStreamProtos.File file, String configurationMnemonic, long syncStartTimeMillis) {
    String uri = file.getUri();
    if (!uri.startsWith(URLUtil.FILE_PROTOCOL)) {
      return null;
    }
    try {
      File f = new File(new URI(uri));
      return new LocalFileOutputArtifact(
        f,
        OutputArtifactParser.bazelFileToArtifactPath(file),
        file.getPathPrefixCount(),
        configurationMnemonic,
        file.getDigest());
    }
    catch (URISyntaxException | IllegalArgumentException e) {
      return null;
    }
  }
}
