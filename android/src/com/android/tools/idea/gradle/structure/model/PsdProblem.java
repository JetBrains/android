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
package com.android.tools.idea.gradle.structure.model;

import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class PsdProblem {
  @NotNull private final String myText;
  @NotNull private final Severity mySeverity;

  public PsdProblem(@NotNull String text, @NotNull Severity severity) {
    myText = text;
    mySeverity = severity;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public Severity getSeverity() {
    return mySeverity;
  }

  public enum Severity {
    ERROR(JBColor.RED), WARNING(JBColor.GRAY);

    @NotNull private final Color myColor;

    Severity(@NotNull Color color) {
      myColor = color;
    }

    @NotNull
    public Color getColor() {
      return myColor;
    }
  }
}
