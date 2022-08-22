/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.heap;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.LeakHunter;
import com.intellij.util.containers.WeakList;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;

public final class HeapStrongReferenceCountAction extends AnAction {

  private static final Logger LOG = Logger.getInstance(HeapStrongReferenceCountAction.class);
  private static final int MAX_DEPTH = 100_000;

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    WeakList<Object> roots = new WeakList<>();
    roots.addAll(LeakHunter.allRoots().get().keySet());
    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(ComponentsSet.getComponentSet());
    StatusCode statusCode = new HeapSnapshotTraverse(stats).walkObjects(MAX_DEPTH, roots);
    if (statusCode != StatusCode.NO_ERROR) {
      LOG.warn("Heap traversing finished with an error: " + statusCode);
    }
    stats.print(new PrintWriter(System.out, true, StandardCharsets.UTF_8));
  }
}
