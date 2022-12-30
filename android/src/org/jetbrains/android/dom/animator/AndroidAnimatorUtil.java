// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android.dom.animator;

import com.google.common.collect.ImmutableList;

public final class AndroidAnimatorUtil {
  private static final ImmutableList<String> TAG_NAMES = ImmutableList.of("set", "objectAnimator", "animator", "selector");

  public static ImmutableList<String> getPossibleRoots() {
    return TAG_NAMES;
  }
}
