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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind
import com.intellij.openapi.project.Project

/**
 * Processor that blocks AGP upgrades if android.defaults.buildfeatures.aidl is present in gradle.properties after moving to AGP 9.0
 */
class BlockAidlPropertyPresentRefactoringProcessor: AbstractBlockPropertyUnlessNoOpProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion) : super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor)

  override val featureName = "AIDL"
  override val propertyKey = "android.defaults.buildfeatures.aidl"
  override val propertyRemovedVersion = AgpVersion.parse("9.0.0-alpha01")
  override val necessityInfo = PointNecessity(propertyRemovedVersion)
  override val componentKind = UpgradeAssistantComponentKind.BLOCK_AIDL_PROPERTY_PRESENT
  override val defaultChangedVersion = AgpVersion.parse("8.0.0-alpha04")
  override val noOpValue = false
  override fun getRefactoringId() = "com.android.tools.agp.upgrade.aidlBlockProperty"
}