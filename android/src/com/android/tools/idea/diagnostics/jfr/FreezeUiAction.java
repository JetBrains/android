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
package com.android.tools.idea.diagnostics.jfr;

import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

// Causes the UI to freeze for long enough to trigger PerformanceWatcher
public class FreezeUiAction extends AnAction {

  private static int FREEZE_DURATION_MS = RegistryManager.getInstance().intValue("performance.watcher.unresponsive.interval.ms") + 1000;

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ApplicationManager.getApplication().runWriteAction(
      () -> {
        long start = System.currentTimeMillis();
        // do some computation that can't get optimized away
        int[] foo = new int[5];
        int x = 1;
        while (System.currentTimeMillis() < start + FREEZE_DURATION_MS) {
          foo[x] += Math.abs(foo[(x + 1)] + 7 - 3 * foo[1]);
          x = Math.abs(x + foo[x % 5]) % 5;
        }
        System.out.println(foo[2]);
      }
    );
  }
}
