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
package com.android.tools.idea.gradle.structure.configurables.issues;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.intellij.ui.HyperlinkAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

public class NavigationHyperlinkListener extends HyperlinkAdapter {
  @NotNull private final PsContext myContext;
  @NotNull private final LinkHandler[] myHandlers;

  public NavigationHyperlinkListener(@NotNull PsContext context) {
    this(context,
         new QuickFixLinkHandler(context),
         new GoToPathLinkHandler(context),
         new InternetLinkHandler());
  }

  public NavigationHyperlinkListener(@NotNull PsContext context, @NotNull LinkHandler... handlers) {
    myContext = context;
    myHandlers = handlers;
  }

  @Override
  protected void hyperlinkActivated(HyperlinkEvent e) {
    navigate(e.getDescription());
  }

  public void navigate(@NotNull String target) {
    for (LinkHandler handler : myHandlers) {
      if (handler.accepts(target)) {
        handler.navigate(target);
        break;
      }
    }
  }
}
