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
package com.google.idea.blaze.base.qsync;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.qsync.project.ProjectProtoTransform;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.List;

/**
 * Provides a {@link ProjectProtoTransform} that requires AS specific dependencies so cannot be
 * created directly by the base code.
 */
public interface ProjectProtoTransformProvider {

  ExtensionPointName<ProjectProtoTransformProvider> EP_NAME =
      new ExtensionPointName<>("com.google.idea.blaze.base.qsync.ProjectProtoTransformProvider");

  static ImmutableList<ProjectProtoTransform> getAll(QuerySyncProject project) {
    return EP_NAME.getExtensionList().stream()
        .map(ep -> ep.createTransforms(project))
        .flatMap(List::stream)
        .collect(ImmutableList.toImmutableList());
  }

  List<ProjectProtoTransform> createTransforms(QuerySyncProject project);
}
