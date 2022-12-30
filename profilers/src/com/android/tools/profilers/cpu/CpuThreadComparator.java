/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import java.util.Comparator;
import kotlin.comparisons.ComparisonsKt;
import org.jetbrains.annotations.NotNull;

/**
 * Comparator that is used to sort a list of CpuThreadinfo by number of child nodes then by default sort order.
 */
public final class CpuThreadComparator {

  public static Comparator<CpuThreadInfo> BASE =
    ComparisonsKt.compareBy(CpuThreadComparator::relevancyRank,
                            CpuThreadInfo::getName,
                            CpuThreadInfo::getId);

  public static Comparator<CpuThreadInfo> withCaptureInfo(@NotNull CpuCapture capture) {
    return ComparisonsKt.compareBy(CpuThreadComparator::relevancyRank,
                                   // Thread with more children go first, so we negate child count
                                   thread -> - capture.getCaptureNode(thread.getId()).getChildCount(),
                                   CpuThreadInfo::getName,
                                   CpuThreadInfo::getId);
  }

  private static int relevancyRank(CpuThreadInfo thread) {
    return thread.isMainThread() ? 0 :
           thread.isRenderThread() ? 1 :
           thread.isGpuThread() ? 2 :
           4;
  }
}
