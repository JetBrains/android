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

import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.intellij.ui.JBColor.GRAY;
import static com.intellij.ui.JBColor.RED;

public class PsdIssue {
  @NotNull private final String myText;
  @NotNull private final Type myType;

  public PsdIssue(@NotNull String text, @NotNull Type type) {
    myText = text;
    myType = type;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public Type getType() {
    return myType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PsdIssue psdIssue = (PsdIssue)o;
    return Objects.equal(myText, psdIssue.myText) &&
           myType == psdIssue.myType;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myText, myType);
  }

  @Override
  public String toString() {
    return myType.name() + ": " + myText;
  }

  public enum Type {
    ERROR(RED), WARNING(GRAY);

    @NotNull private final Color myColor;

    Type(@NotNull Color color) {
      myColor = color;
    }

    @NotNull
    public Color getColor() {
      return myColor;
    }
  }
}
