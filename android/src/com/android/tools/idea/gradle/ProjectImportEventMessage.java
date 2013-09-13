/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle;

import com.android.tools.idea.gradle.service.notification.NotificationHyperlink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/**
 * Describes an unusual event that occurred during a project import.
 */
public class ProjectImportEventMessage implements Serializable {
  @NotNull private final String myCategory;
  @NotNull private final Content myContent;

  public ProjectImportEventMessage(@NotNull String category, @NotNull String text) {
    this(category, text, null);
  }

  public ProjectImportEventMessage(@NotNull String category, @NotNull String text, @Nullable NotificationHyperlink hyperlink) {
    myCategory = category;
    myContent = new Content(text, hyperlink);
  }

  @NotNull
  public String getCategory() {
    return myCategory;
  }

  @NotNull
  public Content getContent() {
    return myContent;
  }

  @Override
  public String toString() {
    String content = myContent.getText();
    NotificationHyperlink hyperlink = myContent.getHyperlink();
    if (hyperlink != null) {
      content += " " + hyperlink.toString();
    }
    if (myCategory.isEmpty()) {
      return content;
    }
    return myCategory + " " + content;
  }

  public static class Content {
    @NotNull private final String myText;
    @Nullable private final NotificationHyperlink myHyperlink;

    Content(@NotNull String text, @Nullable NotificationHyperlink hyperlink) {
      myText = text;
      myHyperlink = hyperlink;
    }

    @NotNull
    public String getText() {
      return myText;
    }

    @Nullable
    public NotificationHyperlink getHyperlink() {
      return myHyperlink;
    }
  }
}
