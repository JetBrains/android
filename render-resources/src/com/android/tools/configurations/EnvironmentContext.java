/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.configurations;

import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.tools.configurations.ConfigurationListener.CFG_ACTIVITY;
import static com.android.tools.configurations.ConfigurationListener.CFG_LOCALE;
import static com.android.tools.configurations.ConfigurationListener.CFG_NAME;
import static com.android.tools.configurations.ConfigurationListener.CFG_TARGET;
import static com.android.tools.configurations.ConfigurationListener.CFG_THEME;

import com.android.ide.common.resources.Locale;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.res.ResourceUtils;
import com.android.tools.sdk.CompatibilityRenderTarget;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages the environmental and user-selected state of the Configuration
 * (e.g., Target API, Theme, Locale, and Activity).
 */
public class EnvironmentContext {
  private static final String NO_ACTIVITY = new String();

  public interface Context {
    @NotNull ConfigurationSettings getSettings();
    @NotNull FolderConfiguration getEditedConfig();
    @Nullable String calculateActivity();
    @NotNull String getPreferredTheme();
    @Nullable IAndroidTarget getTargetForRendering(@Nullable IAndroidTarget target);
  }

  private final Context myContext;

  @Nullable private IAndroidTarget myTarget;
  @Nullable private String myTheme;
  @Nullable private String myActivity;
  @Nullable private Locale myLocale = null;
  private String myDisplayName;

  public EnvironmentContext(@NotNull Context context) {
    this.myContext = context;
  }

  public void initFromEditedConfig() {
    // if no qualifier is present, matching the original fallback behavior.
    myLocale = Locale.create(myContext.getEditedConfig());
  }

  @Nullable
  public String getActivity() {
    if (myActivity == NO_ACTIVITY) {
      return null;
    }
    if (myActivity == null) {
      // Preserves subclass polymorphism via Context bridge
      myActivity = myContext.calculateActivity();
      if (myActivity == null) {
        myActivity = NO_ACTIVITY;
        return null;
      }
    }
    return myActivity;
  }

  /**
   * Sets the associated activity.
   *
   * @param activity The new activity to set.
   * @return A bitmask containing {@link ConfigurationListener#CFG_ACTIVITY} if the activity changed,
   *         or 0 if it remained the same. Used by the Configuration facade to notify listeners.
   */
  public int setActivity(@Nullable String activity) {
    if (!Objects.equals(myActivity, activity)) {
      myActivity = activity;
      return CFG_ACTIVITY;
    }
    return 0;
  }

  @NotNull
  public Locale getLocale() {
    if (myLocale == null) {
      return myContext.getSettings().getLocale();
    }
    return myLocale;
  }

  /**
   * Sets the locale.
   *
   * @param locale The new locale to apply.
   * @return A bitmask containing {@link ConfigurationListener#CFG_LOCALE} if the locale changed,
   *         or 0 if it remained the same. Used by the Configuration facade to notify listeners.
   */
  public int setLocale(@NotNull Locale locale) {
    if (!Objects.equals(myLocale, locale)) {
      myLocale = locale;
      return CFG_LOCALE;
    }
    return 0;
  }

  @NotNull
  public String getTheme() {
    if (myTheme == null) {
      return myContext.getPreferredTheme();
    }
    return myTheme;
  }

  /**
   * Sets the theme style.
   *
   * @param theme The new theme to apply.
   * @return A bitmask containing {@link ConfigurationListener#CFG_THEME} if the theme changed,
   *         or 0 if it remained the same. Used by the Configuration facade to notify listeners.
   */
  public int setTheme(@Nullable String theme) {
    if (!Objects.equals(myTheme, theme)) {
      myTheme = theme;
      checkThemePrefix();
      return CFG_THEME;
    }
    return 0;
  }

  private void checkThemePrefix() {
    if (myTheme != null && !myTheme.startsWith(PREFIX_RESOURCE_REF)) {
      if (myTheme.isEmpty()) {
        myTheme = myContext.getPreferredTheme();
        return;
      }
      myTheme = ResourceUtils.getStyleResourceUrl(myTheme);
    }
  }

  @Nullable
  public IAndroidTarget getTarget() {
    if (myTarget == null) {
      IAndroidTarget target = myContext.getSettings().getTarget();
      VersionQualifier version = myContext.getEditedConfig().getVersionQualifier();
      if (target != null && version != null && version.getVersion() > target.getVersion().getFeatureLevel()) {
        target = myContext.getSettings().getTarget(version.getVersion());
      }
      return myContext.getTargetForRendering(target);
    }
    return myTarget;
  }

  @Nullable
  public IAndroidTarget getRealTarget() {
    IAndroidTarget target = getTarget();
    if (target instanceof CompatibilityRenderTarget) {
      CompatibilityRenderTarget compatTarget = (CompatibilityRenderTarget)target;
      return compatTarget.getRealTarget();
    }
    return target;
  }

  /**
   * Sets the rendering target.
   *
   * @param target The new rendering target to apply.
   * @return A bitmask containing {@link ConfigurationListener#CFG_TARGET} if the target changed,
   *         or 0 if it remained the same. Used by the Configuration facade to notify listeners.
   */
  public int setTarget(@Nullable IAndroidTarget target) {
    if (myTarget != target) {
      myTarget = myContext.getTargetForRendering(target);
      return CFG_TARGET;
    }
    return 0;
  }

  @Nullable
  public String getDisplayName() {
    return myDisplayName;
  }

  /**
   * Sets the display name to be shown for this configuration.
   *
   * @param displayName The new display name.
   * @return A bitmask containing {@link ConfigurationListener#CFG_NAME} if the display name changed,
   *         or 0 if it remained the same. Used by the Configuration facade to notify listeners.
   */
  public int setDisplayName(@Nullable String displayName) {
    if (!Objects.equals(myDisplayName, displayName)) {
      myDisplayName = displayName;
      return CFG_NAME;
    }
    return 0;
  }

  public void copyFrom(EnvironmentContext other) {
    this.myTarget = other.myTarget;     // Lazy (avoids fetching project state)
    this.myLocale = other.myLocale;     // Lazy (avoids fetching project state)

    // Eagerly evaluated. This explicitly matches the original behavior in
    // Configuration.java, ensuring these calculated values are securely pinned to the clone.
    this.myTheme = other.getTheme();
    this.myActivity = other.getActivity();
    this.myDisplayName = other.getDisplayName();
  }
}
