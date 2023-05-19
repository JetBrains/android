// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.navigation

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * This allows to index and then search references in KTS files. getRangeInElement is what is in use from abstract class.
 * In other words it enables findUsages for references in KTS.
 *
 * Groovy on other hand has manipulator for references (GroovyMacroManipulator) on platform level.
 */
class KotlinRefManipulator : AbstractElementManipulator<KtNameReferenceExpression>() {
  override fun handleContentChange(element: KtNameReferenceExpression, range: TextRange, newContent: String): KtNameReferenceExpression? {
    return null
  }
}