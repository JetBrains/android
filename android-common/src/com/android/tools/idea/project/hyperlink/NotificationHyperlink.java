/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.project.hyperlink;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Base hyperlink class for notification purposes.
 */
public abstract class NotificationHyperlink {
  @NotNull private final String myUrl;
  @NotNull private final String myValue;

  private boolean myCloseOnClick;

  protected NotificationHyperlink(@NotNull String url, @NotNull String text) {
    myUrl = url;
    myValue = String.format("<a href=\"%1$s\">%2$s</a>", StringUtil.escapeXml(url), text);
  }

  protected abstract void execute(@NotNull Project project);

  public boolean executeIfClicked(@NotNull Project project, @NotNull HyperlinkEvent event) {
    if (myUrl.equals(event.getDescription())) {
      execute(project);
      return true;
    }
    return false;
  }

  public boolean isCloseOnClick() {
    return myCloseOnClick;
  }

  @NotNull
  public NotificationHyperlink setCloseOnClick(boolean closeOnClick) {
    myCloseOnClick = closeOnClick;
    return this;
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @Override
  public String toString() {
    return toHtml();
  }

  @NotNull
  public String toHtml() {
    return myValue;
  }
}
