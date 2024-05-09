/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.android.inspection

import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.ALL_VALUE_RESOURCE_TYPES
import com.android.tools.idea.res.getReferredResourceOrManifestField
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.openapi.module.ModuleUtil
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.inspections.CreateFileResourceQuickFix
import org.jetbrains.android.inspections.CreateValueResourceQuickFix
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

class KotlinAndroidResourceQuickFixProvider : UnresolvedReferenceQuickFixProvider<KtSimpleNameReference>() {

    override fun registerFixes(ref: KtSimpleNameReference, registrar: QuickFixActionRegistrar) {
        buildKotlinAndroidResourceQuickFixActions(ref.expression, registrar::register)
    }

    override fun getReferenceClass() = KtSimpleNameReference::class.java
}

class KotlinAndroidResourceQuickFixRegistrarK2 : KotlinQuickFixRegistrar() {
    override val list: KotlinQuickFixesList = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(kotlinAndroidResourceQuickFixFactory)
    }
}

private val kotlinAndroidResourceQuickFixFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.UnresolvedReference ->
    val ref = diagnostic.psi as? KtSimpleNameExpression ?: return@IntentionBased emptyList<IntentionAction>()
    buildList { buildKotlinAndroidResourceQuickFixActions(ref, ::add) }
}

private fun buildKotlinAndroidResourceQuickFixActions(expression: KtSimpleNameExpression,
                                                      consumeCreatedQuickFix: (IntentionAction) -> Unit) {
    val contextModule = ModuleUtil.findModuleForPsiElement(expression) ?: return
    val facet = AndroidFacet.getInstance(contextModule) ?: return
    facet.getModuleSystem().getPackageName() ?: return
    val contextFile = expression.containingFile ?: return

    val info = getReferredResourceOrManifestField(facet, expression, null, true)
    if (info == null || info.isFromManifest) return

    val resourceType = ResourceType.fromClassName(info.className) ?: return

    if (ALL_VALUE_RESOURCE_TYPES.contains(resourceType)) {
        consumeCreatedQuickFix(CreateValueResourceQuickFix(facet, resourceType, info.fieldName, contextFile, true))
    }

    val resourceFolderType = FolderTypeRelationship.getNonValuesRelatedFolder(resourceType)
    if (resourceFolderType != null) {
        consumeCreatedQuickFix(CreateFileResourceQuickFix(facet, resourceFolderType, info.fieldName, contextFile, true))
    }
}