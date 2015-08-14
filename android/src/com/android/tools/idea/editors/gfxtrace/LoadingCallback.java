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
package com.android.tools.idea.editors.gfxtrace;

import com.google.common.util.concurrent.FutureCallback;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBLoadingPanel;
import org.jetbrains.annotations.Nullable;

public abstract class LoadingCallback<T> implements FutureCallback<T> {
  @Nullable private final Logger myLogger;
  @Nullable private final Done myLoading;

  public LoadingCallback(Logger logger) {
    myLogger = logger;
    myLoading = null;
  }

  public LoadingCallback(Logger logger, JBLoadingPanel loading) {
    myLogger = logger;
    myLoading = new LoadingPanelDone(loading);
  }

  public LoadingCallback(Logger logger, Done loading) {
    myLogger = logger;
    myLoading = loading;
  }

  @Override
  public void onFailure(Throwable t) {
    if (myLogger != null) {
      myLogger.error(t);
    }
    if (myLoading != null) {
      myLoading.stopLoading();
    }
  }

  public interface Done {
    void stopLoading();
  }

  private class LoadingPanelDone implements Done {
    @Nullable private final JBLoadingPanel myLoading;

    private LoadingPanelDone(JBLoadingPanel loading) {
      myLoading = loading;
    }

    @Override
    public void stopLoading() {
      myLoading.stopLoading();
    }
  }
}
