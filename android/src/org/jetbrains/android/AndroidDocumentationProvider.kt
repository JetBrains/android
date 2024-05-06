// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0
// license that can be found in the LICENSE file.
package org.jetbrains.android

import com.android.SdkConstants
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceType
import com.android.tools.configurations.Configuration
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.javadoc.AndroidJavaDocRenderer
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.intellij.facet.ProjectFacetManager
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.lang.documentation.ExternalDocumentationProvider
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.android.facet.AndroidFacet

private fun guessAndroidModule(project: Project, element: PsiElement) =
  ModuleUtilCore.findModuleForPsiElement(element)
    ?: project.getAndroidFacets().firstOrNull()?.module

private fun PsiElement.asDeclaredFrameworkField(): PsiField? =
  (this as? PsiField)?.takeIf {
    it.containingClass?.containingClass?.let(AndroidPsiUtils::getQualifiedNameSafely) ==
      SdkConstants.CLASS_R
  }

private fun isMyContext(element: PsiElement, project: Project): Boolean {
  if (element !is PsiClass) return false
  return runReadAction {
    val vFile = element.containingFile?.virtualFile ?: return@runReadAction false
    val path = FileUtil.toSystemIndependentName(vFile.path)
    return@runReadAction path
      .lowercase()
      .contains("/${SdkConstants.FN_FRAMEWORK_LIBRARY}!/") &&
      ProjectFacetManager.getInstance(project)
        .getFacets<AndroidFacet>(AndroidFacet.ID)
        .isNotEmpty() &&
      JarFileSystem.getInstance().getVirtualFileForJar(vFile)?.name ==
        SdkConstants.FN_FRAMEWORK_LIBRARY
  }
}

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
          FolderConfiguration.createDefault()
        )
      }
    return AndroidJavaDocRenderer.render(module, configuration, resourceReference.resourceUrl)
  }

  override fun fetchExternalDocumentation(
    project: Project,
    element: PsiElement,
    docUrls: List<String>,
    onHover: Boolean
  ): String? {
    // Workaround: When you invoke completion on an android.R.type.name field in a Java class, we
    // never get a chance to provide documentation for it via generateDoc, presumably because the
    // field is recognized by an earlier documentation provider (the generic Java javadoc one?) as
    // something we have documentation for. We do however get a chance to fetch documentation for
    // it; that's this call, so in that case we insert our javadoc rendering into the fetched
    // documentation.
    val docInjected = runReadAction { injectExternalDoc(project, element, docUrls) }

    if (docInjected || !isMyContext(element, project)) return null
    return JavaDocumentationProvider.fetchExternalJavadoc(
      element,
      docUrls,
      AndroidJavaDocExternalFilter(project)
    )
  }

  @RequiresReadLock
  private fun injectExternalDoc(
    project: Project,
    element: PsiElement,
    docUrls: List<String>,
  ): Boolean {
    val field = element.asDeclaredFrameworkField() ?: return false
    // We don't have the original module, so just find one of the Android modules in the
    // project. It's theoretically possible that this will point to a different Android version
    // than the one module used by the original request.
    val module = guessAndroidModule(project, element) ?: return false
    val containingClassName = requireNotNull(field.containingClass).name
    if (containingClassName == null) {
      thisLogger()
        .error(
          "Got null for name of containing class. Field: %s, containing class: %s",
          field.toString(),
          field.containingClass.toString()
        )
      return false
    }
    val type = ResourceType.fromClassName(containingClassName) ?: return false
    val name = field.name
    val render = AndroidJavaDocRenderer.render(module, type, name, true)
    val external =
      JavaDocumentationProvider.fetchExternalJavadoc(
        element,
        docUrls,
        AndroidJavaDocExternalFilter(project)
      )
    return AndroidJavaDocRenderer.injectExternalDocumentation(render, external) != null
  }

  override fun hasDocumentationFor(element: PsiElement, originalElement: PsiElement) = false

  override fun canPromptToConfigureDocumentation(element: PsiElement) = false

  override fun promptToConfigureDocumentation(element: PsiElement) {}
}
