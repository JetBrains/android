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
package com.android.tools.profilers;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Notification data that will ultimately be displayed to the user in a toast or bubble.
 *
 * If optional URL data is included, it should be shown on a separate line as its own link.
 */
public final class Notification {
  public enum Severity {
    INFO,
    WARNING,
    ERROR
  }

  @NotNull private final Severity mySeverity;
  @NotNull private final String myTitle;
  @NotNull private final String myText;
  @Nullable private final UrlData myUrlData;

  private Notification(@NotNull Builder builder) {
    mySeverity = builder.mySeverity;
    myTitle = builder.myTitle;
    myText = builder.myText;
    myUrlData = builder.myUrlData;
  }

  @NotNull
  public Severity getSeverity() {
    return mySeverity;
  }

  @NotNull
  public String getTitle() {
    return myTitle;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @Nullable
  public UrlData getUrlData() {
    return myUrlData;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Notification that = (Notification)o;
    return mySeverity == that.mySeverity &&
           Objects.equals(myTitle, that.myTitle) &&
           Objects.equals(myText, that.myText) &&
           Objects.equals(myUrlData, that.myUrlData);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myText, myTitle, myText, myUrlData);
  }

  public final static class Builder {
    @NotNull private Severity mySeverity = Severity.INFO;
    @NotNull private final String myTitle;
    @NotNull private final String myText;
    @Nullable private UrlData myUrlData;

    public Builder(@NotNull String title, @NotNull String text) {
      myTitle = title;
      myText = text;
    }

    @NotNull
    public Builder setSeverity(@NotNull Severity severity) {
      mySeverity = severity;
      return this;
    }

    @NotNull
    public Builder setUrlData(@NotNull UrlData urlData) {
      myUrlData = urlData;
      return this;
    }

    @NotNull
    public Builder setUrl(@NotNull String url, @NotNull String text) {
      return setUrlData(new UrlData(url, text));
    }

    @NotNull
    public Notification build() {
      return new Notification(this);
    }
  }

  public final static class UrlData {
    @NotNull private final String myUrl;
    @NotNull private final String myText;

    public UrlData(@NotNull String url, @NotNull String text) {
      myUrl = url;
      myText = text;
    }

    @NotNull
    public String getUrl() {
      return myUrl;
    }

    @NotNull
    public String getText() {
      return myText;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      UrlData urlData = (UrlData)o;
      return Objects.equals(myUrl, urlData.myUrl) &&
             Objects.equals(myText, urlData.myText);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myUrl, myText);
    }
  }
}
