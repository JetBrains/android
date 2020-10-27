/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.dom.animation;

import com.google.common.collect.ImmutableList;
import org.jetbrains.android.dom.animation.fileDescriptions.InterpolatorDomFileDescription;

public class AndroidAnimationUtils {
  private AndroidAnimationUtils() {
  }

  private static final ImmutableList<String> ROOT_TAGS =
    ImmutableList.<String>builder()
      .add("set", "alpha", "scale", "translate", "rotate") // tween animation
      .add("layoutAnimation", "gridLayoutAnimation") // LayoutAnimationController inflation
      .addAll(InterpolatorDomFileDescription.STYLEABLE_BY_TAG.keySet())
      .build();

  public static ImmutableList<String> getPossibleRoots() {
    return ROOT_TAGS;
  }
}
