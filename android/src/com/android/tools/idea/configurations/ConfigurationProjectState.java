/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.configurations;


import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.rendering.Locale;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Per project state for layouts */
@SuppressWarnings("UnusedDeclaration") // Getters called by XML serialization reflection
@Tag("config")
public class ConfigurationProjectState {
  @Nullable private String myLocale;
  @Nullable private String myTarget;

  @Tag("locale")
  @Nullable
  public String getLocale() {
    return myLocale;
  }

  public void setLocale(@Nullable String locale) {
    myLocale = locale;
  }

  @Tag("target")
  @Nullable
  public String getTarget() {
    return myTarget;
  }

  public void setTarget(@Nullable String target) {
    myTarget = target;
  }

  public void saveState(@NotNull Configuration configuration) {
    setLocale(toLocaleString(configuration.getLocale()));
    setTarget(toTargetString(configuration.getTarget()));
  }

  public void loadState(@NotNull Configuration configuration) {
    ConfigurationManager manager = configuration.getConfigurationManager();
    IAndroidTarget target = fromTargetString(manager, myTarget);
    configuration.startBulkEditing();
    if (target != null) {
      configuration.setTarget(target);
    }
    if (myLocale != null) {
      configuration.setLocale(Locale.create(myLocale));
    }
    configuration.finishBulkEditing();
  }

  @Nullable
  static IAndroidTarget fromTargetString(@NotNull ConfigurationManager manager, @Nullable String targetString) {
    if (targetString != null) {
      for (IAndroidTarget target : manager.getTargets()) {
        if (targetString.equals(target.hashString())) {
          return target;
        }
      }
    }

    return null;
  }

  @NotNull
  static Locale fromLocaleString(@Nullable String locale) {
    if (locale == null) {
      return Locale.ANY;
    }
    return Locale.create(locale);
  }

  @Nullable
  static String toLocaleString(@Nullable Locale locale) {
    if (locale == null || locale == Locale.ANY) {
      return null;
    } else {
      if (locale.hasRegion()) {
        return locale.language.getValue() + "-r" + locale.region.getValue();
      } else {
        return locale.language.getValue();
      }
    }
  }

  @Nullable
  static String toTargetString(@Nullable IAndroidTarget target) {
    return target != null ? target.hashString() : null;
  }
}
