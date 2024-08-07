/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.libraries;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;

/** Interface for collecting lint rule jars */
public interface LintCollector {
  ExtensionPointName<LintCollector> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.LintCollector");

  ImmutableList<File> collectLintJars(Project project, BlazeProjectData blazeProjectData);

  boolean isEnabled();

  static ImmutableList<File> getLintJars(Project project, BlazeProjectData blazeProjectData) {
    return EP_NAME
        .extensions()
        .filter(ep -> ep.isEnabled())
        .map(ep -> ep.collectLintJars(project, blazeProjectData))
        .flatMap(List::stream)
        .collect(toImmutableList());
  }
}
