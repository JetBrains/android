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
package com.google.idea.blaze.android;

import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** An issue output sink that collects the messages outputted to it. */
public class MessageCollector implements OutputSink<IssueOutput> {
  private final ArrayList<String> messages = new ArrayList<>();

  @Override
  public Propagation onOutput(@NotNull IssueOutput output) {
    messages.add(output.getMessage());
    return Propagation.Continue;
  }

  public List<String> getMessages() {
    return messages;
  }
}
