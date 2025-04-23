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
package com.android.tools.adtui.model.filter;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * The {@code Filter} class represents a Pattern that matches those containing a certain compiled string.
 * See {@link #matches(String)}
 */
public final class Filter {

  public static final Filter EMPTY_FILTER = new Filter();

  @NotNull final private String myFilterString;
  final private boolean myIsMatchCase;
  final private boolean myIsRegex;
  @Nullable final private Pattern myPattern;

  public Filter() {
    this("");
  }

  public Filter(@NotNull String filterString) {
    this(filterString, false, false);
  }

  public Filter(@NotNull String filterString, boolean isMatchCase, boolean isRegex) {
    myFilterString = filterString;
    myIsMatchCase = isMatchCase;
    myIsRegex = isRegex;
    myPattern = createFilterPattern();
  }

  @NotNull
  public String getFilterString() {
    return myFilterString;
  }

  public boolean isMatchCase() {
    return myIsMatchCase;
  }

  public boolean isRegex() {
    return myIsRegex;
  }

  public boolean isEmpty() {
    return myPattern == null;
  }

  /**
   * Attempts to match a string against the filter pattern.
   *
   * The match process always succeeds when {@link #isEmpty()} is true, which is usually caused caused by an empty filter string.
   * Otherwise, the filter string is compiled as pattern with following configurations:
   * {@link #isRegex()}:      if the pattern should match the string as a regex
   * {@link #isMatchCase()}:  if the pattern is case sensitive
   *
   * A string is matched if one of its substrings matches the pattern.
   */
  public boolean matches(@NotNull String string) {
    return myPattern == null || myPattern.matcher(string).matches();
  }

  @Nullable
  private Pattern createFilterPattern() {
    int flags = myIsMatchCase ? 0 : Pattern.CASE_INSENSITIVE;
    Pattern pattern = null;

    if (!myFilterString.isEmpty()) {
      if (myIsRegex) {
        try {
          pattern = Pattern.compile("^.*" + myFilterString + ".*$", flags);
        }
        catch (PatternSyntaxException e) {
          assert e.getMessage() != null;
        }
      }
      if (pattern == null) {
        pattern = Pattern.compile("^.*" + Pattern.quote(myFilterString) + ".*$", flags);
      }
    }
    return pattern;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Filter) {
      Filter filter = ((Filter)obj);
      if (myPattern == null) {
        return filter.myPattern == null;
      }
      return myPattern.pattern().equals(filter.myPattern) && myPattern.flags() == filter.myPattern.flags();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return myPattern == null ? 0 : Objects.hash(myPattern.pattern(), myPattern.flags());
  }
}
