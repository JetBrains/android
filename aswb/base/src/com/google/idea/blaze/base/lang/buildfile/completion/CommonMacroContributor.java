/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.completion;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.intellij.openapi.extensions.ExtensionPointName;

/** Extension point for exposing common macros in completion results. */
public interface CommonMacroContributor {

  ExtensionPointName<CommonMacroContributor> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.CommonMacroContributor");

  ImmutableList<CommonMacros> getMacros();

  static ImmutableList<CommonMacros> getAllMacros() {
    return EP_NAME
        .extensions()
        .flatMap(c -> c.getMacros().stream())
        .distinct()
        .collect(toImmutableList());
  }

  /** Data class defining a set of loaded macros within a single package. */
  @AutoValue
  abstract class CommonMacros {
    /** Starlark file reference of the form "//foo:bar.bzl" */
    public abstract String location();

    /** The loaded symbol within the file specified by 'location'. */
    public abstract ImmutableList<String> functionNames();

    public static CommonMacros.Builder builder() {
      return new AutoValue_CommonMacroContributor_CommonMacros.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {

      public abstract CommonMacros.Builder setLocation(String location);

      protected abstract ImmutableList.Builder<String> functionNamesBuilder();

      @CanIgnoreReturnValue
      public Builder addFunctionName(String fn) {
        functionNamesBuilder().add(fn);
        return this;
      }

      @CanIgnoreReturnValue
      public Builder addFunctionNames(String... functions) {
        functionNamesBuilder().add(functions);
        return this;
      }

      public abstract Builder setFunctionNames(ImmutableList<String> list);

      public abstract CommonMacros build();
    }
  }
}
