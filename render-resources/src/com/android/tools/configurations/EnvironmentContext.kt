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
package com.android.tools.configurations

import com.android.SdkConstants
import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.sdklib.IAndroidTarget
import com.android.tools.res.getStyleResourceUrl
import com.android.tools.sdk.CompatibilityRenderTarget

/** Manages the environmental and user-selected state of the Configuration (e.g., Target API, Theme, Locale, and Activity). */
class EnvironmentContext(private val context: Context) {
  interface Context {
    val settings: ConfigurationSettings
    val editedConfig: FolderConfiguration

    fun calculateActivity(): String?

    val preferredTheme: String

    fun getTargetForRendering(target: IAndroidTarget?): IAndroidTarget?
  }

  private var _target: IAndroidTarget? = null
  private var _theme: String? = null
  private var _activity: String? = null
  private var _locale: Locale? = null
  var displayName: String? = null
    private set

  fun initFromEditedConfig() {
    _locale = Locale.create(context.editedConfig)
  }

  val activity: String?
    get() {
      if (_activity === NO_ACTIVITY) return null

      if (_activity == null) {
        // Preserves subclass polymorphism via Context bridge
        _activity = context.calculateActivity() ?: NO_ACTIVITY
      }
      return if (_activity === NO_ACTIVITY) null else _activity
    }

  /**
   * Sets the associated activity.
   *
   * @return A bitmask containing [ConfigurationListener.CFG_ACTIVITY] if changed, else 0.
   */
  fun setActivity(activity: String?): Int {
    if (_activity != activity) {
      _activity = activity
      return ConfigurationListener.CFG_ACTIVITY
    }
    return 0
  }

  val locale: Locale
    get() = _locale ?: context.settings.locale

  /**
   * Sets the locale.
   *
   * @return A bitmask containing[ConfigurationListener.CFG_LOCALE] if changed, else 0.
   */
  fun setLocale(locale: Locale): Int {
    if (_locale != locale) {
      _locale = locale
      return ConfigurationListener.CFG_LOCALE
    }
    return 0
  }

  val theme: String
    get() = _theme ?: context.preferredTheme

  /**
   * Sets the theme style.
   *
   * @return A bitmask containing[ConfigurationListener.CFG_THEME] if changed, else 0.
   */
  fun setTheme(theme: String?): Int {
    if (_theme != theme) {
      _theme = theme
      checkThemePrefix()
      return ConfigurationListener.CFG_THEME
    }
    return 0
  }

  private fun checkThemePrefix() {
    val currentTheme = _theme ?: return

    if (!currentTheme.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
      if (currentTheme.isEmpty()) {
        _theme = context.preferredTheme
        return
      }
      _theme = getStyleResourceUrl(currentTheme)
    }
  }

  val target: IAndroidTarget?
    get() {
      if (_target == null) {
        var projectTarget = context.settings.target
        val version = context.editedConfig.versionQualifier

        if (projectTarget != null && version != null && version.version > projectTarget.version.featureLevel) {
          projectTarget = context.settings.getTarget(version.version)
        }
        return context.getTargetForRendering(projectTarget)
      }
      return _target
    }

  val realTarget: IAndroidTarget?
    get() {
      val currentTarget = this.target
      return (currentTarget as? CompatibilityRenderTarget)?.realTarget ?: currentTarget
    }

  /**
   * Sets the rendering target.
   *
   * @return A bitmask containing[ConfigurationListener.CFG_TARGET] if changed, else 0.
   */
  fun setTarget(target: IAndroidTarget?): Int {
    // TODO(b/475475082): This contains a legacy bug from Configuration.java.
    // _target stores a wrapper (CompatibilityRenderTarget) while `target` is raw,
    // so `!==` often evaluates to true incorrectly and spams CFG_TARGET listeners.
    // Left as-is for now to maintain strict bug-for-bug compatibility during the Kotlin
    // conversion, but this should be fixed to compare `this.realTarget !== target` in a follow-up CL.
    if (_target !== target) {
      _target = context.getTargetForRendering(target)
      return ConfigurationListener.CFG_TARGET
    }
    return 0
  }

  /**
   * Sets the display name to be shown for this configuration.
   *
   * @return A bitmask containing[ConfigurationListener.CFG_NAME] if changed, else 0.
   */
  fun setDisplayName(displayName: String?): Int {
    if (this.displayName != displayName) {
      this.displayName = displayName
      return ConfigurationListener.CFG_NAME
    }
    return 0
  }

  fun copyFrom(other: EnvironmentContext) {
    // All properties now copy their direct backing fields.
    // This makes cloning consistent and ensures the `NO_ACTIVITY` cache marker is preserved.
    this._target = other._target
    this._locale = other._locale
    this._theme = other._theme
    this._activity = other._activity
    this.displayName = other.displayName
  }

  companion object {
    private val NO_ACTIVITY = String()
  }
}
