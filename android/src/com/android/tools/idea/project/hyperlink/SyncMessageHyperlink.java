/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import java.util.Collection;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Base hyperlink class for notification purposes.
 */
public abstract class SyncMessageHyperlink implements SyncMessageFragment {
  @NotNull private final String myUrl;
  @NotNull private final String myValue;

  protected SyncMessageHyperlink(@NotNull String url, @NotNull String text) {
    myUrl = url;
    myValue = String.format("<a href=\"%1$s\">%2$s</a>", StringUtil.escapeXml(url), text);
  }

  protected abstract void execute(@NotNull Project project);

  @Override
  public final void executeHandler(@NotNull Project project, @NotNull HyperlinkEvent event) {
    execute(project);
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  @Override
  public Collection<String> getUrls() {
    return ImmutableList.of(myUrl);
  }

  @Override
  public String toString() {
    return toHtml();
  }

  @Override
  @NotNull
  public String toHtml() {
    return myValue;
  }
}
