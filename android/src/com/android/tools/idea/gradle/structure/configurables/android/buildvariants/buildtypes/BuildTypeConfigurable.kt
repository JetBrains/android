// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.structure.configurables.android.buildvariants.buildtypes

import com.android.tools.idea.gradle.structure.configurables.android.ChildModelConfigurable
import com.android.tools.idea.gradle.structure.configurables.ui.buildvariants.buildtypes.BuildTypeConfigPanel
import com.android.tools.idea.gradle.structure.configurables.ui.properties.listPropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.properties.simplePropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.properties.mapPropertyEditor
import com.android.tools.idea.gradle.structure.model.android.PsBuildType
import com.android.tools.idea.gradle.structure.model.meta.PropertiesUiModel
import com.android.tools.idea.gradle.structure.model.meta.uiProperty
import javax.swing.JComponent

class BuildTypeConfigurable(private val buildType: PsBuildType) : ChildModelConfigurable<PsBuildType>(buildType) {
  override fun getBannerSlogan() = "Build Type '${buildType.name}'"

  override fun createOptionsPanel(): JComponent = BuildTypeConfigPanel(buildType).component
}

fun buildTypePropertiesModel() =
    PropertiesUiModel(
        listOf(
            uiProperty(PsBuildType.BuildTypeDescriptors.debuggable, ::simplePropertyEditor),
// TODO(b/70501607): Decide on PsBuildType.BuildTypeDescriptors.embedMicroApp,
            uiProperty(PsBuildType.BuildTypeDescriptors.jniDebuggable, ::simplePropertyEditor),
            uiProperty(PsBuildType.BuildTypeDescriptors.renderscriptDebuggable, ::simplePropertyEditor),
            uiProperty(PsBuildType.BuildTypeDescriptors.renderscriptOptimLevel, ::simplePropertyEditor),
            uiProperty(PsBuildType.BuildTypeDescriptors.proGuardFiles, listPropertyEditor(::simplePropertyEditor)),
            uiProperty(PsBuildType.BuildTypeDescriptors.manifestPlaceholders, mapPropertyEditor(::simplePropertyEditor)),
            uiProperty(PsBuildType.BuildTypeDescriptors.minifyEnabled, ::simplePropertyEditor),
            uiProperty(PsBuildType.BuildTypeDescriptors.multiDexEnabled, ::simplePropertyEditor),
// TODO(b/70501607): Decide on PsBuildType.BuildTypeDescriptors.pseudoLocalesEnabled,
// TODO(b/70501607): Decide on PsBuildType.BuildTypeDescriptors.testCoverageEnabled,
            uiProperty(PsBuildType.BuildTypeDescriptors.applicationIdSuffix, ::simplePropertyEditor),
            uiProperty(PsBuildType.BuildTypeDescriptors.versionNameSuffix, ::simplePropertyEditor),
            uiProperty(PsBuildType.BuildTypeDescriptors.zipAlignEnabled, ::simplePropertyEditor)
        ))

