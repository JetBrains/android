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

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.android.ChildModelConfigurable
import com.android.tools.idea.gradle.structure.configurables.ui.PropertiesUiModel
import com.android.tools.idea.gradle.structure.configurables.ui.buildvariants.buildtypes.BuildTypeConfigPanel
import com.android.tools.idea.gradle.structure.configurables.ui.listPropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.mapPropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.simplePropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.uiProperty
import com.android.tools.idea.gradle.structure.model.android.PsBuildType
import com.google.wireless.android.sdk.stats.PSDEvent
import javax.swing.Icon

class BuildTypeConfigurable(
  private val buildType: PsBuildType,
  val context: PsContext
) : ChildModelConfigurable<PsBuildType, BuildTypeConfigPanel>(buildType) {
  override fun getBannerSlogan() = "Build Type '${buildType.name}'"
  override fun getIcon(expanded: Boolean): Icon? = buildType.icon
  override fun createPanel(): BuildTypeConfigPanel = BuildTypeConfigPanel(buildType, context)
}

fun buildTypePropertiesModel(isLibrary: Boolean) =
  PropertiesUiModel(
    listOfNotNull(
      if (!isLibrary) uiProperty(PsBuildType.BuildTypeDescriptors.applicationIdSuffix, ::simplePropertyEditor,
                                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_BUILDTYPES_APPLICATIOND_ID_SUFFIX)
      else null,
      uiProperty(PsBuildType.BuildTypeDescriptors.versionNameSuffix, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_BUILDTYPES_VERSION_NAME_SUFFIX),
      uiProperty(PsBuildType.BuildTypeDescriptors.debuggable, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_BUILDTYPES_DEBUGGABLE),
// TODO(b/70501607): Decide on PsBuildType.BuildTypeDescriptors.embedMicroApp,
      uiProperty(PsBuildType.BuildTypeDescriptors.jniDebuggable, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_BUILDTYPES_JNI_DEBUGGABLE),
      uiProperty(PsBuildType.BuildTypeDescriptors.renderscriptDebuggable, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_BUILDTYPES_RENDERSCRIPT_DEBUGGABLE),
      uiProperty(PsBuildType.BuildTypeDescriptors.renderscriptOptimLevel, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_BUILDTYPES_RENDERSCRIPT_OPTIMIZATION_LEVEL),
      uiProperty(PsBuildType.BuildTypeDescriptors.signingConfig, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_BUILDTYPES_SIGNING_CONFIG),
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      if (isLibrary) uiProperty(PsBuildType.BuildTypeDescriptors.consumerProGuardFiles, ::listPropertyEditor, null) else null,
      uiProperty(PsBuildType.BuildTypeDescriptors.minifyEnabled, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_BUILDTYPES_MINIFY_ENABLED),
      uiProperty(PsBuildType.BuildTypeDescriptors.proGuardFiles, ::listPropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_BUILDTYPES_PROGUARD_FILES),
      uiProperty(PsBuildType.BuildTypeDescriptors.manifestPlaceholders, ::mapPropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_BUILDTYPES_MANIFEST_PLACEHOLDERS),
      uiProperty(PsBuildType.BuildTypeDescriptors.multiDexEnabled, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_BUILDTYPES_MULTI_DEX_ENABLED),
// TODO(b/70501607): Decide on PsBuildType.BuildTypeDescriptors.pseudoLocalesEnabled,
// TODO(b/70501607): Decide on PsBuildType.BuildTypeDescriptors.testCoverageEnabled,
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      uiProperty(PsBuildType.BuildTypeDescriptors.matchingFallbacks, ::listPropertyEditor, null)
    ))

