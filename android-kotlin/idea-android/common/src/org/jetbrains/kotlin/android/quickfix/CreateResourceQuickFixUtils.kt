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

import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.ALL_VALUE_RESOURCE_TYPES
import com.android.tools.idea.res.getReferredResourceOrManifestField
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.module.ModuleUtil
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.inspections.CreateFileResourceQuickFix
import org.jetbrains.android.inspections.CreateValueResourceQuickFix
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

fun getCreateResourceQuickFixActions(expression: KtSimpleNameExpression) : List<IntentionAction> {
    val contextModule = ModuleUtil.findModuleForPsiElement(expression) ?: return emptyList()
    val facet = AndroidFacet.getInstance(contextModule) ?: return emptyList()
    // The module containing the code must have a manifest with a defined package name to be
    // eligible to contain resources. If we're in a module without a manifest (or one with a
    // blank package name), take no action.
    facet.getModuleSystem().getPackageName() ?: return emptyList()
    val contextFile = expression.containingFile ?: return emptyList()

    val info = getReferredResourceOrManifestField(facet, expression, null, true)
    if (info == null || info.isFromManifest) return emptyList()

    val resourceType = ResourceType.fromClassName(info.className) ?: return emptyList()
    val facetForQuickFix = info.resolvedModule?.androidFacet ?: facet

    return buildList {
        if (resourceType in ALL_VALUE_RESOURCE_TYPES) {
            add(CreateValueResourceQuickFix(facetForQuickFix, resourceType, info.fieldName, contextFile))
        }

        FolderTypeRelationship.getNonValuesRelatedFolder(resourceType)?.let {
            add(CreateFileResourceQuickFix(facetForQuickFix, it, info.fieldName, contextFile, true))
        }
    }
}