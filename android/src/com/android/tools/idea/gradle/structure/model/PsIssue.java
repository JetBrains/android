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

import com.android.tools.idea.gradle.structure.navigation.PsNavigationPath;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.icons.AllIcons.General.BalloonError;
import static com.intellij.icons.AllIcons.General.BalloonInformation;
import static com.intellij.icons.AllIcons.General.BalloonWarning;
import static com.intellij.ui.JBColor.GRAY;
import static com.intellij.ui.JBColor.RED;
import static com.intellij.ui.JBColor.YELLOW;

public class PsIssue {
  @NotNull private final String myText;
  @NotNull private final Type myType;
  @NotNull private final PsNavigationPath myPath;

  @Nullable private final String myDescription;

  public PsIssue(@NotNull String text, @NotNull PsNavigationPath path, @NotNull Type type) {
    myText = text;
    myPath = path;
    myType = type;
    myDescription = null;
  }

  public PsIssue(@NotNull String text, @NotNull String description, @NotNull PsNavigationPath path, @NotNull Type type) {
    myText = text;
    myDescription = description;
    myPath = path;
    myType = type;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }

  @NotNull
  public Type getType() {
    return myType;
  }

  @NotNull
  public PsNavigationPath getPath() {
    return myPath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PsIssue that = (PsIssue)o;
    return Objects.equal(myText, that.myText)
           && Objects.equal(myDescription, that.myDescription)
           && Objects.equal(myPath, that.getPath())
           && myType == that.myType;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myText, myDescription, myPath, myType);
  }

  @Override
  public String toString() {
    return myType.name() + ": " + myText;
  }

  public enum Type {
    ERROR("Error", BalloonError, RED, 0), WARNING("Warning", BalloonWarning, YELLOW, 1), INFO("Information", BalloonInformation, GRAY, 2);

    @NotNull private final Icon myIcon;
    @NotNull private final String myText;
    @NotNull private final Color myColor;
    private final int myPriority;

    Type(@NotNull String text, @NotNull Icon icon, @NotNull Color color, int priority) {
      myText = text;
      myColor = color;
      myIcon = icon;
      myPriority = priority;
    }

    @NotNull
    public String getText() {
      return myText;
    }

    @NotNull
    public Icon getIcon() {
      return myIcon;
    }

    @NotNull
    public Color getColor() {
      return myColor;
    }

    public int getPriority() {
      return myPriority;
    }
  }
}
