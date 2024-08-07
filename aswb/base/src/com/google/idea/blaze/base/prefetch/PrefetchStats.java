/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.prefetch;

import com.google.auto.value.AutoValue;

/** Class encapsulating stats about a prefetch operation. */
@AutoValue
public abstract class PrefetchStats {

  public static final PrefetchStats NONE = create(0L);

  public static PrefetchStats create(long bytesPrefetched) {
    return new AutoValue_PrefetchStats(bytesPrefetched);
  }

  public PrefetchStats combine(PrefetchStats that) {
    return create(this.bytesPrefetched() + that.bytesPrefetched());
  }

  /** Returns the number of bytes downloaded over the network. */
  public abstract long bytesPrefetched();
}
