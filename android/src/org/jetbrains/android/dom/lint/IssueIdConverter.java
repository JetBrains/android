/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.dom.lint;

import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.detector.api.Issue;
import com.google.common.collect.ImmutableMap;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class IssueIdConverter extends ResolvingConverter<Issue> {
  private static ImmutableMap<String, Issue> ourIssues = null;

  @NotNull
  public static ImmutableMap<String, Issue> getIdSet() {
    if (ourIssues == null) {
      final ImmutableMap.Builder<String, Issue> builder = ImmutableMap.builder();
      for (Issue issue : new BuiltinIssueRegistry().getIssues()) {
        builder.put(issue.getId(), issue);
      }

      ourIssues = builder.build();
    }

    return ourIssues;
  }

  @NotNull
  @Override
  public Collection<Issue> getVariants(ConvertContext context) {
    return getIdSet().values();
  }

  @Nullable
  @Override
  public Issue fromString(@Nullable @NonNls String s, ConvertContext context) {
    return getIdSet().get(s);
  }

  @Nullable
  @Override
  public String toString(@Nullable Issue issue, ConvertContext context) {
    return issue == null ? null : issue.getId();
  }

  @Nullable
  @Override
  public LookupElement createLookupElement(Issue issue) {
    return LookupElementBuilder.create(issue, issue.getId());
  }
}
