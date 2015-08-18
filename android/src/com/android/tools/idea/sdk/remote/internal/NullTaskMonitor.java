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

package com.android.tools.idea.sdk.remote.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.ILogger;
import com.android.utils.NullLogger;


/**
 * A no-op implementation of the {@link ITaskMonitor} interface.
 * <p/>
 * This can be passed to methods that require a monitor when the caller doesn't
 * have any UI to update or means to report tracked progress.
 * A custom {@link ILogger} is used. Clients could use {@link NullLogger} if
 * they really don't care about the logging either.
 */
public class NullTaskMonitor implements ITaskMonitor {

  private final ILogger mLog;

  /**
   * Creates a no-op {@link ITaskMonitor} that defers logging to the specified
   * logger.
   * <p/>
   * This can be passed to methods that require a monitor when the caller doesn't
   * have any UI to update or means to report tracked progress.
   *
   * @param log An {@link ILogger}. Must not be null. Consider using {@link NullLogger}.
   */
  public NullTaskMonitor(ILogger log) {
    mLog = log;
  }

  @Override
  public void setDescription(String format, Object... args) {
    // pass
  }

  @Override
  public void log(String format, Object... args) {
    mLog.info(format, args);
  }

  @Override
  public void logError(String format, Object... args) {
    mLog.error(null /*throwable*/, format, args);
  }

  @Override
  public void logVerbose(String format, Object... args) {
    mLog.verbose(format, args);
  }

  @Override
  public void setProgressMax(int max) {
    // pass
  }

  @Override
  public int getProgressMax() {
    return 0;
  }

  @Override
  public void incProgress(int delta) {
    // pass
  }

  /**
   * Always return 1.
   */
  @Override
  public int getProgress() {
    return 1;
  }

  /**
   * Always return false.
   */
  @Override
  public boolean isCancelRequested() {
    return false;
  }

  @Override
  public ITaskMonitor createSubMonitor(int tickCount) {
    return this;
  }

  /**
   * Always return false.
   */
  @Override
  public boolean displayPrompt(final String title, final String message) {
    return false;
  }

  /**
   * Always return null.
   */
  @Override
  public UserCredentials displayLoginCredentialsPrompt(String title, String message) {
    return null;
  }

  // --- ILogger ---

  @Override
  public void error(@Nullable Throwable t, @Nullable String errorFormat, Object... args) {
    mLog.error(t, errorFormat, args);
  }

  @Override
  public void warning(@NonNull String warningFormat, Object... args) {
    mLog.warning(warningFormat, args);
  }

  @Override
  public void info(@NonNull String msgFormat, Object... args) {
    mLog.info(msgFormat, args);
  }

  @Override
  public void verbose(@NonNull String msgFormat, Object... args) {
    mLog.verbose(msgFormat, args);
  }
}
