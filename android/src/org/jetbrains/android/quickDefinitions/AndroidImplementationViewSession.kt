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
package org.jetbrains.android.quickDefinitions

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceUrl
import com.android.tools.idea.res.psi.AndroidResourceToPsiResolver
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.intellij.codeInsight.hint.ImplementationViewElement
import com.intellij.codeInsight.hint.ImplementationViewSession
import com.intellij.codeInsight.hint.ImplementationViewSessionFactory
import com.intellij.codeInsight.hint.PsiImplementationViewElement
import com.intellij.codeInsight.hint.PsiImplementationViewSession
import com.intellij.codeInsight.navigation.ImplementationSearcher
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.util.Processor
import org.jetbrains.android.dom.AndroidXmlDocumentationProvider

/**
 *  [ImplementationViewSessionFactory] for Android resources, for a better "Quick Definition" UI.`
 *
 * Shows a preview of resource declarations for a [ResourceReference] found in the resource repository.
 */
class AndroidImplementationViewSessionFactory : ImplementationViewSessionFactory {
  override fun createSession(
    dataContext: DataContext,
    project: Project,
    isSearchDeep: Boolean,
    alwaysIncludeSelf: Boolean
  ): ImplementationViewSession? {
    // createSession() is called on elements in editor. It is also called the first time you ask for quick definitions on a lookupElement.
    // For this reason, we use PsiImplementationViewSession.getElement to decide which element to use.
    val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return null
    val editor = PsiImplementationViewSession.getEditor(dataContext) ?: return null
    val elementInEditor = CommonDataKeys.PSI_ELEMENT.getData(dataContext)
    val correctedElement = PsiImplementationViewSession.getElement(project, file, editor, elementInEditor)
    val contextElement = file.findElementAt(editor.caretModel.offset) ?: return null
    val resourceReferencePsiElement = getResourceReferencePsiElement(correctedElement, contextElement) ?: return null
    return AndroidImplementationViewSession(resourceReferencePsiElement, contextElement, editor)
  }

  override fun createSessionForLookupElement(
    project: Project,
    editor: Editor?,
    file: VirtualFile?,
    lookupItemObject: Any?,
    isSearchDeep: Boolean,
    alwaysIncludeSelf: Boolean
  ): ImplementationViewSession? {
    // createSessionForLookupElement() is called on the second and subsequent requests for the quick definitions of lookup items.
    if (editor == null || file == null) { return null }
    val contextElement = PsiManager.getInstance(project).findFile(file)?.findElementAt(editor.caretModel.offset) ?: return null
    val resourceReferencePsiElement = getResourceReferencePsiElement(lookupItemObject, contextElement) ?: return null
    return AndroidImplementationViewSession(resourceReferencePsiElement, contextElement, editor)
  }

  private fun getResourceReferencePsiElement(element: Any?, contextElement: PsiElement): ResourceReferencePsiElement? {
    return when (element) {
      is AndroidXmlDocumentationProvider.ResourceReferenceCompletionElement -> {
        val resourceReference = ResourceUrl.parse(element.resource)?.resolve(
          ResourceNamespace.TODO(),
          ResourceNamespace.Resolver.EMPTY_RESOLVER) ?: return null
        ResourceReferencePsiElement(contextElement, resourceReference)
      }
      is PsiElement -> ResourceReferencePsiElement.create(element)
      else -> null
    }
  }
}

/**
 * [ImplementationViewSession] specific to Android resources
 *
 * Implementations for resources are found via the [AndroidResourceToPsiResolver] resource repositories.
 */
class AndroidImplementationViewSession(
  private val resourceReferencePsiElement: ResourceReferencePsiElement,
  private val contextElement: PsiElement,
  override val editor: Editor?
) : ImplementationViewSession {

  // TODO(lukeegan): Create own ImplementationViewElement to show custom text eg. entire Style Tags for styleable attrs.
  override val implementationElements: List<ImplementationViewElement>
    get() {
      return ProgressManager.getInstance().runProcessWithProgressSynchronously(
        ThrowableComputable {
          runReadAction {
            AndroidResourceToPsiResolver.getInstance()
              .getGotoDeclarationTargets(resourceReferencePsiElement.resourceReference, contextElement)
              .map { PsiImplementationViewElement(it) }
          }
        },
        ImplementationSearcher.getSearchingForImplementations(),
        true,
        contextElement.project
      )
    }

    override val file: VirtualFile? = contextElement.containingFile.virtualFile
    override val text: String? = contextElement.text

    override val factory: ImplementationViewSessionFactory
      get() = ImplementationViewSessionFactory.EP_NAME.findExtensionOrFail(AndroidImplementationViewSessionFactory::class.java)

    override val project: Project = resourceReferencePsiElement.project

    override fun searchImplementationsInBackground(indicator: ProgressIndicator,
                                                   processor: Processor<in ImplementationViewElement>): List<ImplementationViewElement> = emptyList()

    override fun elementRequiresIncludeSelf(): Boolean = false

    override fun needUpdateInBackground(): Boolean = false

    override fun dispose() {}
}