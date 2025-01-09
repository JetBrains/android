/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.intellij.openapi.project.Project

abstract class AbstractBlockPropertyWithPreviousDefaultProcessor: AbstractBlockPropertyUnlessNoOpProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  abstract val defaultChangedVersion: AgpVersion

  override fun blockProcessorReasons(): List<BlockReason> {
    if (this.current < defaultChangedVersion && this.new >= propertyRemovedVersion) {
      return listOf(AgpVersionTooOldForPropertyRemoved())
    }
    return super.blockProcessorReasons()
  }

  inner class AgpVersionTooOldForPropertyRemoved: BlockReason("There have been changes in how $featureName is configured.",description = "Please first update AGP to a version greater or equal to $defaultChangedVersion but lower than $propertyRemovedVersion to make the applicable changes")
}