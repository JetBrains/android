// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class CommonAndroidUtil {

  public static CommonAndroidUtil getInstance() {
    return ApplicationManager.getApplication().getService(CommonAndroidUtil.class);
  }

  public abstract boolean isAndroidProject(@NotNull Project project);
}
