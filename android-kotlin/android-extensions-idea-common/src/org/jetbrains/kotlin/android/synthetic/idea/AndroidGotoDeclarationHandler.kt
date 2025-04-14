// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.android.synthetic.idea

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.xml.XmlAttribute
import org.jetbrains.kotlin.android.synthetic.res.AndroidLayoutXmlFileManager
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfoOrNull
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

class AndroidGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        if (sourceElement is LeafPsiElement && sourceElement.parent is KtSimpleNameExpression) {
            val simpleNameExpression = sourceElement.parent as? KtSimpleNameExpression ?: return null
            val layoutManager = getLayoutManager(sourceElement) ?: return null
            val propertyDescriptor = resolvePropertyDescriptor(simpleNameExpression) ?: return null

            val psiElements = layoutManager.propertyToXmlAttributes(propertyDescriptor)
            val valueElements = psiElements.mapNotNull { (it as? XmlAttribute)?.valueElement as? PsiElement }
            if (valueElements.isNotEmpty()) return valueElements.toTypedArray()
        }

        return null
    }

    private fun resolvePropertyDescriptor(simpleNameExpression: KtSimpleNameExpression): PropertyDescriptor? {
        val resolvedCall = simpleNameExpression.resolveToCall()
        return resolvedCall?.resultingDescriptor as? PropertyDescriptor
    }

    private fun getLayoutManager(sourceElement: PsiElement): AndroidLayoutXmlFileManager? {
        val moduleInfo = sourceElement.moduleInfoOrNull?.findAndroidModuleInfo() ?: return null
        return moduleInfo.module.getService(AndroidLayoutXmlFileManager::class.java)
    }

    override fun getActionText(context: DataContext): String? {
        return null
    }
}
