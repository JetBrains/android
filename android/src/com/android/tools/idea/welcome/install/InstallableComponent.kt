/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.welcome.install

import com.android.repository.api.UpdatablePackage
import com.android.repository.impl.meta.RepositoryPackages
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.welcome.isWritable
import com.android.tools.idea.welcome.wizard.getSizeLabel
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.openapi.diagnostic.thisLogger

private val PROGRESS_LOGGER = StudioLoggerProgressIndicator(InstallableComponent::class.java)

/**
 * Base class for leaf components (the ones that are immediately installed).
 */
abstract class InstallableComponent(
  private val name: String,
  description: String,
  @JvmField protected val installUpdates: Boolean
) : ComponentTreeNode(description) {
  protected var willBeInstalled: BoolProperty = BoolValueProperty(true)
  private var userSelection: Boolean? = null // null means default component enablement is used
  private var isOptional = true
  private var isInstalled = false
  private var isUnavailable = false

  @JvmField
  protected var sdkHandler: AndroidSdkHandler? = null

  /**
   * Gets the packages that this component would actually install (the required packages that aren't already installed
   * or have an update available, if we're installing updates).
   */
  val packagesToInstall: Collection<UpdatablePackage>
    get() = requiredSdkPackages.plus(optionalSdkPackages)
      .mapNotNull { repositoryPackages.consolidatedPkgs[it] }
      .filter { p -> p.hasRemote() && (!p.hasLocal() || installUpdates && p.isUpdate) }

  val unavailablePackages: Collection<String>
    get() {
      val installedPackages = requiredSdkPackages
        .mapNotNull { repositoryPackages.consolidatedPkgs[it]?.local?.path }
      return requiredSdkPackages.minus(packagesToInstall.map { it.path }).minus(installedPackages)
    }

  protected val repositoryPackages: RepositoryPackages get() = sdkHandler!!.getSdkManager(PROGRESS_LOGGER).packages

  /**
   * Gets the unfiltered collection of all packages required by this component.
   */
  protected abstract val requiredSdkPackages: Collection<String>
  protected open val optionalSdkPackages: Collection<String> = listOf()

  // TODO: support patches if this is an update
  val downloadSize: Long
    get() = packagesToInstall.mapNotNull { it.remote!!.archive?.complete?.size }.sum()

  protected open fun isSelectedByDefault(): Boolean = true

  protected open fun isOptionalForSdkLocation(): Boolean = true

  override val label: String
    get() {
      val sizeLabel = when {
        isInstalled -> "installed"
        isUnavailable -> "unavailable"
        else -> getSizeLabel(downloadSize)
      }
      return "$name – ($sizeLabel)"
    }

  abstract fun configure(installContext: InstallContext, sdkHandler: AndroidSdkHandler)

  override val isEnabled: Boolean = isOptional

  override val childrenToInstall: Collection<InstallableComponent>
    get() = if (!willBeInstalled.get()) setOf() else setOf(this)

  override val steps: Collection<ModelWizardStep<*>> = setOf()

  override fun createSteps(): Collection<DynamicWizardStep> = emptySet()

  override fun updateState(handler: AndroidSdkHandler) {
    // If we don't have anything to install, show as unchecked and not editable.
    sdkHandler = handler
    val nothingToInstall = !isWritable(handler.location) || packagesToInstall.isEmpty()
    isOptional = !nothingToInstall && isOptionalForSdkLocation()

    willBeInstalled.set(
      when {
        !isOptional -> !nothingToInstall
        userSelection != null -> userSelection!!
        else -> isSelectedByDefault()
      }
    )
    isInstalled = packagesToInstall.isEmpty() && unavailablePackages.isEmpty()
    isUnavailable = unavailablePackages.isNotEmpty()
    if (isUnavailable) {
      thisLogger().warn("$name depends on the the packages that are not available: ${unavailablePackages.joinToString(", ")}")
    }
  }

  override fun toggle(isSelected: Boolean) {
    if (isOptional) {
      userSelection = isSelected
      willBeInstalled.set(isSelected)
    }
  }

  override val immediateChildren: Collection<ComponentTreeNode> get() = emptySet()

  override val isChecked: Boolean get() = willBeInstalled.get()
}
