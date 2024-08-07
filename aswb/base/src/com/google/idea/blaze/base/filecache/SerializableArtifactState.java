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
package com.google.idea.blaze.base.filecache;

import com.google.devtools.intellij.model.ProjectData.LocalFileOrOutputArtifact;
import com.google.idea.blaze.common.artifact.ArtifactState;

/**
 * Legacy sync specific interface to complement {@link
 * com.google.idea.blaze.common.artifact.ArtifactState}. The purpose of this interface is to contain
 * legacy sync specific code that cannot be included directly inside the shared library due to its
 * dependencies.
 *
 * <p>See also {@link ArtifactStateProtoConverter#toProto(ArtifactState)}.
 */
public interface SerializableArtifactState {
  LocalFileOrOutputArtifact serializeToProto();
}
