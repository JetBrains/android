// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.dsl.api;

import com.android.tools.idea.diagnostics.crash.StudioCrashReporter;
import com.android.tools.idea.diagnostics.crash.StudioExceptionReport;

public class BuildModelStudioCrashReporter implements BuildModelErrorReporter{
  @Override
  public void report(Throwable e) {
      StudioCrashReporter reporter = StudioCrashReporter.getInstance();
      reporter.submit(new StudioExceptionReport.Builder().setThrowable(e, false).build());
  }
}
