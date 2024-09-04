/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.projectview;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.concurrent.Immutable;

/** Contains the user's language preferences from the project view. */
@Immutable
public final class WorkspaceLanguageSettings
    implements ProtoWrapper<ProjectData.WorkspaceLanguageSettings> {
  private final WorkspaceType workspaceType;
  private final ImmutableSet<LanguageClass> activeLanguages;

  public WorkspaceLanguageSettings(
      WorkspaceType workspaceType, ImmutableSet<LanguageClass> activeLanguages) {
    this.workspaceType = workspaceType;
    this.activeLanguages = activeLanguages;
  }

  public static WorkspaceLanguageSettings fromProto(ProjectData.WorkspaceLanguageSettings proto) {
    return new WorkspaceLanguageSettings(
        WorkspaceType.fromString(proto.getWorkspaceType()),
        ProtoWrapper.map(
            proto.getActiveLanguagesList(),
            LanguageClass::fromString,
            ImmutableSet.toImmutableSet()));
  }

  @Override
  public ProjectData.WorkspaceLanguageSettings toProto() {
    return ProjectData.WorkspaceLanguageSettings.newBuilder()
        .setWorkspaceType(workspaceType.toProto())
        .addAllActiveLanguages(ProtoWrapper.mapToProtos(activeLanguages))
        .build();
  }

  public WorkspaceType getWorkspaceType() {
    return workspaceType;
  }

  public ImmutableSet<LanguageClass> getActiveLanguages() {
    return activeLanguages;
  }

  public boolean isWorkspaceType(WorkspaceType workspaceType) {
    return this.workspaceType == workspaceType;
  }

  public boolean isWorkspaceType(WorkspaceType... workspaceTypes) {
    for (WorkspaceType workspaceType : workspaceTypes) {
      if (this.workspaceType == workspaceType) {
        return true;
      }
    }
    return false;
  }

  public boolean isLanguageActive(LanguageClass languageClass) {
    return getActiveLanguages().contains(languageClass);
  }

  /**
   * Returns the set of known rule names corresponding to the currently active languages in this
   * project.
   *
   * <p>Don't rely on this list being complete -- some rule names are recognized at runtime using
   * heuristics.
   */
  public Predicate<String> getAvailableTargetKinds() {
    ImmutableMultimap<LanguageClass, Kind> kinds = Kind.getPerLanguageKinds();
    Set<String> ruleNames =
        activeLanguages.stream()
            .map(kinds::get)
            .flatMap(Collection::stream)
            .map(Kind::getKindString)
            .collect(Collectors.toSet());
    return ruleNames::contains;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    WorkspaceLanguageSettings that = (WorkspaceLanguageSettings) o;
    return workspaceType == that.workspaceType
        && Objects.equal(getActiveLanguages(), that.getActiveLanguages());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(workspaceType, getActiveLanguages());
  }

  @Override
  public String toString() {
    return "WorkspaceLanguageSettings {"
        + "\n"
        + "  workspaceType: "
        + workspaceType
        + "\n"
        + "  activeLanguages: "
        + getActiveLanguages()
        + "\n"
        + '}';
  }
}
