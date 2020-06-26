// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.dom.layout;

import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.AttributeProcessingUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.*;

public final class AndroidLayoutUtil {
  private AndroidLayoutUtil() {
  }

  @NotNull
  public static List<String> getPossibleRoots(@NotNull AndroidFacet facet) {
    final List<String> result = new ArrayList<>();
    result.add(VIEW_TAG);
    result.add(VIEW_MERGE);
    result.add(VIEW_FRAGMENT);
    result.addAll(AndroidDomUtil.removeUnambiguousNames(AttributeProcessingUtil.getViewClassMap(facet)));
    result.remove(VIEW);
    result.add(TAG_LAYOUT);
    return result;
  }
}
