/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.common.surface.organization

import com.intellij.icons.AllIcons
import javax.swing.Icon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.jewel.ui.icon.PathIconKey

/**
 * Type of the preview group - for example test preview.
 *
 * @param iconKey icon for this preview
 * @param icon icon for this preview, same as [iconKey], the only difference is that Compose and
 *   Swing needs different format for icons.
 * @param defaultGroupState default [OrganizationGroup.isOpened] state of new created
 *   [OrganizationGroup].
 */
enum class OrganizationGroupType(
  val iconKey: PathIconKey?,
  val icon: Icon?,
  val defaultGroupState: Boolean,
) {
  Default(null, null, true),
  Test(
    PathIconKey("expui/runConfigurations/junit.svg", AllIcons::class.java),
    AllIcons.RunConfigurations.Junit,
    false,
  ),
}

/**
 * Information required for each organization group.
 *
 * @param groupId unique id for this group
 * @param displayName name of the organization
 * @param groupType type of the group
 * @param saveState action to perform on [setOpened] state change
 */
class OrganizationGroup(
  val groupId: String,
  val displayName: String,
  val groupType: OrganizationGroupType = OrganizationGroupType.Default,
  defaultOpenedState: Boolean = true,
  val saveState: (Boolean) -> Unit = { _ -> },
) {

  private val _isOpened = MutableStateFlow(defaultOpenedState)

  /** If group is opened, all previews in this group are visible. */
  val isOpened: StateFlow<Boolean> = _isOpened.asStateFlow()

  /** Set group opened or closed. */
  fun setOpened(isOpened: Boolean) {
    _isOpened.update { isOpened }
    saveState(isOpened)
  }
}
