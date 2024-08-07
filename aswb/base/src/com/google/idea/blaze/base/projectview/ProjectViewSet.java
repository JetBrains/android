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
package com.google.idea.blaze.base.projectview;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.Section;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/** A collection of project views and their file names. */
public final class ProjectViewSet implements Serializable {

  public static final ProjectViewSet EMPTY = builder().build();
  private static final long serialVersionUID = 2L;

  private final ImmutableList<ProjectViewFile> projectViewFiles;

  public ProjectViewSet(ImmutableList<ProjectViewFile> projectViewFiles) {
    this.projectViewFiles = projectViewFiles;
  }

  /** Returns all values from all list sections in the project views, in order */
  public <T> List<T> listItems(SectionKey<T, ListSection<T>> key) {
    List<T> result = Lists.newArrayList();
    for (ListSection<T> section : getSections(key)) {
      result.addAll(section.items());
    }
    return result;
  }

  /** Returns all values from all scalar sections in the project views, in order */
  public <T> List<T> listScalarItems(SectionKey<T, ScalarSection<T>> key) {
    List<T> result = Lists.newArrayList();
    for (ScalarSection<T> section : getSections(key)) {
      result.add(section.getValue());
    }
    return result;
  }

  /** Gets the last value from any scalar sections */
  public <T> Optional<T> getScalarValue(SectionKey<T, ScalarSection<T>> key) {
    Collection<ScalarSection<T>> sections = getSections(key);
    if (sections.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(Iterables.getLast(sections).getValue());
  }

  public <T, SectionType extends Section<T>> Collection<SectionType> getSections(
      SectionKey<T, SectionType> key) {
    List<SectionType> result = Lists.newArrayList();
    for (ProjectViewFile projectViewFile : projectViewFiles) {
      ProjectView projectView = projectViewFile.projectView;
      result.addAll(projectView.getSectionsOfType(key));
    }
    return result;
  }

  public ImmutableList<ProjectViewFile> getProjectViewFiles() {
    return projectViewFiles;
  }

  @Nullable
  public ProjectViewFile getTopLevelProjectViewFile() {
    return !projectViewFiles.isEmpty() ? projectViewFiles.get(projectViewFiles.size() - 1) : null;
  }

  /** A project view/file pair */
  public static class ProjectViewFile implements Serializable {
    private static final long serialVersionUID = 1L;
    public final ProjectView projectView;
    @Nullable public final File projectViewFile;

    public ProjectViewFile(ProjectView projectView, @Nullable File projectViewFile) {
      this.projectView = projectView;
      this.projectViewFile = projectViewFile;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for a project view */
  public static class Builder {
    ImmutableList.Builder<ProjectViewFile> projectViewFiles = ImmutableList.builder();

    @CanIgnoreReturnValue
    public Builder add(ProjectView projectView) {
      return add(null, projectView);
    }

    @CanIgnoreReturnValue
    public Builder add(@Nullable File projectViewFile, ProjectView projectView) {
      projectViewFiles.add(new ProjectViewFile(projectView, projectViewFile));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addAll(Collection<ProjectViewFile> projectViewFiles) {
      this.projectViewFiles.addAll(projectViewFiles);
      return this;
    }

    public ProjectViewSet build() {
      return new ProjectViewSet(projectViewFiles.build());
    }
  }
}
