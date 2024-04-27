/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.progress;

import com.android.ProgressManagerAdapter;
import com.android.annotations.NonNull;
import com.intellij.ide.ApplicationInitializedListenerJavaShim;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;

/**
 * An adapter for accessing {@link ProgressManager} in code that cannot depend on IntelliJ platform.
 */
public final class StudioProgressManagerAdapter extends ProgressManagerAdapter {
  /**
   * Checks if the progress indicator associated with the current thread has been canceled and, if
   * so, throws a {@link ProcessCanceledException} exception.
   */
  public static void checkCanceled() throws ProcessCanceledException {
    ProgressManagerAdapter.checkCanceled();
  }

  @Override
  protected void doCheckCanceled() throws ProcessCanceledException {
    ProgressManager.checkCanceled();
  }

  @Override
  protected void doThrowIfCancellation(@NonNull Throwable t) {
    if (t instanceof ProcessCanceledException) {
      throw (ProcessCanceledException)t;
    }
    super.doThrowIfCancellation(t);
  }

  private StudioProgressManagerAdapter() {}

  public static class Installer extends ApplicationInitializedListenerJavaShim {
    @Override
    public void componentsInitialized() {
      ProgressManagerAdapter.setInstance(new StudioProgressManagerAdapter());
    }
  }
}
