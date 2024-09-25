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
package com.google.idea.common.settings;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import java.util.Collection;
import java.util.stream.Stream;

/** Text which can be searched for in the Settings dialog and 'Help → Find Action'. */
@AutoValue
@CopyAnnotations
@Immutable
public abstract class SearchableText {

  /**
   * Returns the label to use in a {@link com.intellij.openapi.options.Configurable} UI and 'Help →
   * Find Action'/'Search Everywhere' results.
   */
  public abstract String label();

  /** Returns additional search terms for this text. */
  abstract ImmutableSet<String> tags();

  /** Creates {@link SearchableText} for the given UI label. */
  public static SearchableText forLabel(String label) {
    return SearchableText.withLabel(label).build();
  }

  /** Returns a builder for {@link SearchableText} with the given UI label. */
  public static Builder withLabel(String label) {
    return builder().setLabel(label);
  }

  static Builder builder() {
    return new AutoValue_SearchableText.Builder();
  }

  abstract Builder toBuilder();

  /** A builder for {@link SearchableText}. */
  @AutoValue.Builder
  public abstract static class Builder {

    abstract Builder setLabel(String label);

    abstract ImmutableSet.Builder<String> tagsBuilder();

    /**
     * Adds search terms, allowing this text to be a result for the given strings, even if they
     * don't appear in the user-visible {@link #label()}.
     */
    @CanIgnoreReturnValue
    public final Builder addTags(String... tags) {
      for (String tag : tags) {
        tagsBuilder().add(tag);
      }
      return this;
    }

    public abstract SearchableText build();
  }

  /** Returns the {@link SearchableText} of the given settings and texts. */
  public static ImmutableCollection<SearchableText> collect(
      Collection<? extends ConfigurableSetting<?, ?>> settings, SearchableText... texts) {
    return collect(settings, ImmutableList.copyOf(texts));
  }

  /** Returns the {@link SearchableText} of the given settings and texts. */
  public static ImmutableCollection<SearchableText> collect(
      Collection<? extends ConfigurableSetting<?, ?>> settings, Collection<SearchableText> texts) {
    return Stream.concat(settings.stream().map(ConfigurableSetting::searchableText), texts.stream())
        .collect(toImmutableList());
  }
}
