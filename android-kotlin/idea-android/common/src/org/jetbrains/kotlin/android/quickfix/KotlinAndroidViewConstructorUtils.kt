/*
 * Copyright (C) 2023 The Android Open Source Project
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
package org.jetbrains.kotlin.android.quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperTypeEntry
import org.jetbrains.kotlin.psi.createPrimaryConstructorIfAbsent
import org.jetbrains.kotlin.psi.psiUtil.containingClass

object KotlinAndroidViewConstructorUtils {
    const val DESCRIPTION = "Add Android View constructors using '@JvmOverloads'"

    val REQUIRED_SUPERTYPE = ClassId.fromString("android/view/View")

    // For supertypes that are not android.view.View, we will default to the two parameter constructor.
    // The reason is avoiding passing 0 as defStyleAttr which causes the component default theme to be
    // removed. For example, a new class with android.widget.Button as a supertype, would cause the custom
    // Button not to have a theme.
    val ALLOWED_THREE_PARAMETER_CONSTRUCTOR_DIRECT_SUPERTYPES = setOf(
      REQUIRED_SUPERTYPE,
      ClassId.fromString("android/view/ViewGroup"),
    )

    val REQUIRED_CONSTRUCTOR_SIGNATURE = listOf(
      ClassId.fromString("android/content/Context"),
      ClassId.fromString("android/util/AttributeSet"),
      StandardClassIds.Int,
    )

    fun applyFix(project: Project, element: KtSuperTypeEntry, useThreeParameterConstructor: Boolean) {
        val psiFactory = KtPsiFactory(project, markGenerated = true)
        val ktClass = element.containingClass() ?: return

        val (constructorSignature, superCallSignature) = if (useThreeParameterConstructor) {
            """(
          context: android.content.Context, attrs: android.util.AttributeSet? = null, defStyleAttr: Int = 0
          )""".trimIndent() to "(context, attrs, defStyleAttr)"
        }
        else {
            """(
          context: android.content.Context, attrs: android.util.AttributeSet? = null
          )""".trimIndent() to "(context, attrs)"
        }
        val newPrimaryConstructor = psiFactory.createPrimaryConstructor(constructorSignature)

        val primaryConstructor = ktClass.createPrimaryConstructorIfAbsent().replaced(newPrimaryConstructor)
        primaryConstructor.valueParameterList?.let { ShortenReferencesFacility.getInstance().shorten(it) }
        primaryConstructor.addAnnotation(JvmStandardClassIds.JVM_OVERLOADS_CLASS_ID)

        element.replace(psiFactory.createSuperTypeCallEntry(element.text + superCallSignature))
    }
}