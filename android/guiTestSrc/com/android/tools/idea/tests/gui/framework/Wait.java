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
package com.android.tools.idea.tests.gui.framework;

import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.fest.swing.timing.Timeout;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

/** A fluent API to wait, up to a time limit, for an {@link Objective} to be met. */
public final class Wait {
  private final Timeout myTimeout;

  private Wait(@Nonnull Timeout timeout) {
    myTimeout = timeout;
  }

  /** Sets a time limit. */
  public static Wait minutes(long minutesToWait) {
    return new Wait(Timeout.timeout(minutesToWait, TimeUnit.MINUTES));
  }

  /** Sets a time limit. */
  public static Wait seconds(long secondsToWait) {
    return new Wait(Timeout.timeout(secondsToWait, TimeUnit.SECONDS));
  }

  /** Takes a {@code description} that, if the time limit expires, is appended to "Timed out waiting for ". */
  public WithDescription expecting(@Nonnull String description) {
    return new WithDescription(description);
  }

  public final class WithDescription {
    private final String myDescription;

    private WithDescription(@Nonnull String description) {
      myDescription = description;
    }

    /** Waits until {@code objective} is met or the time limit set by {@link #minutes} or {@link #seconds} expires. */
    public void until(@Nonnull final Objective objective) {
      Condition condition = new Condition(myDescription) {
        @Override
        public boolean test() {
          return objective.isMet();
        }
      };
      Pause.pause(condition, myTimeout);
    }
  }

  public interface Objective {

    /** Returns {@code true} when waiting should end before the time limit expires. */
    boolean isMet();
  }
}
