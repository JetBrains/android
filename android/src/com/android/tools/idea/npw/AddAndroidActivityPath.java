/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.npw.template.ConfigureTemplateParametersStep;
import com.android.tools.idea.npw.template.RenderTemplateModel;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.templates.TemplateMetadata.ATTR_TARGET_API;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.PATH;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.createKey;

/**
 * Wizard path for adding a new activity.
 *
 * @deprecated Replaced by {@link RenderTemplateModel} and {@link ConfigureTemplateParametersStep}.
 */
public final class AddAndroidActivityPath  {
  public static final Key<Boolean> KEY_IS_LAUNCHER = createKey("is.launcher.activity", PATH, Boolean.class);
  public static final Key<TemplateEntry> KEY_SELECTED_TEMPLATE = createKey("selected.template", PATH, TemplateEntry.class);
  public static final Key<AndroidVersion> KEY_TARGET_API = createKey(ATTR_TARGET_API, PATH, AndroidVersion.class);
  public static final Key<Boolean> KEY_OPEN_EDITORS = createKey("open.editors", WIZARD, Boolean.class);

  public static final String CUSTOMIZE_ACTIVITY_TITLE = "Customize the Activity";

  /**
   * Makes the package name relative to a package prefix.
   *
   * Examples:
   * getRelativePackageName("com.google", "com.google.android") -> "android"
   * getRelativePackageName("com.google.android", "com.google.android") -> ""
   * getRelativePackageName("com.google.android", "not.google.android") -> "not.google.android"
   */
  @NotNull
  static String removeCommonPackagePrefix(@NotNull String packagePrefix, @NotNull String packageName) {
    String relativePackageName = packageName;
    if (packageName.equals(packagePrefix)) {
      relativePackageName = "";
    }
    else if (packageName.length() > packagePrefix.length()
             && packageName.startsWith(packagePrefix)
             && packageName.charAt(packagePrefix.length()) == '.') {
      relativePackageName = relativePackageName.substring(packagePrefix.length() + 1);
    }
    return relativePackageName;
  }
}
