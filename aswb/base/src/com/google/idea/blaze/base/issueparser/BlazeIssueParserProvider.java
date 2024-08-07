/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.issueparser;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser.Parser;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Extension point for providing {@link
 * com.google.idea.blaze.base.issueparser.BlazeIssueParser.Parser}s.
 */
public interface BlazeIssueParserProvider {

  ExtensionPointName<BlazeIssueParserProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BlazeIssueParserProvider");

  static List<Parser> getAllIssueParsers(Project project) {
    return Arrays.stream(EP_NAME.getExtensions())
        .map(provider -> provider.getIssueParsers(project))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  ImmutableList<BlazeIssueParser.Parser> getIssueParsers(Project project);
}
