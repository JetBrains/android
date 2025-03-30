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

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors.SUPERTYPE_NOT_INITIALIZED
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.quickfix.QuickFixContributor
import org.jetbrains.kotlin.idea.quickfix.QuickFixes
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSuperTypeEntry
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes

class K1AndroidViewConstructorFix(element: KtSuperTypeEntry) : KotlinQuickFixAction<KtSuperTypeEntry>(element) {

    override fun getText() = KotlinAndroidViewConstructorUtils.DESCRIPTION
    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        return AndroidFacet.getInstance(file) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val ktClass = element.containingClass() ?: return

        val bindingContext = ktClass.analyze(BodyResolveMode.PARTIAL)

        val useThreeParameterConstructor = ktClass.superTypeListEntries
          .mapNotNull { bindingContext[BindingContext.TYPE, it.typeReference]?.getClassId() }
          // Check if the super is android.view.View to use the three parameters constructors
          .any { it in KotlinAndroidViewConstructorUtils.ALLOWED_THREE_PARAMETER_CONSTRUCTOR_DIRECT_SUPERTYPES }

        KotlinAndroidViewConstructorUtils.applyFix(project, element, useThreeParameterConstructor)
    }

    companion object Factory : KotlinSingleIntentionActionFactory() {

        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val superTypeEntry = SUPERTYPE_NOT_INITIALIZED.cast(diagnostic).psiElement

            val ktClass = superTypeEntry.containingClass() ?: return null
            if (ktClass.primaryConstructor != null) return null

            val context = superTypeEntry.analyze()
            val type = superTypeEntry.typeReference?.let { context[BindingContext.TYPE, it] } ?: return null

            if (!type.isAndroidView() && type.supertypes().none { it.isAndroidView() }) return null

            val constructorSignatures = type.constructorSignatures() ?: return null
            if (KotlinAndroidViewConstructorUtils.REQUIRED_CONSTRUCTOR_SIGNATURE !in constructorSignatures) return null

            return K1AndroidViewConstructorFix(superTypeEntry)
        }

        private fun KotlinType.getClassId() = constructor.declarationDescriptor.classId

        private fun KotlinType.isAndroidView() = getClassId() == KotlinAndroidViewConstructorUtils.REQUIRED_SUPERTYPE

        private fun KotlinType.constructorSignatures(): List<List<ClassId?>>? {
            val classDescriptor = constructor.declarationDescriptor as? ClassDescriptor ?: return null
            return classDescriptor.constructors.map {
                it.valueParameters.map { parameter -> parameter.type.getClassId() }
            }
        }
    }
}

class K1AndroidViewConstructorFixRegistrar : QuickFixContributor {
    override fun registerQuickFixes(quickFixes: QuickFixes) {
        quickFixes.register(SUPERTYPE_NOT_INITIALIZED, K1AndroidViewConstructorFix.Factory)
    }
}
