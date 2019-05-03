/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import com.google.common.collect.Lists;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class AndroidStudioPreferences {
  public static void cleanUpPreferences(@NotNull ExtensionPoint<ConfigurableEP<Configurable>> preferences,
                                        @NotNull List<String> bundlesToRemove) {
    List<ConfigurableEP<Configurable>> nonStudioExtensions = Lists.newArrayList();
    for (ConfigurableEP<Configurable> extension : preferences.getExtensionList()) {
      if (bundlesToRemove.contains(extension.instanceClass)) {
        nonStudioExtensions.add(extension);
      }
    }

    preferences.unregisterExtensions(ep -> !nonStudioExtensions.contains(ep));
  }
}
