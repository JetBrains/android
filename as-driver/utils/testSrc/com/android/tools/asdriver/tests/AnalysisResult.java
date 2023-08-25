/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.asdriver.tests;

import com.android.tools.asdriver.proto.ASDriver;
import com.intellij.lang.annotation.HighlightSeverity;
import java.util.Arrays;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides a nicer API than the underlying proto's builders. The proto's generated classes aren't
 * accessible to test classes anyway.
 */
public class AnalysisResult {
  @NotNull
  HighlightSeverity severity;
  @NotNull
  String text;
  @Nullable
  String description;
  @Nullable
  String toolId;
  int lineNumber;

  public AnalysisResult(@NotNull HighlightSeverity severity, @NotNull String text, @Nullable String description, @Nullable String toolId, int lineNumber) {
    this.severity = severity;
    this.text = text;
    this.description = description;
    this.toolId = toolId;
    this.lineNumber = lineNumber;
  }

  @NotNull
  public HighlightSeverity getSeverity() {
    return severity;
  }

  @NotNull
  public String getText() {
    return text;
  }

  @Nullable
  public String getDescription() {
    return description;
  }

  @Nullable
  public String getToolId() {
    return toolId;
  }

  public int getLineNumber() {
    return lineNumber;
  }

  @Override
  public String toString() {
    return "AnalysisResult{" +
           "severity=" + severity +
           ", text='" + text + '\'' +
           ", description='" + description + '\'' +
           ", toolId='" + toolId + '\'' +
           ", lineNumber=" + lineNumber +
           '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AnalysisResult other)) return false;

    return Objects.equals(other.severity, severity) &&
           Objects.equals(other.text, text) &&
           Objects.equals(other.description, description) &&
           Objects.equals(other.toolId, toolId) &&
           other.lineNumber == lineNumber;
  }

  @Override
  public int hashCode() {
    return Objects.hash(severity, text, description, toolId, lineNumber);
  }

  public static AnalysisResult fromProto(ASDriver.AnalysisResult protoResult) {
    ASDriver.AnalysisResult.HighlightSeverity protoSeverity = protoResult.getSeverity();
    HighlightSeverity desiredSeverity = new HighlightSeverity(protoSeverity.getName(), protoSeverity.getValue());
    // Try to reuse standard HighlightSeverity instances so that additional properties are set, e.g. display name.
    HighlightSeverity severity =
      Arrays.stream(HighlightSeverity.DEFAULT_SEVERITIES).filter((hs) -> hs.equals(desiredSeverity)).findFirst()
        .orElse(desiredSeverity);
    String description = protoResult.hasDescription() ? protoResult.getDescription() : null;
    String toolId = protoResult.hasToolId() ? protoResult.getToolId() : null;

    return new AnalysisResult(severity, protoResult.getText(), description, toolId, protoResult.getLineNumber());
  }
}
