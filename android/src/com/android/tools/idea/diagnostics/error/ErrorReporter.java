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

package com.android.tools.idea.diagnostics.error;

import com.android.annotations.Nullable;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.util.Consumer;
import java.awt.Component;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public class ErrorReporter extends ErrorReportSubmitter {

  @NotNull
  @Override
  public String getReportActionText() {
    return AndroidBundle.message("error.report.to.google.action");
  }

  @Override
  public boolean submit(IdeaLoggingEvent @NotNull [] events,
                        @Nullable String description,
                        @Nullable Component parentComponent,
                        @NotNull Consumer<? super SubmittedReportInfo> callback) {
    // FIXME-ank: depends on modified intellij platform (does not compile with the canonical intellij)
    return true;
  }
}
