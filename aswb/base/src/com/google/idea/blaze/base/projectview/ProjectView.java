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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.Section;
import com.google.idea.blaze.base.projectview.section.SectionBuilder;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/** Represents instructions for what should be included in a project. */
public final class ProjectView implements Serializable {
  private static final long serialVersionUID = 3L;

  private final ImmutableList<Section<?>> sections;

  public ProjectView(ImmutableList<Section<?>> sections) {
    this.sections = sections;
  }

  @SuppressWarnings("unchecked")
  public <T, SectionType extends Section<T>> ImmutableList<SectionType> getSectionsOfType(
      SectionKey<T, SectionType> key) {
    ImmutableList.Builder<SectionType> result = ImmutableList.builder();
    for (Section<?> section : sections) {
      if (section.isSectionType(key)) {
        result.add((SectionType) section);
      }
    }
    return result.build();
  }

  /** Returns all values from the given list section */
  public <T> List<T> listItems(SectionKey<T, ListSection<T>> key) {
    List<T> result = Lists.newArrayList();
    for (ListSection<T> section : getSectionsOfType(key)) {
      result.addAll(section.items());
    }
    return result;
  }

  /** Gets the last value from any scalar sections */
  @Nullable
  public <T> T getScalarValue(SectionKey<T, ScalarSection<T>> key) {
    return getScalarValue(key, null);
  }

  /** Gets the last value from any scalar sections */
  @Nullable
  public <T> T getScalarValue(SectionKey<T, ScalarSection<T>> key, @Nullable T defaultValue) {
    Collection<ScalarSection<T>> sections = getSectionsOfType(key);
    if (sections.isEmpty()) {
      return defaultValue;
    } else {
      return Iterables.getLast(sections).getValue();
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(ProjectView projectView) {
    return new Builder(projectView);
  }

  public ImmutableList<Section<?>> getSections() {
    return sections;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProjectView that = (ProjectView) o;
    return Objects.equal(sections, that.sections);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(sections);
  }

  /** Builder class. */
  public static class Builder {
    private final List<Section<?>> sections = Lists.newArrayList();

    Builder() {}

    Builder(ProjectView projectView) {
      sections.addAll(projectView.sections);
    }

    /** Gets the last section of the type in the builder. Useful to add on to sections. */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T, SectionType extends Section<T>> SectionType getLast(SectionKey<T, SectionType> key) {
      for (Section<?> section : sections) {
        if (section.isSectionType(key)) {
          return (SectionType) section;
        }
      }
      return null;
    }

    @CanIgnoreReturnValue
    public <T, SectionType extends Section<T>> Builder add(SectionBuilder<T, SectionType> builder) {
      return add(builder.build());
    }

    @CanIgnoreReturnValue
    public <T, SectionType extends Section<T>> Builder add(SectionType section) {
      sections.add(section);
      return this;
    }

    @CanIgnoreReturnValue
    public <T> Builder remove(Section<T> section) {
      sections.remove(section);
      return this;
    }

    /** Replaces a section if it already exists. If it doesn't, just add the section. */
    @CanIgnoreReturnValue
    public <T, SectionType extends Section<T>> Builder replace(
        @Nullable Section<T> section, SectionBuilder<T, SectionType> builder) {
      return replace(section, builder.build());
    }

    /** Replaces a section if it already exists. If it doesn't, just add the section. */
    @CanIgnoreReturnValue
    public <T> Builder replace(@Nullable Section<T> toReplace, Section<T> replaceWith) {
      if (toReplace == null) {
        return add(replaceWith);
      }

      int i = sections.indexOf(toReplace);
      if (i == -1) {
        throw new IllegalArgumentException("Section not in this builder.");
      }
      sections.remove(i);
      sections.add(i, replaceWith);
      return this;
    }

    public ProjectView build() {
      return new ProjectView(ImmutableList.copyOf(sections));
    }
  }
}
