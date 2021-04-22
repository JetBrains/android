// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.rendering.webp;

import com.android.tools.adtui.webp.WebpNativeLibHelper;
import com.android.tools.idea.rendering.RenderSecurityManagerOverrides;
import org.jetbrains.annotations.NotNull;
import java.io.File;

public class WebpRenderSecurityManagerOverrides implements RenderSecurityManagerOverrides {
  @Override
  public boolean allowsPropertiesAccess() {
    return false;
  }

  @Override
  public boolean allowsLibraryLinking(@NotNull String lib) {
    return lib.equals(new File(WebpNativeLibHelper.getLibLocation(), WebpNativeLibHelper.getLibName()).getAbsolutePath());
  }
}
