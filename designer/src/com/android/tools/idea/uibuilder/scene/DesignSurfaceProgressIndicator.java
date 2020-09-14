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
package com.android.tools.idea.uibuilder.scene;

import com.android.annotations.concurrency.GuardedBy;
import com.android.tools.idea.common.surface.DesignSurface;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.util.ui.TimerUtil;
import com.intellij.util.ui.UIUtil;
import javax.swing.Timer;
import org.jetbrains.annotations.NotNull;

/**
 * Progress indicator used to display in {@link DesignSurface} that model update is happening.
 */
public class DesignSurfaceProgressIndicator {
  @GuardedBy("this")
  private AndroidPreviewProgressIndicator myCurrentIndicator;
  private final DesignSurface myDesignSurface;

  public DesignSurfaceProgressIndicator(@NotNull DesignSurface designSurface) {
    myDesignSurface = designSurface;
  }

  public synchronized void start() {
    if (myCurrentIndicator == null) {
      myCurrentIndicator = new AndroidPreviewProgressIndicator();
      myCurrentIndicator.start();
    }
  }

  public synchronized void stop() {
    if (myCurrentIndicator != null) {
      myCurrentIndicator.stop();
      myCurrentIndicator = null;
    }
  }

  private class AndroidPreviewProgressIndicator extends ProgressIndicatorBase {
    private final Object myLock = new Object();

    @Override
    public void start() {
      super.start();
      UIUtil.invokeLaterIfNeeded(() -> {
        final Timer timer = TimerUtil.createNamedTimer("Android rendering progress timer", 0, event -> {
          synchronized (myLock) {
            if (isRunning()) {
              myDesignSurface.registerIndicator(this);
            }
          }
        });
        timer.setRepeats(false);
        timer.start();
      });
    }

    @Override
    public void stop() {
      synchronized (myLock) {
        super.stop();
        ApplicationManager.getApplication().invokeLater(() -> myDesignSurface.unregisterIndicator(this));
      }
    }
  }
}
