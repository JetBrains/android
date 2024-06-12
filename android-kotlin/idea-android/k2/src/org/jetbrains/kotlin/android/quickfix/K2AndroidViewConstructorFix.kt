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

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.SupertypeNotInitialized
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.fixes.AbstractKotlinApplicableQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinDiagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSuperTypeEntry
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class K2AndroidViewConstructorFix(
    element: KtSuperTypeEntry,
    private val useThreeParameterConstructor: Boolean
) : AbstractKotlinApplicableQuickFix<KtSuperTypeEntry>(element) {
    override fun getFamilyName(): String = KotlinAndroidViewConstructorUtils.DESCRIPTION

    // Called from the EDT.
    override fun apply(element: KtSuperTypeEntry, project: Project, editor: Editor?, file: KtFile) {
        KotlinAndroidViewConstructorUtils.applyFix(project, element, useThreeParameterConstructor)
    }

    companion object {
        // Called from a background thread in an open analysis session.
        private fun KtAnalysisSession.createForDiagnostic(diagnostic: SupertypeNotInitialized): K2AndroidViewConstructorFix? {
            val superTypeReference = diagnostic.psi
            val superTypeEntry = superTypeReference.getNonStrictParentOfType<KtSuperTypeEntry>() ?: return null
            val ktClass = superTypeEntry.containingClass() ?: return null
            if (ktClass.primaryConstructor != null) return null

            val superType = superTypeReference.getKtType() as? KtNonErrorClassType ?: return null

            if (!isAndroidView(superType) && superType.getAllSuperTypes().none { isAndroidView(it) }) {
                return null
            }

            val superConstructors = superType.scope?.getConstructors() ?: return null
            val superConstructorClassSignatures = superConstructors.map { constructor ->
                constructor.valueParameters.map { param ->
                    classId(param.returnType)
                }
            }

            if (KotlinAndroidViewConstructorUtils.REQUIRED_CONSTRUCTOR_SIGNATURE !in superConstructorClassSignatures) {
                return null
            }

            // We don't have the ability to analyze in apply(), so capture all of the information that the quickfix
            // needs to actually apply the fix here. In this case, we need to know whether to delegate to the two-
            // or three-argument version of the View constructor. (See the discussion at the definition of
            // [ALLOWED_THREE_PARAMETER_CONSTRUCTOR_DIRECT_SUPERTYPES] for why we do this.)
            val useThreeParameterConstructor =
                ktClass.superTypeListEntries
                    .mapNotNull { it.typeReference?.getKtType() }
                    .any { classId(it) in KotlinAndroidViewConstructorUtils.ALLOWED_THREE_PARAMETER_CONSTRUCTOR_DIRECT_SUPERTYPES }

            return K2AndroidViewConstructorFix(superTypeEntry, useThreeParameterConstructor)
        }

        val FACTORY: KotlinDiagnosticFixFactory<SupertypeNotInitialized> =
            diagnosticFixFactory(SupertypeNotInitialized::class) { listOfNotNull(createForDiagnostic(it)) }

        private fun KtAnalysisSession.classId(type: KtType): ClassId? = type.expandedClassSymbol?.classIdIfNonLocal
        private fun KtAnalysisSession.isAndroidView(type: KtType): Boolean =
            classId(type) == KotlinAndroidViewConstructorUtils.REQUIRED_SUPERTYPE
    }
}

class K2AndroidViewConstructorFixRegistrar : KotlinQuickFixRegistrar() {
    override val list: KotlinQuickFixesList = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerApplicator(K2AndroidViewConstructorFix.FACTORY)
    }
}