/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.cpp.navigation;

import com.google.common.base.Stopwatch;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import java.util.concurrent.TimeUnit;

/** A wrapper {@link ProgressIndicator} with a timeout. */
public class WatchdogIndicator extends AbstractProgressIndicatorExBase {

  private final ProgressIndicator indicator;
  private final long timeout;
  private final TimeUnit timeUnit;
  private final Stopwatch stopwatch;

  public WatchdogIndicator(ProgressIndicator indicator, long timeout, TimeUnit timeUnit) {
    this.indicator = indicator;
    this.timeout = timeout;
    this.timeUnit = timeUnit;
    this.stopwatch = Stopwatch.createStarted();
  }

  @Override
  public boolean isCanceled() {
    return indicator.isCanceled() || isTimeout();
  }

  private boolean isTimeout() {
    return stopwatch.elapsed(timeUnit) > timeout;
  }
}
