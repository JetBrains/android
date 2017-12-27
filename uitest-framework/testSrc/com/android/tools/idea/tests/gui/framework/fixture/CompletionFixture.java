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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.project.Project;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class CompletionFixture {
  // In most cases a 8 second delay is not needed.
  // However there may be a lot of computations done the first time android completions are shown.
  // This is 2 x worst case scenario on a slow machine.
  private static final long MAX_DELAY_SECONDS = 8;

  private final LookupManager myManager;

  public CompletionFixture(@NotNull IdeFrameFixture frame) {
    myManager = LookupManager.getInstance(frame.getProject());
  }

  public void waitForCompletionsToShow() {
    Wait.seconds(MAX_DELAY_SECONDS).expecting("lookup to show").until(this::isCompletionsShowing);
  }

  public void waitForCompletionsToHide() {
    Wait.seconds(MAX_DELAY_SECONDS).expecting("lookup to hide").until(() -> !isCompletionsShowing());
  }

  private boolean isCompletionsShowing() {
    Lookup lookup = myManager.getActiveLookup();
    if (!(lookup instanceof LookupImpl)) {
      return false;
    }
    LookupImpl impl = (LookupImpl)lookup;
    return impl.isVisible() && impl.getList().isShowing();
  }
}
