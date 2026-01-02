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
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import javax.annotation.concurrent.Immutable;

/** Contains the user's language preferences from the project view. */
@Immutable
public final class WorkspaceLanguageSettings {
  private final WorkspaceType workspaceType;
  private final ImmutableSet<LanguageClass> activeLanguages;

  public WorkspaceLanguageSettings(
      WorkspaceType workspaceType, ImmutableSet<LanguageClass> activeLanguages) {
    this.workspaceType = workspaceType;
    this.activeLanguages = activeLanguages;
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

  public boolean isLanguageActive(LanguageClass languageClass) {
    return getActiveLanguages().contains(languageClass);
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
