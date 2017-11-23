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
import com.android.tools.idea.gradle.structure.quickfix.QuickFixes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class QuickFixLinkHandler implements LinkHandler {
  @NonNls public static final String QUICK_FIX_PATH_TYPE = "psdFix://";

  @NotNull private final PsContext myContext;

  public QuickFixLinkHandler(@NotNull PsContext context) {
    myContext = context;
  }

  @Override
  public boolean accepts(@NotNull String target) {
    return target.startsWith(QUICK_FIX_PATH_TYPE);
  }

  @Override
  public void navigate(@NotNull String target) {
    String quickFixPath = target.substring(QUICK_FIX_PATH_TYPE.length());
    QuickFixes.executeQuickFix(quickFixPath, myContext);
  }
}