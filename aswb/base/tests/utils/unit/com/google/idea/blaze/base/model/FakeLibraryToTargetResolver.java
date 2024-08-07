/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.model;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.function.Function.identity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.project.Project;
import java.util.Optional;
import java.util.stream.Stream;

/** Fake implementation of {@link LibraryToTargetResolver} for use in tests. */
public final class FakeLibraryToTargetResolver implements LibraryToTargetResolver {

  private ImmutableMap<LibraryKey, Label> libraryKeyToLabelMap = ImmutableMap.of();

  @Override
  public Stream<Label> collectTargets(Project project) {
    return libraryKeyToLabelMap.values().stream();
  }

  @Override
  public Optional<Label> resolveLibraryToTarget(Project project, LibraryKey library) {
    return Optional.ofNullable(libraryKeyToLabelMap.get(library));
  }

  public void setLibraryKeyToLabelMap(ImmutableMap<LibraryKey, Label> libraryKeyToLabelMap) {
    this.libraryKeyToLabelMap = libraryKeyToLabelMap;
  }

  /** Fill {@link #libraryKeyToLabelMap}'s values with the given targets and arbitrary keys. */
  public void setAvailableLabels(ImmutableList<Label> labels) {
    libraryKeyToLabelMap =
        labels.stream()
            .collect(
                toImmutableMap(
                    label -> LibraryKey.fromIntelliJLibraryName(label.targetName() + ".jar"),
                    identity()));
  }
}
