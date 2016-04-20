/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.sdk.progress;

import com.android.annotations.Nullable;
import com.android.repository.api.DelegatingProgressIndicator;
import com.android.repository.api.ProgressIndicator;
import com.intellij.openapi.diagnostic.FrequentEventDetector;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sometimes {@link FrequentEventDetector} complains about progress getting updated too frequently. This class ensures that
 * the text, secondary text, and completion percent of the wrapped {@link ProgressIndicator} are updated no more than once per
 * {@link #INTERVAL} ms.
 */
public class ThrottledProgressWrapper extends DelegatingProgressIndicator {
  private static final long INTERVAL = 5;
  private String mySecondaryText;
  private String myText;
  private double myFraction;
  private final Timer myTimer = new Timer("ThrottledProgressWrapperTimer");
  private final AtomicReference<TimerTask> myTimerTask = new AtomicReference<>(null);

  public ThrottledProgressWrapper(ProgressIndicator toWrap) {
    super(toWrap);
  }

  @Override
  public void setSecondaryText(@Nullable String s) {
    mySecondaryText = s;
    update();
  }

  @Override
  public void setText(@Nullable String s) {
    myText = s;
    update();
  }

  @Override
  public void setFraction(double v) {
    myFraction = v;
    update();
  }

  private void update() {
    if (myTimerTask.compareAndSet(null, new TimerTask() {
      @Override
      public void run() {
        myTimerTask.set(null);
        ThrottledProgressWrapper.super.setText(myText);
        ThrottledProgressWrapper.super.setSecondaryText(mySecondaryText);
        ThrottledProgressWrapper.super.setFraction(myFraction);
      }
    })) {
      myTimer.schedule(myTimerTask.get(), INTERVAL);
    }
  }
}
