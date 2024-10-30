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

import static com.google.common.collect.Iterables.concat;
import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

/** Represents arguments to a {@code query} invocation. */
@AutoValue
public abstract class QuerySpec implements TruncatingFormattable {

  /**
   * A way to transform the query specification to a bazel query and flags.
   *
   * <p>Querying all targets in the project view may generate too large output,
   * while querying just targets the IDE needs may result in a too slow query.
   *
   * <p>{@link QueryStrategy} instances allow experiment with queries and bazel flags.
   */
  public enum QueryStrategy {
    PLAIN {
      @Override
      public ImmutableList<String> getQueryFlags() {
        return ImmutableList.of(
          "--output=streamed_proto",
          "--relative_locations=true",
          "--consistent_labels=true"
        );
      }

      @Override
      public Optional<String> getQueryExpression(QuerySpec querySpec) {
        String baseExpression = baseExpression(querySpec);
        if (baseExpression.isEmpty()) {
          return Optional.empty();
        }
        return Optional.of("(" + baseExpression + ")");
      }
    },

    FILTERING_TO_KNOWN_AND_USED_TARGETS {
      @Override
      public ImmutableList<String> getQueryFlags() {
        return ImmutableList.of(
          "--output=streamed_proto",
          "--noproto:rule_inputs_and_outputs",
          "--relative_locations=true",
          "--consistent_labels=true"
        );
      }

      @Override
      public Optional<String> getQueryExpression(QuerySpec querySpec) {
        final var baseQuery = baseExpression(querySpec);
        if (baseQuery.isEmpty()) {
          return Optional.empty();
        }
        String ruleClassPattern = String.join("|", concat(SOURCE_FILE_AS_LIST, querySpec.supportedRuleClasses()));
        return Optional.of(

          "let base = " +
          baseQuery +
          "\n" +
          " in let known = kind(\"" + ruleClassPattern + "\", $base) \n" +
          " in let unknown = $base except $known \n" +
          " in $known union ($base intersect allpaths($known, $unknown)) \n");
      }
    };

    public abstract ImmutableList<String> getQueryFlags();
    public abstract Optional<String> getQueryExpression(QuerySpec querySpec);

    protected final String baseExpression(QuerySpec querySpec) {
      return querySpec.includes().stream().map(s -> String.format("%s:*", s)).collect(joining(" + ")) +
             querySpec.excludes().stream().map(s -> String.format(" - %s:*", s)).collect(joining());
    }
  }

  private static final ImmutableList<String> SOURCE_FILE_AS_LIST = ImmutableList.of("source file");

  public abstract QueryStrategy queryStrategy();

  public abstract Path workspaceRoot();

  /** The set of package patterns to include. */
  abstract ImmutableList<String> includes();

  /** The set of package patterns to include. */
  abstract ImmutableList<String> excludes();

  /** The set of rule classes that query sync supports directly. */
  abstract ImmutableSet<String> supportedRuleClasses();

  @Memoized
  public ImmutableList<String> getQueryFlags() {
    return queryStrategy().getQueryFlags();
  }

  @Memoized
  public Optional<String> getQueryExpression() {
    return queryStrategy().getQueryExpression(this);
  }

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

  public static Builder builder(QuerySpec.QueryStrategy queryStrategy) {
    return new AutoValue_QuerySpec.Builder().queryStrategy(queryStrategy);
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
    public abstract Builder queryStrategy(QueryStrategy queryStrategy);

    public abstract Builder workspaceRoot(Path workspaceRoot);

    abstract ImmutableList.Builder<String> includesBuilder();

    abstract ImmutableList.Builder<String> excludesBuilder();

    public abstract Builder supportedRuleClasses(ImmutableSet<String> supportedRuleClasses);

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
