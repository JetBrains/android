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
package com.google.idea.blaze.common.artifact;

/** A variant of {@link OutputArtifactWithoutDigest} that includes the digest of its content. */
public interface OutputArtifact extends OutputArtifactWithoutDigest {
  /**
   * The digest of the artifact file.
   *
   * <p>The digest of the artifact file; using the build tool's configured digest algorithm. It
   * represents the content of the file and can be used to detect whether the content has changed.
   */
  String getDigest();
}
