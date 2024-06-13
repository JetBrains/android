/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common;

import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LintProblemData {
  private final Incident myIncident;
  private final Issue myIssue;
  private final String myMessage;
  private final TextRange myTextRange;
  private final Severity myConfiguredSeverity;
  private final LintFix myQuickfixData;

  public LintProblemData(@NotNull Incident incident,
                         @NotNull Issue issue,
                         @NotNull String message,
                         @NotNull TextRange textRange,
                         @Nullable Severity configuredSeverity,
                         @Nullable LintFix quickfixData) {
    myIncident = incident;
    myIssue = issue;
    myTextRange = textRange;
    myMessage = message;
    myConfiguredSeverity = configuredSeverity;
    myQuickfixData = quickfixData;
  }

  @NotNull
  public Incident getIncident() {
    return myIncident;
  }

  @NotNull
  public Issue getIssue() {
    return myIssue;
  }

  @NotNull
  public TextRange getTextRange() {
    return myTextRange;
  }

  @NotNull
  public String getMessage() {
    return myMessage;
  }

  @Nullable
  public Severity getConfiguredSeverity() {
    return myConfiguredSeverity;
  }

  @Nullable
  public LintFix getQuickfixData() {
    return myQuickfixData;
  }
}
