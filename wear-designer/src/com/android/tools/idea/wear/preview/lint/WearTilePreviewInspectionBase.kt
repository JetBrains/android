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
package com.android.tools.idea.wear.preview.lint

import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.isUnitTestFile
import com.android.tools.idea.util.dependsOn
import com.android.tools.idea.wear.preview.WearPreviewBundle.message
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.util.module

/**
 * Base class for Wear Tile Preview inspections. This base class implements [isAvailableForFile] by checking that
 * [StudioFlags.WEAR_TILE_PREVIEW] flag is on, the file's language is either [JavaLanguage] or [KotlinLanguage], that the file is either a
 * unit test file or not depending on [isUnitTestInspection], and, that the file's module depends on
 * [GoogleMavenArtifactId.WEAR_TILES_TOOLING_PREVIEW].
 */
abstract class WearTilePreviewInspectionBase(private val isUnitTestInspection: Boolean = false) : AbstractBaseUastLocalInspectionTool() {
  override fun isAvailableForFile(file: PsiFile): Boolean {
    return StudioFlags.WEAR_TILE_PREVIEW.get() &&
      isUnitTestFile(file.project, file.virtualFile) == isUnitTestInspection &&
      file.language in setOf(KotlinLanguage.INSTANCE, JavaLanguage.INSTANCE) &&
      file.module?.dependsOn(GoogleMavenArtifactId.WEAR_TILES_TOOLING_PREVIEW) == true
  }

  override fun getGroupDisplayName() = message("inspection.group.name")
}
