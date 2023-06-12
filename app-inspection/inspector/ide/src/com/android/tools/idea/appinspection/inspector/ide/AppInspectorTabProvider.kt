/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspector.ide

import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import javax.swing.Icon

/**
 * Data which is needed to launch an inspector and get a [AppInspectorMessenger] connected to it.
 */
class AppInspectorLaunchConfig(
  /** The ID of the inspector, e.g. "example.inspection" */
  val id: String,
  val params: AppInspectorLaunchParams
)

/** A wrapper around a target inspector jar that either was successfully resolved or not. */
sealed class AppInspectorMessengerTarget {
  class Resolved(val messenger: AppInspectorMessenger) : AppInspectorMessengerTarget()

  /**
   * Represents inspectors that cannot be launched, e.g. the target library used by the app is too
   * old or the user's app was proguarded.
   */
  class Unresolved(val error: String) : AppInspectorMessengerTarget()
}

interface AppInspectorTabProvider : Comparable<AppInspectorTabProvider> {
  companion object {
    @JvmField
    val EP_NAME =
      ExtensionPointName<AppInspectorTabProvider>(
        "com.android.tools.idea.appinspection.inspector.ide.appInspectorTabProvider"
      )
  }

  /**
   * A list of configurations for launching relevant inspectors.
   *
   * The overridden value provided here must contain at least one configuration. See also:
   * [createTab].
   *
   * When the number of configs is one, the App Inspection framework will handle basic errors in the
   * inspector. For example, when an inspector crashes, app inspection will show a toast that
   * prompts the user to restart the tab.
   */
  val launchConfigs: List<AppInspectorLaunchConfig>
  val displayName: String
  val icon: Icon?
    get() = null
  val learnMoreUrl: String?
    get() = null
  fun isApplicable(): Boolean = true

  /**
   * Whether this tab's UI can handle working with disposed inspectors or not.
   *
   * By default, after an inspector is disposed (i.e. the process its inspecting has stopped), its
   * associated tab is closed, as it takes intentional effort to handle this case. After all, trying
   * to interact with a disposed inspector will cause exceptions to get thrown.
   *
   * Children that override this method to return true are explicitly opting into a more complex UI
   * lifecycle (with two states, mutable and immutable, depending on the state of its associated
   * inspector).
   */
  fun supportsOffline(): Boolean = false

  /**
   * Extension point for creating UI that communicates with some target inspector and is shown in
   * the app inspection tool window.
   *
   * @param ideServices Various functions which clients may use to request IDE-specific behaviors
   * @param processDescriptor Information about the process and device that the associated inspector
   *   that will drive this UI is attached to
   * @param messengerTargets A list of inspector messenger targets, one generated per config
   *   specified in [launchConfigs]. Children should check if the target is
   *   [AppInspectorMessengerTarget.Resolved] or, if not, may want to consider showing the wrapped
   *   error to users. Furthermore, resolved messengers can be individually checked for disposal
   *   using [AppInspectorMessenger.awaitForDisposal]. For inspector tabs that host multiple
   *   inspector agents, this can be a useful method to determine which inspector terminated and
   *   show an appropriate error message to user.
   */
  fun createTab(
    project: Project,
    ideServices: AppInspectionIdeServices,
    processDescriptor: ProcessDescriptor,
    messengerTargets: List<AppInspectorMessengerTarget>,
    parentDisposable: Disposable
  ): AppInspectorTab

  override fun compareTo(other: AppInspectorTabProvider): Int =
    this.displayName.compareTo(other.displayName)
}
