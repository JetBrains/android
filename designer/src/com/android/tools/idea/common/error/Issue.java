/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.error;

import com.android.tools.idea.common.model.NlComponent;
import com.intellij.lang.annotation.HighlightSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkListener;
import java.util.stream.Stream;

/**
 * Represent an Error that can be displayed in the {@link IssuePanel}.
 */
public abstract class Issue {

  public static final String EXECUTE_FIX = "Execute Fix: ";

  /**
   * @return A short summary of the error description
   */
  @NotNull
  public abstract String getSummary();

  /**
   * @return The description of the error. It can contains some HTML tag
   */
  @NotNull
  public abstract String getDescription();

  @NotNull
  public abstract HighlightSeverity getSeverity();

  /**
   * @return An indication of the origin of the error like the Component causing the error
   */
  @Nullable
  public abstract NlComponent getSource();

  /**
   * @return The priority between 1 and 10.
   */
  @NotNull
  public abstract String getCategory();

  /**
   * Allows the {@link Issue} to return an HyperlinkListener to handle embedded links
   */
  @Nullable
  public HyperlinkListener getHyperlinkListener() {
    return null;
  }

  /**
   * @return a Steam of pair containing the description of the fix as the first element
   * and a {@link Runnable} to execute the fix
   */
  @NotNull
  public Stream<Fix> getFixes() {
    return Stream.empty();
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) return true;
    if (!(object instanceof Issue)) return false;
    Issue issue = (Issue)object;

    return issue.getSeverity().equals(getSeverity())
           && issue.getSummary().equals(getSummary())
           && issue.getDescription().equals(getDescription())
           && issue.getCategory().equals(getCategory())
           && issue.getSource() == getSource();
  }

  @Override
  public int hashCode() {
    int result = 13;
    result += 17 * getSeverity().hashCode();
    result += 19 * getSummary().hashCode();
    result += 23 * getDescription().hashCode();
    result += 29 * getCategory().hashCode();
    NlComponent source = getSource();
    if (source != null) {
      result += 31 * source.hashCode();
    }
    return result;
  }

  /**
   * Representation of a quick fix for the issue
   */
  public static class Fix {
    String myDescription;
    Runnable myRunnable;

    /**
     * @param description Descption of the fix
     * @param runnable    Action to exectute the fix
     */
    public Fix(@NotNull String description, @NotNull Runnable runnable) {
      myDescription = description;
      myRunnable = runnable;
    }

    @NotNull
    public String getDescription() {
      return myDescription;
    }

    @NotNull
    public Runnable getRunnable() {
      return myRunnable;
    }
  }
}
