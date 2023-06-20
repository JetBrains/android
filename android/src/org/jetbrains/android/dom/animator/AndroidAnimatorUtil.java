package org.jetbrains.android.dom.animator;

import com.google.common.collect.ImmutableList;

public class AndroidAnimatorUtil {
  private static final ImmutableList<String> TAG_NAMES = ImmutableList.of("set", "objectAnimator", "animator", "selector");

  public static ImmutableList<String> getPossibleRoots() {
    return TAG_NAMES;
  }
}
