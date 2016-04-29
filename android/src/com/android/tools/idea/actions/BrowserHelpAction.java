/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Opens a help link in a browser window.
 */
public final class BrowserHelpAction extends AnAction {
  @NotNull private final String myDocUrl;

  public BrowserHelpAction(@NotNull String topic, @NotNull String docUrl) {
    super(String.format("%1$s help", topic), String.format("Open help link for %1$s [%2$s]", topic, docUrl), AllIcons.Actions.Help);
    myDocUrl = docUrl;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    BrowserUtil.browse(myDocUrl);
  }
}
