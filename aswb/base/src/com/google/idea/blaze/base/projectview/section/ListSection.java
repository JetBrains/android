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
package com.google.idea.blaze.base.projectview.section;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.projectview.section.sections.ItemOrTextBlock;
import com.google.idea.blaze.base.projectview.section.sections.TextBlock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * List value. Eg.
 *
 * <p>my_attribute: value0 value1 value2 ...
 */
public final class ListSection<T> extends Section<T> {
  private static final long serialVersionUID = 2L;

  private final ImmutableList<ItemOrTextBlock<T>> itemsOrComments;

  ListSection(
      SectionKey<T, ? extends ListSection<T>> sectionKey, ImmutableList<ItemOrTextBlock<T>> items) {
    super(sectionKey);
    this.itemsOrComments = items;
  }

  public Collection<T> items() {
    return itemsOrComments
        .stream()
        .map(item -> item.item)
        .filter(item -> item != null)
        .collect(Collectors.toList());
  }

  public ImmutableList<ItemOrTextBlock<T>> itemsOrComments() {
    return itemsOrComments;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    ListSection<?> that = (ListSection<?>) o;
    return Objects.equal(itemsOrComments, that.itemsOrComments);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), itemsOrComments);
  }

  public static <T> Builder<T> builder(SectionKey<T, ListSection<T>> sectionKey) {
    return new Builder<>(sectionKey, null);
  }

  public static <T> Builder<T> update(
      SectionKey<T, ListSection<T>> sectionKey, @Nullable ListSection<T> section) {
    return new Builder<>(sectionKey, section);
  }

  /** Builder for list sections */
  public static class Builder<T> extends SectionBuilder<T, ListSection<T>> {
    private final List<ItemOrTextBlock<T>> items = new ArrayList<>();

    public Builder(SectionKey<T, ListSection<T>> sectionKey, @Nullable ListSection<T> section) {
      super(sectionKey);
      if (section != null) {
        items.addAll(section.itemsOrComments);
      }
    }

    @CanIgnoreReturnValue
    public final Builder<T> add(T item) {
      items.add(new ItemOrTextBlock<>(item));
      return this;
    }

    @CanIgnoreReturnValue
    public final Builder<T> addAll(List<? extends T> items) {
      for (T item : items) {
        add(item);
      }
      return this;
    }

    @CanIgnoreReturnValue
    public final Builder<T> add(TextBlock textBlock) {
      items.add(new ItemOrTextBlock<T>(textBlock));
      return this;
    }

    @CanIgnoreReturnValue
    public final Builder<T> removeMatches(Predicate<ItemOrTextBlock<T>> predicate) {
      items.removeIf(predicate);
      return this;
    }

    @CanIgnoreReturnValue
    public final Builder<T> remove(T item) {
      items.remove(new ItemOrTextBlock<>(item));
      return this;
    }

    @Override
    public final ListSection<T> build() {
      return new ListSection<>(getSectionKey(), ImmutableList.copyOf(items));
    }
  }
}
