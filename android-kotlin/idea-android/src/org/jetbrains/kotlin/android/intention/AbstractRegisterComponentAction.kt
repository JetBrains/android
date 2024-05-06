/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.android.intention

import com.android.tools.idea.kotlin.insideBody
import com.android.tools.idea.kotlin.isSubclassOf
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import org.jetbrains.android.dom.AndroidAttributeValue
import org.jetbrains.android.dom.manifest.Application
import org.jetbrains.android.dom.manifest.ApplicationComponent
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.android.isSubclassOf
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.psi.psiUtil.isProtected


abstract class AbstractRegisterComponentAction<T : ApplicationComponent>(
    text: String,
    private val componentClassName: String,
) : SelfTargetingIntention<KtClass>(KtClass::class.java, { text }) {

    abstract fun Application.getCurrentComponents(): List<T>
    abstract fun Application.addComponent(): T
    abstract fun T.getComponentClass(): AndroidAttributeValue<PsiClass>

    final override fun isApplicableTo(element: KtClass, caretOffset: Int): Boolean {
        val androidFacet = AndroidFacet.getInstance(element.containingFile) ?: return false
        val manifest = Manifest.getMainManifest(androidFacet) ?: return false
        return !element.isLocal &&
               !element.isAbstract() &&
               !element.isPrivate() &&
               !element.isProtected() &&
               !element.isInner() &&
               !element.name.isNullOrEmpty() &&
               !element.insideBody(caretOffset) &&
               element.isSubclassOfComponentType() &&
               !element.isRegisteredComponent(manifest)
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    private fun KtClass.isSubclassOfComponentType(): Boolean =
        if (KotlinPluginModeProvider.isK2Mode()) {
            allowAnalysisOnEdt {
                @OptIn(KtAllowAnalysisFromWriteAction::class) // TODO(b/310045274)
                allowAnalysisFromWriteAction {
                    analyze(this@isSubclassOfComponentType) {
                        isSubclassOf(
                            this@isSubclassOfComponentType,
                            ClassId.topLevel(FqName(componentClassName)),
                            strict = true
                        )
                    }
                }
            }
        }
        else {
            (descriptor as? ClassDescriptor)?.defaultType?.isSubclassOf(componentClassName, strict = true) ?: false
        }

    private fun KtClass.isRegisteredComponent(manifest: Manifest): Boolean =
        manifest.application.getCurrentComponents().any {
            it.getComponentClass().value?.qualifiedName == fqName?.asString()
        }

    final override fun applyTo(element: KtClass, editor: Editor?) {
        AndroidFacet.getInstance(element.containingFile)?.let(Manifest::getMainManifest)?.let { manifest ->
            val psiClass = element.toLightClass() ?: return
            manifest.application.addComponent().getComponentClass().value = psiClass
        }
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        // Disable intention preview because the fix goes in a separate file.
        return IntentionPreviewInfo.EMPTY
    }
}