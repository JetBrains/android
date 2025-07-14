// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.rendering.classloading

import org.jetbrains.org.objectweb.asm.commons.Remapper

/**
 * [Remapper] that renames all references to [org.jetbrains.compose.ui.tooling.preview.PreviewParameterProvider]
 * with the [androidx.compose.ui.tooling.preview.PreviewParameterProvider].
 * The names used are JVM internal names and thus separated with "/".
 */
internal class PreviewParameterProviderRemapper() : Remapper() {
  override fun map(internalName: String?): String? {
    return internalName?.replaceFirst(MULTIPLATFORM_PREFIX, ANDROIDX_PREFIX)
  }
}

internal fun Remapper.chainWith(remapper: Remapper): Remapper = object : Remapper() {
  override fun map(internalName: String?): String? {
    return remapper.map(this@chainWith.map(internalName))
  }
}

private const val MULTIPLATFORM_PREFIX = "org/jetbrains/compose/ui/tooling/preview/PreviewParameterProvider"
private const val ANDROIDX_PREFIX = "androidx/compose/ui/tooling/preview/PreviewParameterProvider"
