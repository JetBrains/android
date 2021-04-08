/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

/**
 * Delegates to an Alarm but synchronizes on disposal so we don't try to execute a request after it's disposed.
 */
final class SafeAlarm implements Disposable {
  private final @NotNull Alarm myAlarm = new Alarm(this);

  SafeAlarm(Disposable parentDisposable) {
    Disposer.register(parentDisposable, this);
  }

  @Override
  public synchronized void dispose() {
    myAlarm.dispose();
  }

  public synchronized void addRequest(@NotNull Runnable request, long delayMillis) {
    if (myAlarm.isDisposed()) {
      return;
    }
    myAlarm.addRequest(request, delayMillis);
  }

  public void cancelAllRequests() {
    myAlarm.cancelAllRequests();
  }
}
