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


import com.android.ide.common.resources.Locale;
import com.android.sdklib.IAndroidTarget;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Per project state for layouts */
@SuppressWarnings("UnusedDeclaration") // Getters called by XML serialization reflection
@Tag("config")
public class ConfigurationProjectState {
  @Nullable private String myLocale;
  @Nullable private String myTarget;
  private boolean myPickTarget = true;
  /** List (in MRU order) of device IDs manually chosen */
  @NotNull private List<String> myDeviceIds = new ArrayList<>();
  /**
   * The last device orientation of non-wear devices.
   * We force set the orientation to wear devices, this makes current selected orientation changed and the last selected orientation
   * is lost. We save the last selected orientation here, and restore it when next time the user switches device to non-wear devices.
   *
   * When this value is null, it means the last selected device is not a wear device.
   */
  @Nullable
  private String myNonWearDeviceLastSelectedStateName;

  /**
   * True, if the last default orientation from the last selected non-wear-device is the default for the device.
   */
  private boolean myNonWearDeviceDefaultStateName;

  @NotNull
  @Property(surroundWithTag = false)
  @XCollection(elementName = "device", valueAttributeName = "id")
  public List<String> getDeviceIds() {
    return myDeviceIds;
  }

  public void setDeviceIds(@NotNull List<String> deviceIds) {
    myDeviceIds = deviceIds;
  }

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

  @Tag("pickBest")
  public boolean isPickTarget() {
    return myPickTarget;
  }

  public void setPickTarget(boolean pickTarget) {
    myPickTarget = pickTarget;
  }

  @Nullable
  public String getNonWearDeviceLastSelectedStateName() {
    return myNonWearDeviceLastSelectedStateName;
  }

  public boolean isNonWearDeviceDefaultStateName() {
    return myNonWearDeviceDefaultStateName;
  }

  public void setNonWearDeviceLastSelectedStateName(@Nullable String nonWearDeviceLastState, boolean nonWearDeviceDefaultState) {
    myNonWearDeviceLastSelectedStateName = nonWearDeviceLastState;
    myNonWearDeviceDefaultStateName = nonWearDeviceDefaultState;
  }

  @Nullable
  static IAndroidTarget fromTargetString(@NotNull ConfigurationManager manager, @Nullable String targetString) {
    if (targetString != null) {
      for (IAndroidTarget target : manager.getTargets()) {
        if (targetString.equals(target.hashString()) && ConfigurationManager.isLayoutLibTarget(target)) {
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
      return locale.qualifier.getFolderSegment();
    }
  }

  @Nullable
  static String toTargetString(@Nullable IAndroidTarget target) {
    return target != null ? target.hashString() : null;
  }
}
