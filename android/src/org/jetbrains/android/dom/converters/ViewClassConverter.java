// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.android.dom.converters;

import com.intellij.util.ArrayUtilRt;
import org.jetbrains.android.util.AndroidUtils;

/**
 * @author Eugene.Kudelevsky
 */
public class ViewClassConverter extends PackageClassConverter {
  public ViewClassConverter() {
    super(false, ArrayUtilRt.EMPTY_STRING_ARRAY, true, new String[]{AndroidUtils.VIEW_CLASS_NAME});
  }
}