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
package com.google.idea.blaze.base.scope;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.IssueOutput.Category;
import java.util.List;

/** Test class that collects issues. */
public class ErrorCollector implements OutputSink<IssueOutput> {

  private final StringBuilder printOutput;
  List<IssueOutput> issues = Lists.newArrayList();

  public ErrorCollector() {
    this.printOutput = new StringBuilder();
  }

  public ErrorCollector(StringBuilder printOutput) {
    this.printOutput = printOutput;
  }

  @Override
  public Propagation onOutput(IssueOutput output) {
    issues.add(output);
    return Propagation.Continue;
  }

  public void assertNoIssues() {
    assertWithMessage(
            "There were issues during the Blaze invocation. Please check the invocation's log: %s",
            printOutput.toString())
        .that(issues)
        .isEmpty();
  }

  public void assertHasErrors() {
    assertThat(issues.stream().anyMatch(i -> i.getCategory() == Category.ERROR)).isTrue();
  }

  public void assertIssues(String... requiredMessages) {
    List<String> messages = Lists.newArrayList();
    for (IssueOutput issue : issues) {
      messages.add(issue.getMessage());
    }
    assertThat(messages).containsExactly((Object[]) requiredMessages);
  }

  public void assertIssueContaining(String s) {
    assertWithMessage("Issues must contain: " + s)
        .that(issues.stream().anyMatch((issue) -> issue.getMessage().contains(s)))
        .isTrue();
  }
}
