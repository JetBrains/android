/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.console;

import com.google.idea.blaze.base.io.AbsolutePathPatcher.AbsolutePathPatcherUtil;
import com.google.idea.blaze.base.issueparser.NonProblemHyperlinkInfo;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.PossiblyDumbAware;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Wraps up a filter result, identifying it as not to be included in occurrence navigation. */
public final class NonProblemFilterWrapper implements Filter, PossiblyDumbAware {

  public static NonProblemFilterWrapper wrap(Filter delegate) {
    return new NonProblemFilterWrapper(delegate);
  }

  private final Filter delegate;

  private NonProblemFilterWrapper(Filter delegate) {
    this.delegate = delegate;
  }

  @Nullable
  @Override
  public Result applyFilter(String line, int entireLength) {
    // Blaze error message uses absolute path for BUILD files. If it's not processed by
    // IssueOutputFilter, it will be processed here. Since we cannot modify how delegate filters
    // process hyper info, update line string before pass it to filter.
    line = AbsolutePathPatcherUtil.fixAllPaths(line);
    Result result = delegate.applyFilter(line, entireLength);
    return result != null ? wrapResult(result) : null;
  }

  private static Result wrapResult(Result result) {
    return new Result(
        result
            .getResultItems()
            .stream()
            .map(NonProblemFilterWrapper::wrapResult)
            .collect(Collectors.toList()));
  }

  private static ResultItem wrapResult(ResultItem item) {
    return new ResultItem(
        item.getHighlightStartOffset(),
        item.getHighlightEndOffset(),
        wrapLink(item.getHyperlinkInfo()),
        item.getHighlightAttributes(),
        item.getFollowedHyperlinkAttributes());
  }

  private static HyperlinkInfo wrapLink(@Nullable HyperlinkInfo link) {
    return link != null ? (NonProblemHyperlinkInfo) link::navigate : null;
  }

  @Override
  public boolean isDumbAware() {
    return delegate instanceof DumbAware;
  }
}
