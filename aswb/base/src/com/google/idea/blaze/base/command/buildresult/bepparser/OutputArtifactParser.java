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
package com.google.idea.blaze.base.command.buildresult.bepparser;

import com.google.common.base.Joiner;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;

/** Parses output artifacts from the blaze build event protocol (BEP). */
public interface OutputArtifactParser {

  ExtensionPointName<OutputArtifactParser> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.OutputArtifactParser");

  @Nullable
  static OutputArtifact parseArtifact(
      BuildEventStreamProtos.File file, long syncStartTimeMillis) {
    return Arrays.stream(EP_NAME.getExtensions())
        .map(p -> p.parse(file, syncStartTimeMillis))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  @Nullable
  OutputArtifact parse(BuildEventStreamProtos.File file, long syncStartTimeMillis);

  static Path bazelFileToArtifactPath(BuildEventStreamProtos.File file) {
    return Path.of(Joiner.on('/').join(file.getPathPrefixList()), file.getName());
  }
}
