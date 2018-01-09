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
package com.android.tools.adtui.model;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FilterModel {
  private final ArrayList<Consumer<Pattern>> myConsumers = new ArrayList<>();

  private boolean myIsRegex;
  private boolean myIsMatchCase;
  private String myFilterString;

  public void setFilterString(@NotNull String filterString) {
    if (!filterString.equals(myFilterString)) {
      myFilterString = filterString;
      notifyFilterChange();
    }
  }

  public boolean getIsRegex() {
    return myIsRegex;
  }

  public void setIsRegex(boolean regex) {
    if (myIsRegex != regex) {
      myIsRegex = regex;
      notifyFilterChange();
    }
  }

  public boolean getIsMatchCase() {
    return myIsMatchCase;
  }

  public void setIsMatchCase(boolean matchCase) {
    if (myIsMatchCase != matchCase) {
      myIsMatchCase = matchCase;
      notifyFilterChange();
    }
  }

  public void addOnFilterChange(@NotNull Consumer<Pattern> callback) {
    myConsumers.add(callback);
  }

  private void notifyFilterChange() {
    for (Consumer<Pattern> consumer : myConsumers) {
      consumer.consume(getFilterPattern(myFilterString, myIsMatchCase, myIsRegex));
    }
  }

  /**
   * Returns the resulting Pattern that matches those containing the filter string.
   *
   * @param filter      the filter string
   * @param isMatchCase if the Pattern is case sensitive
   * @param isRegex     if the Pattern is a regex match
   * @return the Pattern correspondent to the parameters
   */
  @Nullable
  public static Pattern getFilterPattern(@Nullable String filter, boolean isMatchCase, boolean isRegex) {
    Pattern pattern = null;

    if (filter != null && !filter.isEmpty()) {
      int flags = isMatchCase ? 0 : Pattern.CASE_INSENSITIVE;
      if (isRegex) {
        try {
          pattern = Pattern.compile("^.*" + filter + ".*$", flags);
        }
        catch (PatternSyntaxException e) {
          String error = e.getMessage();
          assert (error != null);
        }
      }
      if (pattern == null) {
        pattern = Pattern.compile("^.*" + Pattern.quote(filter) + ".*$", flags);
      }
    }
    return pattern;
  }
}
