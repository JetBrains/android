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
package com.android.tools.idea.ui.resourcechooser;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.util.ui.EmptyIcon;
import org.junit.Test;

import javax.swing.*;

import java.awt.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AsyncIconTest {
  @Test
  public void testAsyncIcon() throws ExecutionException, InterruptedException, TimeoutException {
    SettableFuture<Icon> futureIcon = SettableFuture.create();
    Icon placeholder = EmptyIcon.create(50, 40);
    AtomicInteger loaded = new AtomicInteger(0);

    AsyncIcon asyncIcon = new AsyncIcon(futureIcon, placeholder, loaded::incrementAndGet);

    assertEquals(0, loaded.get());
    assertEquals(50, asyncIcon.getIconWidth());
    assertEquals(40, asyncIcon.getIconHeight());

    AtomicInteger painted = new AtomicInteger(0);
    Icon mockIcon = new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        painted.incrementAndGet();
      }

      @Override
      public int getIconWidth() {
        return 50;
      }

      @Override
      public int getIconHeight() {
        return 40;
      }
    };

    // This will still paint the placeholder since the Future is still pending
    asyncIcon.paintIcon(null, null, 1, 2);
    assertEquals(0, painted.get());

    futureIcon.set(mockIcon);
    assertEquals(1, loaded.get());

    asyncIcon.paintIcon(null, null, 1, 2);
    assertEquals(1, painted.get());

    // Now check with an icon that it's already loaded (we didn't have to wait for it)
    asyncIcon = new AsyncIcon(
      Futures.immediateFuture(mockIcon),
      placeholder,
      loaded::incrementAndGet);
    assertEquals(2, loaded.get());
    asyncIcon.paintIcon(null, null, 1, 2);
    assertEquals(2, painted.get());
  }
}