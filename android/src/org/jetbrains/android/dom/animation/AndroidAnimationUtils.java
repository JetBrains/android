// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.android.dom.animation;

import com.google.common.collect.ImmutableList;
import org.jetbrains.android.dom.animation.fileDescriptions.InterpolatorDomFileDescription;

public final class AndroidAnimationUtils {
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
