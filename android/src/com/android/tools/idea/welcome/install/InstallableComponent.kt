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
import com.android.repository.io.FileOp
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.welcome.isWritable
import com.android.tools.idea.welcome.wizard.getSizeLabel
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep
import com.android.tools.idea.wizard.dynamic.ScopedStateStore

private val PROGRESS_LOGGER = StudioLoggerProgressIndicator(InstallableComponent::class.java)

/**
 * Base class for leaf components (the ones that are immediately installed).
 */
abstract class InstallableComponent(
  @JvmField protected val stateStore: ScopedStateStore,
  private val name: String,
  description: String,
  @JvmField protected val installUpdates: Boolean,
  @JvmField protected val fileOp: FileOp
) : ComponentTreeNode(description) {
  @Suppress("LeakingThis") // getting identity hash code of abstract class should be fine
  @JvmField
  protected val key: ScopedStateStore.Key<Boolean> =
    stateStore.createKey("component.enabled." + System.identityHashCode(this), Boolean::class.java)
  private var userSelection: Boolean? = null // null means default component enablement is used
  private var isOptional = true
  private var isInstalled = false
  @JvmField
  protected var sdkHandler: AndroidSdkHandler? = null

  /**
   * Gets the packages that this component would actually install (the required packages that aren't already installed
   * or have an update available, if we're installing updates).
   */
  val packagesToInstall: Collection<UpdatablePackage>
    get() = requiredSdkPackages
      .mapNotNull { repositoryPackages.consolidatedPkgs[it] }
      .filter { p -> p.hasRemote() && (!p.hasLocal() || installUpdates && p.isUpdate) }

  protected val repositoryPackages: RepositoryPackages get() = sdkHandler!!.getSdkManager(PROGRESS_LOGGER).packages

  /**
   * Gets the unfiltered collection of all packages required by this component.
   */
  protected abstract val requiredSdkPackages: Collection<String>

  // TODO: support patches if this is an update
  val downloadSize: Long
    get() = packagesToInstall.mapNotNull { it.remote!!.archive?.complete?.size }.sum()

  protected open fun isSelectedByDefault(): Boolean = true

  protected open fun isOptionalForSdkLocation(): Boolean = true

  override val label: String
    get() {
      val sizeLabel = if (isInstalled) "installed" else getSizeLabel(downloadSize)
      return "$name – ($sizeLabel)"
    }

  abstract fun configure(installContext: InstallContext, sdkHandler: AndroidSdkHandler)

  override val isEnabled: Boolean = isOptional

  override val childrenToInstall: Collection<InstallableComponent>
    get() = if (!stateStore.getNotNull(key, true)) setOf() else setOf(this)

  override fun createSteps(): Collection<DynamicWizardStep> = emptySet()

  override fun updateState(handler: AndroidSdkHandler) {
    // If we don't have anything to install, show as unchecked and not editable.
    sdkHandler = handler
    val nothingToInstall = !isWritable(fileOp, handler.location) || packagesToInstall.isEmpty()
    isOptional = !nothingToInstall && isOptionalForSdkLocation()

    val isSelected: Boolean = when {
      !isOptional -> !nothingToInstall
      userSelection != null -> userSelection!!
      else -> isSelectedByDefault()
    }
    stateStore.put(key, isSelected)
    isInstalled = checkInstalledPackages()
  }

  private fun checkInstalledPackages(): Boolean = sdkHandler != null && packagesToInstall.isEmpty()

  override fun toggle(isSelected: Boolean) {
    if (isOptional) {
      userSelection = isSelected
      stateStore.put(key, isSelected)
    }
  }

  override val immediateChildren: Collection<ComponentTreeNode> get() = emptySet()

  override val isChecked: Boolean get() = stateStore.getNotNull(key, true)

  override fun componentStateChanged(modified: Set<ScopedStateStore.Key<*>>): Boolean = modified.contains(key)
}
