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
package com.google.idea.blaze.qsync.query;

import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Formattable;
import java.util.Formatter;
import java.util.Optional;

/** Represents arguments to a {@code query} invocation. */
@AutoValue
public abstract class QuerySpec implements Formattable {

  public abstract Path workspaceRoot();

  /** The set of package patterns to include. */
  abstract ImmutableList<String> includes();

  /** The set of package patterns to include. */
  abstract ImmutableList<String> excludes();

  // LINT.IfChange
  @Memoized
  public ImmutableList<String> getQueryFlags() {
    return ImmutableList.of(
        "--output=streamed_proto", "--relative_locations=true", "--consistent_labels=true");
  }

  @Memoized
  public Optional<String> getQueryExpression() {
    // This is the main query, note the use of :* that means that the query output has
    // all the files in that directory too. So we can identify all that is reachable.
    if (includes().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        "("
            + includes().stream().map(s -> String.format("%s:*", s)).collect(joining(" + "))
            + excludes().stream().map(s -> String.format(" - %s:*", s)).collect(joining())
            + ")");
  }

  // LINT.ThenChange(/aswb/aswb/testdata/projects/test_projects.bzl)

  @Override
  public final String toString() {
    return Joiner.on(' ')
        .join(
            ImmutableList.builder()
                .add("query")
                .addAll(getQueryFlags())
                .add(getQueryExpression().orElse("<empty>"))
                .build());
  }

  @Override
  public void formatTo(Formatter formatter, int flags, int width, int precision) {
    // We implement Formattable for custom "precision" (max length) handling to allow a truncated
    // query expression to be shown as such in the log, using normal string formatting.
    String s = toString();
    if (precision == -1 || s.length() < precision) {
      formatter.format(s);
      return;
    }
    final String truncated = "<truncated>";
    if (precision < truncated.length()) {
      formatter.format(s.substring(0, precision));
      return;
    }
    formatter.format(s.substring(0, precision - truncated.length()));
    formatter.format(truncated);
  }

  public static Builder builder() {
    return new AutoValue_QuerySpec.Builder();
  }

  /**
   * Builder for {@link QuerySpec}.
   *
   * <p>This builder supports:
   *
   * <ul>
   *   <li>paths, representing roots to query (or exclude) including all subpackages
   *   <li>packages, representing individual packages to query (or exclude) without subpackages.
   * </ul>
   */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder workspaceRoot(Path workspaceRoot);

    abstract ImmutableList.Builder<String> includesBuilder();

    abstract ImmutableList.Builder<String> excludesBuilder();

    @CanIgnoreReturnValue
    public Builder includePath(Path include) {
      // Convert root directories into blaze target patterns:
      includesBuilder().add(String.format("//%s/...", include));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder excludePath(Path exclude) {
      // Convert root directories into blaze target patterns:
      excludesBuilder().add(String.format("//%s/...", exclude));
      return this;
    }

    public Builder includePackages(Collection<Path> packages) {
      packages.stream().map(p -> String.format("//%s", p)).forEach(includesBuilder()::add);
      return this;
    }

    public Builder excludePackages(Collection<Path> packages) {
      packages.stream().map(p -> String.format("//%s", p)).forEach(excludesBuilder()::add);
      return this;
    }

    public abstract QuerySpec build();
  }
}
