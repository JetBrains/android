// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.rendering.webp;

import com.android.tools.adtui.webp.WebpNativeLibHelper;
import com.android.tools.rendering.security.RenderSecurityManagerOverrides;
import org.jetbrains.annotations.NotNull;

public class WebpRenderSecurityManagerOverrides implements RenderSecurityManagerOverrides {
  @Override
  public boolean allowsPropertiesAccess() {
    return false;
  }

  @Override
  public boolean allowsLibraryLinking(@NotNull String lib) {
    var location = WebpNativeLibHelper.getLibLocation();
    return location != null && lib.equals(location.toString());
  }
}
