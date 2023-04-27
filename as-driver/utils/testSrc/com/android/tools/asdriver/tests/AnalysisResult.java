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
import org.jetbrains.annotations.Nullable;

/**
 * Provides a nicer API than the underlying proto's builders. The proto's generated classes aren't
 * accessible to test classes anyway.
 */
public class AnalysisResult {
  HighlightSeverity severity;
  String text;
  @Nullable
  String description;
  @Nullable
  String toolId;
  int lineNumber;

  public AnalysisResult(HighlightSeverity severity, String text, @Nullable String description, @Nullable String toolId, int lineNumber) {
    this.severity = severity;
    this.text = text;
    this.description = description;
    this.toolId = toolId;
    this.lineNumber = lineNumber;
  }

  public HighlightSeverity getSeverity() {
    return severity;
  }

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

  public static AnalysisResult fromProto(ASDriver.AnalysisResult protoResult) {
    String severityString = protoResult.getSeverity().toString();
    HighlightSeverity
      severity = Arrays.stream(HighlightSeverity.DEFAULT_SEVERITIES).filter((hs) -> hs.getName().equals(severityString)).findFirst()
      .orElseThrow();
    String description = protoResult.hasDescription() ? protoResult.getDescription() : null;
    String toolId = protoResult.hasToolId() ? protoResult.getToolId() : null;

    return new AnalysisResult(severity, protoResult.getText(), description, toolId, protoResult.getLineNumber());
  }
}
