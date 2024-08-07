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
package com.google.idea.blaze.base.lang.buildfile.language.semantics;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;

/**
 * The built-in names available in the BUILD language. This is not a complete list, and is only
 * intended to be used for syntax highlighting.
 *
 * <p>See https://docs.bazel.build/versions/master/skylark/lib/skylark-overview.html
 */
public class BuiltInNamesProvider {

  public static final ImmutableSet<String> GLOBALS =
      ImmutableSet.of(
          "Actions",
          "all",
          "any",
          "aspect",
          "bool",
          "configuration_field",
          "DefaultInfo",
          "depset",
          "dict",
          "dir",
          "enumerate",
          "fail",
          "False",
          "getattr",
          "hasattr",
          "hash",
          "int",
          "len",
          "list",
          "max",
          "min",
          "None",
          "OutputGroupInfo",
          "PACKAGE_NAME",
          "print",
          "provider",
          "range",
          "register_execution_platforms",
          "register_toolchains",
          "REPOSITORY_NAME",
          "repository_rule",
          "repr",
          "reversed",
          "rule",
          "sorted",
          "str",
          "struct",
          "True",
          "tuple",
          "type",
          "zip");

  private static final ImmutableSet<String> FUNCTIONS =
      ImmutableSet.of(
          "load",
          "package",
          "package_group",
          "licenses",
          "exports_files",
          "glob",
          "select",
          "workspace");

  /** Returns all built-in global symbols and function names. */
  public static ImmutableSet<String> getBuiltInNames(Project project) {
    ImmutableSet.Builder<String> builder =
        ImmutableSet.<String>builder().addAll(GLOBALS).addAll(FUNCTIONS);
    BuildLanguageSpec spec = BuildLanguageSpecProvider.getInstance(project).getLanguageSpec();
    if (spec != null) {
      builder = builder.addAll(spec.getKnownRuleNames());
    }
    return builder.build();
  }

  /** Returns all built-in rules and function names. */
  public static ImmutableSet<String> getBuiltInFunctionNames(Project project) {
    ImmutableSet.Builder<String> builder = ImmutableSet.<String>builder().addAll(FUNCTIONS);
    BuildLanguageSpec spec = BuildLanguageSpecProvider.getInstance(project).getLanguageSpec();
    if (spec != null) {
      builder = builder.addAll(spec.getKnownRuleNames());
    }
    return builder.build();
  }
}
