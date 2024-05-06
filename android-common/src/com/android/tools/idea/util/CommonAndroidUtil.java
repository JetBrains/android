// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public abstract class CommonAndroidUtil {
  /**
   * Key used to store {@link LinkedAndroidModuleGroup} on all modules that are part of the same Gradle project. This key should
   * not be accessed and used directly, instead APIs from ModuleUtil in intellij.android.core should be used.
   */
  public static Key<LinkedAndroidModuleGroup> LINKED_ANDROID_MODULE_GROUP = Key.create("linked.android.module.group");

  public static CommonAndroidUtil getInstance() {
    return ApplicationManager.getApplication().getService(CommonAndroidUtil.class);
  }

  public abstract boolean isAndroidProject(@NotNull Project project);
}
