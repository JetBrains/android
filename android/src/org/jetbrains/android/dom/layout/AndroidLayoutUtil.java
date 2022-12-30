// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android.dom.layout;

import static com.android.SdkConstants.CLASS_VIEW;
import static com.android.SdkConstants.TAG_LAYOUT;
import static com.android.SdkConstants.VIEW;
import static com.android.SdkConstants.VIEW_FRAGMENT;
import static com.android.SdkConstants.VIEW_MERGE;
import static com.android.SdkConstants.VIEW_TAG;

import com.android.tools.idea.psi.TagToClassMapper;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public final class AndroidLayoutUtil {
  private AndroidLayoutUtil() {
  }

  @NotNull
  public static List<String> getPossibleRoots(@NotNull AndroidFacet facet) {
    final List<String> result = new ArrayList<>();
    result.add(VIEW_TAG);
    result.add(VIEW_MERGE);
    result.add(VIEW_FRAGMENT);
    result.addAll(AndroidDomUtil.removeUnambiguousNames(TagToClassMapper.getInstance(facet.getModule()).getClassMap(CLASS_VIEW)));
    result.remove(VIEW);
    result.add(TAG_LAYOUT);
    return result;
  }
}
