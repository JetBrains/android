/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.rendering.classloading;

import java.util.ArrayList;

public class GapWorker {
  public static final ThreadLocal<GapWorker> sGapWorker = new ThreadLocal<>();
  public ArrayList<Object> mRecyclerViews = new ArrayList<>();

  public void add(Object recyclerView) {
    throw new RuntimeException("add executed");
  }

  public void postFromTraversal(Object recyclerView, int prefetchDx, int prefetchDy) {
    throw new RuntimeException("postFromTraversal executed");
  }

  public void otherMethod() {
    throw new RuntimeException("otherMethod executed");
  }
}
