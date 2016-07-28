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
package org.fest.swing.core;

import org.fest.swing.hierarchy.ComponentHierarchy;
import org.fest.swing.hierarchy.ExistingHierarchy;
import org.fest.swing.lock.ScreenLock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Class to extend and modified FEST BasicRobot
 * The package needs to be org.fest.swing.core since the constructor of BasicRobot is package-protected
 * Modifications:
 * + add waitForIdle at the end of enterText
 */
public class StudioRobot extends BasicRobot {
  public StudioRobot(@Nullable Object screenLockOwner, @Nonnull ComponentHierarchy hierarchy) {
    super(screenLockOwner, hierarchy);
  }

  public static @Nonnull Robot robotWithCurrentAwtHierarchy() {
    return new StudioRobot(acquireScreenLock(), new ExistingHierarchy());
  }

  private static @Nonnull Object acquireScreenLock() {
    Object screenLockOwner = new Object();
    ScreenLock.instance().acquire(screenLockOwner);
    return screenLockOwner;
  }

  @Override
  public void enterText(@Nonnull String text) {
    super.enterText(text);
    waitForIdle(); // Wait for all the key events triggered by enterText to be processed
  }
}
