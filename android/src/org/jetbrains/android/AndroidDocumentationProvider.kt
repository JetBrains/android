// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android

import com.android.SdkConstants
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceType
import com.android.tools.configurations.Configuration
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.javadoc.AndroidJavaDocRenderer
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.res.psi.ResourceReferencePsiElement.Companion.create
import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.javadoc.JavaDocExternalFilter
import com.intellij.facet.ProjectFacetManager
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.lang.documentation.ExternalDocumentationProvider
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.NonNls
import java.io.BufferedReader
import java.io.IOException
import java.io.Reader

/**
 * Provides documentation for Android R field references eg R.color.colorPrimary in Java and Kotlin files.
 *
 * Despite the fact that AndroidDocumentationProvider is only registered for Java, since the light classes for resources are as Java
 * classes, the documentation provider works for kotlin files.
 */
class AndroidDocumentationProvider : DocumentationProvider, ExternalDocumentationProvider {
  override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
    if (originalElement == null) {
      return null
    }
    val referencePsiElement = create(element) ?: return null
    val module = ModuleUtilCore.findModuleForPsiElement(originalElement) ?: return null
    val resourceReference = referencePsiElement.resourceReference
    val androidFacet = AndroidFacet.getInstance(originalElement)
      ?: return AndroidJavaDocRenderer.render(module, null, resourceReference.resourceUrl)

    // Creating a basic configuration in case rendering of webp or xml drawables.
    val configuration =
      Configuration.create(ConfigurationManager.getOrCreateInstance(androidFacet.module), FolderConfiguration.createDefault())
    return AndroidJavaDocRenderer.render(module, configuration, resourceReference.resourceUrl)
  }

  override fun fetchExternalDocumentation(project: Project, element: PsiElement, docUrls: List<String>, onHover: Boolean): String? {
    // Workaround: When you invoke completion on an android.R.type.name field in a Java class, we
    // never get a chance to provide documentation for it via generateDoc, presumably because the
    // field is recognized by an earlier documentation provider (the generic Java javadoc one?) as
    // something we have documentation for. We do however get a chance to fetch documentation for it;
    // that's this call, so in that case we insert our javadoc rendering into the fetched documentation.
    val doc = ApplicationManager.getApplication().runReadAction(Computable {
      if (isFrameworkFieldDeclaration(element)) {
        // We don't have the original module, so just find one of the Android modules in the project.
        // It's theoretically possible that this will point to a different Android version than the one
        // module used by the original request.
        val module = guessAndroidModule(project, element)
        val field = element as PsiField
        // because isFrameworkFieldDeclaration returned true
        val containingClass = field.containingClass!!
        val type = ResourceType.fromClassName(containingClass.name!!)
        if (module != null && type != null && field.name != null) {
          val name = field.name
          val render = AndroidJavaDocRenderer.render(module, type, name, true)
          val external = JavaDocumentationProvider.fetchExternalJavadoc(element, docUrls, MyDocExternalFilter(project))
          return@Computable AndroidJavaDocRenderer.injectExternalDocumentation(render, external)
        }
      }
      null
    })
    if (doc != null) return null
    return if (isMyContext(element, project)) JavaDocumentationProvider.fetchExternalJavadoc(
      element,
      docUrls,
      MyDocExternalFilter(project)
    ) else null
  }

  override fun hasDocumentationFor(element: PsiElement, originalElement: PsiElement): Boolean {
    return false
  }

  override fun canPromptToConfigureDocumentation(element: PsiElement): Boolean {
    return false
  }

  override fun promptToConfigureDocumentation(element: PsiElement) {}

  @VisibleForTesting
  internal class MyDocExternalFilter(project: Project?) : JavaDocExternalFilter(project) {
    @Throws(IOException::class)
    public override fun doBuildFromStream(url: String, input: Reader, data: StringBuilder) {
      try {
        // Looking up a method, field or constructor? If so we can use the
        // builtin support -- it works.
        if (ourAnchorSuffix.matcher(url).find()) {
          super.doBuildFromStream(url, input, data)
          return
        }
        BufferedReader(input).use { buf ->
          // Pull out the javadoc section.
          // The format has changed over time, so we need to look for different formats.
          // The document begins with a bunch of stuff we don't want to include (e.g.
          // page navigation etc); in all formats this seems to end with the following marker:
          @NonNls val startSection = "<!-- ======== START OF CLASS DATA ======== -->"
          // This doesn't appear anywhere in recent documentation,
          // but presumably was needed at one point; left for now
          // for users who have old documentation installed locally.
          @NonNls val skipHeader = "<!-- END HEADER -->"
          data.append(HTML)
          var read: String?
          do {
            read = buf.readLine()
          } while (read != null && !read.contains(startSection))
          if (read == null) {
            data.delete(0, data.length)
            return
          }
          data.append(read).append("\n")

          // Read until we reach the class overview (if present); copy everything until we see the
          // optional marker skipHeader.
          var skip = false
          while (buf.readLine().also { read = it } != null &&  // Old format: class description follows <h2>Class Overview</h2>
            !read!!.startsWith("<h2>Class Overview") &&  // New format: class description follows just a <br><hr>. These
            // are luckily not present in the older docs.
            read != "<br><hr>") {
            if (read!!.contains("<table class=")) {
              // Skip all tables until the beginning of the class description
              skip = true
            } else if (read!!.startsWith("<h2 class=\"api-section\"")) {
              // Done; we've reached the section after the class description already.
              // Newer docs have no marker section or class attribute marking the
              // beginning of the class doc.
              read = null
              break
            }
            if (!skip && !read!!.isEmpty()) {
              data.append(read).append("\n")
            }
            if (read!!.contains(skipHeader)) {
              skip = true
            }
          }

          // Now copy lines until the next <h2> section.
          // In older versions of the docs format, this was a "<h2>", but in recent
          // revisions (N+) it's <h2 class="api-section">
          if (read != null) {
            data.append("<br><div>\n")
            while (buf.readLine().also { read = it } != null && !read!!.startsWith("<h2>") && !read!!.startsWith("<h2 ")) {
              data.append(read).append("\n")
            }
            data.append("</div>\n")
          }
          data.append(HTML_CLOSE)
        }
      } catch (e: Exception) {
        LOG.error(e.message, e, "URL: $url")
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance("#org.jetbrains.android.AndroidDocumentationProvider")
    private fun guessAndroidModule(project: Project, element: PsiElement): Module? {
      val module = ModuleUtilCore.findModuleForPsiElement(element)
      return module
        ?: project.getAndroidFacets().stream()
          .map { obj: AndroidFacet -> obj.module }
          .findFirst().orElse(null)
    }

    private fun isFrameworkFieldDeclaration(element: PsiElement): Boolean {
      if (element is PsiField) {
        val typeClass = element.containingClass
        if (typeClass != null) {
          val rClass = typeClass.containingClass
          return rClass != null && SdkConstants.CLASS_R == AndroidPsiUtils.getQualifiedNameSafely(rClass)
        }
      }
      return false
    }

    private fun isMyContext(element: PsiElement, project: Project): Boolean {
      return if (element is PsiClass) {
        ApplicationManager.getApplication()
          .runReadAction(Computable {
            val file = element.getContainingFile() ?: return@Computable false
            val vFile = file.virtualFile ?: return@Computable false
            val path = FileUtil.toSystemIndependentName(vFile.path)
            if (StringUtil.toLowerCase(path)
                .contains("/" + SdkConstants.FN_FRAMEWORK_LIBRARY + "!/")
            ) {
              if (!ProjectFacetManager.getInstance(project).getFacets<AndroidFacet>(AndroidFacet.ID).isEmpty()) {
                val jarFile = JarFileSystem.getInstance().getVirtualFileForJar(vFile)
                return@Computable jarFile != null && SdkConstants.FN_FRAMEWORK_LIBRARY == jarFile.name
              }
            }
            false
          } as Computable<Boolean>)
      } else false
    }
  }
}
