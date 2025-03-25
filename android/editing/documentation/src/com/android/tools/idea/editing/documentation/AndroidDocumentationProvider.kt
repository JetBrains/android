// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0
// license that can be found in the LICENSE file.
package com.android.tools.idea.editing.documentation

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.lang.documentation.ExternalDocumentationProvider
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import org.jetbrains.android.facet.AndroidFacet

/**
 * Provides documentation for Android R field references eg R.color.colorPrimary in Java and Kotlin
 * files.
 *
 * Despite the fact that AndroidDocumentationProvider is only registered for Java, since the light
 * classes for resources are as Java classes, the documentation provider works for kotlin files.
 */
class AndroidDocumentationProvider : DocumentationProvider, ExternalDocumentationProvider {
  override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
    if (originalElement == null) return null

    val resourceReference =
      ResourceReferencePsiElement.create(element)?.resourceReference ?: return null
    val module = ModuleUtilCore.findModuleForPsiElement(originalElement) ?: return null
    val configuration =
      AndroidFacet.getInstance(originalElement)?.let {
        // Creating a basic configuration in case rendering of webp or xml drawables.
        Configuration.create(
          ConfigurationManager.getOrCreateInstance(it.module),
          FolderConfiguration.createDefault(),
        )
      }
    return AndroidJavaDocRenderer.render(module, configuration, resourceReference.resourceUrl)
  }

  override fun hasDocumentationFor(element: PsiElement, originalElement: PsiElement) = false

  override fun canPromptToConfigureDocumentation(element: PsiElement) = false

  override fun promptToConfigureDocumentation(element: PsiElement) {}
}
