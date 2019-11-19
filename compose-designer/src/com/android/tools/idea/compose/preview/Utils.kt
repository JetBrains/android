/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.android.SdkConstants
import com.android.SdkConstants.VALUE_WRAP_CONTENT
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElement
import kotlin.math.max

/** Preview element name */
internal const val PREVIEW_NAME = "Preview"

/** Package containing the preview definitions */
private const val PREVIEW_PACKAGE = "androidx.ui.tooling.preview"

/** Only composables with this annotation will be rendered to the surface */
internal const val PREVIEW_ANNOTATION_FQN = "$PREVIEW_PACKAGE.$PREVIEW_NAME"

internal const val COMPOSABLE_ANNOTATION_FQN = "androidx.compose.Composable"

/** View included in the runtime library that will wrap the @Composable element so it gets rendered by layoutlib */
internal const val COMPOSE_VIEW_ADAPTER = "$PREVIEW_PACKAGE.ComposeViewAdapter"

/** [COMPOSE_VIEW_ADAPTER] view attribute containing the FQN of the @Composable name to call */
private const val COMPOSABLE_NAME_ATTR = "tools:composableName"

/** Action ID of the IDE declared force refresh action (see PlatformActions.xml). This allows us to re-use the shortcut of the declared action. */
private const val FORCE_REFRESH_ACTION_ID = "ForceRefresh"

/** [ShortcutSet] that triggers a build and refreshes the preview */
internal fun getBuildAndRefreshShortcut(): ShortcutSet = KeymapUtil.getActiveKeymapShortcuts(FORCE_REFRESH_ACTION_ID)

const val UNDEFINED_API_LEVEL = -1
const val UNDEFINED_DIMENSION = -1

// Max allowed API
@VisibleForTesting const val MAX_WIDTH = 2000
@VisibleForTesting const val MAX_HEIGHT = 2000

const val WIDTH_PARAMETER = "widthDp"
const val HEIGHT_PARAMETER = "heightDp"

/**
 * Generates the XML string wrapper for one [PreviewElement].
 * @param matchParent when true, the component will take the maximum available space at the parent.
 */
internal fun PreviewElement.toPreviewXmlString(matchParent: Boolean = false) =
  """
    <$COMPOSE_VIEW_ADAPTER
      xmlns:tools="http://schemas.android.com/tools"
      xmlns:aapt="http://schemas.android.com/aapt"
      xmlns:android="http://schemas.android.com/apk/res/android"
      android:layout_width="${dimensionToString(configuration.width,
                                                if (matchParent) SdkConstants.VALUE_MATCH_PARENT else VALUE_WRAP_CONTENT)}"
      android:layout_height="${dimensionToString(configuration.height,
                                                 if (matchParent) SdkConstants.VALUE_MATCH_PARENT else VALUE_WRAP_CONTENT)}"
      $COMPOSABLE_NAME_ATTR="$composableMethodFqn" />
  """.trimIndent()

internal val FAKE_LAYOUT_RES_DIR = LightVirtualFile("layout")

/**
 * A [LightVirtualFile] defined to allow quickly identifying the given file as an XML that is used as adapter
 * to be able to preview composable functions.
 * The contents of the file only reside in memory and contain some XML that will be passed to Layoutlib.
 */
internal class ComposeAdapterLightVirtualFile(name: String, content: String) : LightVirtualFile(name, content) {
  override fun getParent() = FAKE_LAYOUT_RES_DIR
}

/**
 * Transforms a dimension given on the [PreviewConfiguration] into the string value. If the dimension is [UNDEFINED_DIMENSION], the value
 * is converted to `wrap_content`. Otherwise, the value is returned concatenated with `dp`.
 * @param dimension the dimension in dp or [UNDEFINED_DIMENSION]
 * @param defaultValue the value to be used when the given dimension is [UNDEFINED_DIMENSION]
 */
fun dimensionToString(dimension: Int, defaultValue: String = VALUE_WRAP_CONTENT) = if (dimension == UNDEFINED_DIMENSION) {
  defaultValue
}
else {
  "${dimension}dp"
}

/**
 * Truncates the given dimension value to fit between the [min] and [max] values. If the receiver is null,
 * this will return null.
 */
private fun Int?.truncate(min: Int, max: Int): Int? {
  if (this == null) {
    return null
  }

  if (this == UNDEFINED_DIMENSION) {
    return UNDEFINED_DIMENSION
  }

  return minOf(maxOf(this, min), max)
}

/**
 * Contain settings for rendering
 */
data class PreviewConfiguration internal constructor(val apiLevel: Int,
                                                     val theme: String?,
                                                     val width: Int,
                                                     val height: Int,
                                                     val fontScale: Float) {
  fun applyTo(renderConfiguration: Configuration) {
    if (apiLevel != UNDEFINED_API_LEVEL) {
      val highestTarget = renderConfiguration.configurationManager.highestApiTarget!!

      renderConfiguration.target = CompatibilityRenderTarget(highestTarget, apiLevel, null)
    }

    if (theme != null) {
      renderConfiguration.setTheme(theme)
    }

    renderConfiguration.fontScale = max(0f, fontScale)
  }

  companion object {
    /**
     * Cleans the given values and creates a PreviewConfiguration. The cleaning ensures that the user inputted value are within
     * reasonable values before the PreviewConfiguration is created
     */
    @JvmStatic
    fun cleanAndGet(apiLevel: Int?,
                    theme: String?,
                    width: Int?,
                    height: Int?,
                    fontScale: Float?): PreviewConfiguration =
      // We only limit the sizes. We do not limit the API because using an incorrect API level will throw an exception that
      // we will handle and any other error.
      PreviewConfiguration(apiLevel = apiLevel ?: UNDEFINED_API_LEVEL,
                           theme = theme,
                           width = width.truncate(1, MAX_WIDTH) ?: UNDEFINED_DIMENSION,
                           height = height.truncate(1, MAX_HEIGHT) ?: UNDEFINED_DIMENSION,
                           fontScale = fontScale ?: 1f)
  }
}

/**
 * @param displayName display name of this preview element
 * @param composableMethodFqn Fully Qualified Name of the composable method
 * @param previewElementDefinitionPsi [SmartPsiElementPointer] to the preview element definition
 * @param previewBodyPsi [SmartPsiElementPointer] to the preview body. This is the code that will be ran during preview
 * @param configuration the preview element configuration
 */
data class PreviewElement(val displayName: String,
                          val composableMethodFqn: String,
                          val previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>?,
                          val previewBodyPsi: SmartPsiElementPointer<PsiElement>?,
                          val configuration: PreviewConfiguration)

interface PreviewElementFinder {
  /**
   * Returns whether this Preview element finder might apply to the given Kotlin file.
   * The main difference with [findPreviewMethods] is that method might be called on Dumb mode so it must not use any indexes.
   */
  fun hasPreviewMethods(project: Project, vFile: VirtualFile): Boolean

  /**
   * Returns all the [PreviewElement]s present in the passed Kotlin [VirtualFile].
   *
   * This method always runs on smart mode.
   */
  fun findPreviewMethods(project: Project, vFile: VirtualFile): List<PreviewElement> {
    assert(!DumbService.getInstance(project).isDumb) { "findPreviewMethods can not be called on dumb mode" }

    val psiFile = ReadAction.compute<PsiFile?, Throwable> { PsiManager.getInstance(project).findFile(vFile) } ?: return emptyList()
    val uFile: UFile = psiFile.toUElement() as? UFile ?: return emptyList()

    return findPreviewMethods(uFile)
  }

  /**
   * Returns all the [PreviewElement]s present in the passed [UFile].
   *
   * This method always runs on smart mode.
   */
  fun findPreviewMethods(uFile: UFile): List<PreviewElement>
}

internal fun requestBuild(project: Project, module: Module) {
  if (project.isDisposed || module.isDisposed) {
    return
  }

  val modules = mutableSetOf(module)
  ModuleUtil.collectModulesDependsOn(module, modules)
  GradleBuildInvoker.getInstance(project).compileJava(modules.toTypedArray(), TestCompileType.NONE)
}

fun UElement?.toSmartPsiPointer(): SmartPsiElementPointer<PsiElement>? {
  val bodyPsiElement = this?.sourcePsi ?: return null
  return SmartPointerManager.createPointer(bodyPsiElement)
}

/**
 * Extension method that returns if the file is a Kotlin file. This method first checks for the extension to fail fast without having to
 * actually trigger the potentially costly [VirtualFile#fileType] call.
 */
internal fun VirtualFile.isKotlinFileType(): Boolean =
  extension == KotlinFileType.INSTANCE.defaultExtension && fileType == KotlinFileType.INSTANCE