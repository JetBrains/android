// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.structure.dialog

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.navigation.History
import com.intellij.ui.navigation.Place
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.Nls

/**
 * This class is to expose AndroidStudio's project structure dialog as project-scoped configurable in Idea.
 * It's not possible to use [ProjectStructureConfigurable] directly in project settings, because [ProjectStructureConfigurable]
 * is registered as project service, and there is a code that relies on this.
 */
class AndroidProjectStructureConfigurableForIdea(project: Project) : SearchableConfigurable,
                                                                     Place.Navigator, Configurable.NoMargin, Configurable.NoScroll {

  /* We don't use kotlin's delegation because some of the super interfaces contain default methods. Others may start to contain default methods
     at any moment. Kotlin's delegation will not delegate these methods to delegate object, default implementation will be used instead.
   */
  private val delegate: ProjectStructureConfigurable = ProjectStructureConfigurable.getInstance(project)

  override fun getPreferredFocusedComponent() = delegate.preferredFocusedComponent

  override fun setHistory(history: History) = delegate.setHistory(history)

  override fun navigateTo(place: Place?, requestFocus: Boolean) = delegate.navigateTo(place, requestFocus)

  override fun queryPlace(place: Place) = delegate.queryPlace(place)

  override fun getId() = delegate.id

  override fun enableSearch(option: String) = delegate.enableSearch(option)

  @Nls
  override fun getDisplayName() = AndroidBundle.message("configurable.AndroidProjectStructureConfigurableForIdea.display.name")

  override fun getHelpTopic() = delegate.helpTopic

  override fun createComponent() = delegate.createComponent()

  override fun isModified() = delegate.isModified

  @Throws(ConfigurationException::class)
  override fun apply() = delegate.apply()

  override fun reset() = delegate.reset()

  override fun disposeUIResources() = delegate.disposeUIResources()

}