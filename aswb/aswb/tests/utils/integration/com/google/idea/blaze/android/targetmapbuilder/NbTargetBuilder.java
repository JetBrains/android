/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.idea.blaze.android.targetmapbuilder;

import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;

/** Top-level interface for targetmapbuilder utility classes. */
public interface NbTargetBuilder {
  /**
   * Each implementing class provides convenience methods for configuring a particular type of
   * {@link TargetIdeInfo}. This method returns the final result.
   *
   * @return the {@link TargetIdeInfo} configured by this utility
   */
  TargetIdeInfo build();

  /**
   * Given an array of NbTargetBuilders, this method combines the {@link TargetIdeInfo} instances
   * they have configured into a single {@link TargetMap}.
   */
  static TargetMap targetMap(NbTargetBuilder... builders) {
    TargetMapBuilder targetMapBuilder = TargetMapBuilder.builder();
    for (NbTargetBuilder builder : builders) {
      targetMapBuilder.addTarget(builder.build());
    }
    return targetMapBuilder.build();
  }
}
