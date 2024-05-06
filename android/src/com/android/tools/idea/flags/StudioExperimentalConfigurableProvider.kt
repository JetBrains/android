// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.flags

import com.android.tools.idea.IdeInfo
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project

// In Android Studio, "Experimental" is a top-level configurable
class StudioExperimentalConfigurableProvider(private val myProject: Project) : ConfigurableProvider() {

  override fun createConfigurable(): Configurable? {
    return ExperimentalSettingsConfigurable(myProject, true)
  }

  override fun canCreateConfigurable() = IdeInfo.getInstance().isAndroidStudio
}

// In Android plugin, "Experimental" is shown under "Languages and Frameworks"
class PluginExperimentalConfigurableProvider(private val myProject: Project) : ConfigurableProvider() {

  override fun createConfigurable(): Configurable? {
    return ExperimentalSettingsConfigurable(myProject, false)
  }

  override fun canCreateConfigurable() = !IdeInfo.getInstance().isAndroidStudio
}
