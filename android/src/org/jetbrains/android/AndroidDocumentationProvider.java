// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android;

import static com.android.SdkConstants.CLASS_R;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.javadoc.AndroidJavaDocRenderer;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.res.psi.ResourceReferencePsiElement;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.codeInsight.javadoc.JavaDocExternalFilter;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides documentation for Android R field references eg R.color.colorPrimary in Java and Kotlin files.
 *
 * Despite the fact that AndroidDocumentationProvider is only registered for Java, since the light classes for resources are as Java
 * classes, the documentation provider works for kotlin files.
 */
public class AndroidDocumentationProvider implements DocumentationProvider, ExternalDocumentationProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.AndroidDocumentationProvider");

  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (originalElement == null) {
      return null;
    }
    ResourceReferencePsiElement referencePsiElement = ResourceReferencePsiElement.create(element);
    if (referencePsiElement == null) {
      return null;
    }

    Module module = ModuleUtilCore.findModuleForPsiElement(originalElement);
    if (module == null) {
      return null;
    }

    ResourceReference resourceReference = referencePsiElement.getResourceReference();
    AndroidFacet androidFacet = AndroidFacet.getInstance(originalElement);
    if (androidFacet == null) {
      return AndroidJavaDocRenderer.render(module, null, resourceReference.getResourceUrl());
    }

    // Creating a basic configuration in case rendering of webp or xml drawables.
    Configuration configuration =
      Configuration.create(ConfigurationManager.getOrCreateInstance(androidFacet.getModule()), null, FolderConfiguration.createDefault());
    return AndroidJavaDocRenderer.render(module, configuration, resourceReference.getResourceUrl());
  }

  @Override
  public String fetchExternalDocumentation(final Project project, final PsiElement element, final List<String> docUrls, boolean onHover) {
    // Workaround: When you invoke completion on an android.R.type.name field in a Java class, we
    // never get a chance to provide documentation for it via generateDoc, presumably because the
    // field is recognized by an earlier documentation provider (the generic Java javadoc one?) as
    // something we have documentation for. We do however get a chance to fetch documentation for it;
    // that's this call, so in that case we insert our javadoc rendering into the fetched documentation.
    String doc = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        if (isFrameworkFieldDeclaration(element)) {
          // We don't have the original module, so just find one of the Android modules in the project.
          // It's theoretically possible that this will point to a different Android version than the one
          // module used by the original request.
          Module module = guessAndroidModule(project, element);
          PsiField field = (PsiField)element;
          PsiClass containingClass = field.getContainingClass();
          assert containingClass != null; // because isFrameworkFieldDeclaration returned true
          ResourceType type = ResourceType.fromClassName(containingClass.getName());
          if (module != null && type != null && field.getName() != null) {
            String name = field.getName();
            String render = AndroidJavaDocRenderer.render(module, type, name, true);
            String external = JavaDocumentationProvider.fetchExternalJavadoc(element, docUrls, new MyDocExternalFilter(project));
            return AndroidJavaDocRenderer.injectExternalDocumentation(render, external);
          }
        }
        return null;
      }
    });
    if (doc != null) return null;


    return isMyContext(element, project) ?
           JavaDocumentationProvider.fetchExternalJavadoc(element, docUrls, new MyDocExternalFilter(project)) :
           null;
  }

  @Nullable
  private static Module guessAndroidModule(Project project, PsiElement element) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module != null) {
      return module;
    }
    return ProjectSystemUtil.getAndroidFacets(project).stream()
      .map(AndroidFacet::getModule)
      .findFirst().orElse(null);
  }

  private static boolean isFrameworkFieldDeclaration(PsiElement element) {
    if (element instanceof PsiField) {
      PsiField field = (PsiField) element;
      PsiClass typeClass = field.getContainingClass();
      if (typeClass != null) {
        PsiClass rClass = typeClass.getContainingClass();
        return rClass != null && CLASS_R.equals(AndroidPsiUtils.getQualifiedNameSafely(rClass));
      }
    }
    return false;
  }

  @Override
  public boolean hasDocumentationFor(PsiElement element, PsiElement originalElement) {
    return false;
  }

  @Override
  public boolean canPromptToConfigureDocumentation(PsiElement element) {
    return false;
  }

  @Override
  public void promptToConfigureDocumentation(PsiElement element) {
  }

  private static boolean isMyContext(@NotNull final PsiElement element, @NotNull final Project project) {
    if (element instanceof PsiClass) {
      return ApplicationManager.getApplication().runReadAction((Computable<Boolean>)() -> {
        PsiFile file = element.getContainingFile();
        if (file == null) {
          return false;
        }
        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null) {
          return false;
        }
        String path = FileUtil.toSystemIndependentName(vFile.getPath());
        if (StringUtil.toLowerCase(path).contains("/" + SdkConstants.FN_FRAMEWORK_LIBRARY + "!/")) {
          if (!ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).isEmpty()) {
            VirtualFile jarFile = JarFileSystem.getInstance().getVirtualFileForJar(vFile);
            return jarFile != null && SdkConstants.FN_FRAMEWORK_LIBRARY.equals(jarFile.getName());
          }
        }
        return false;
      });
    }
    return false;
  }

  @VisibleForTesting
  static class MyDocExternalFilter extends JavaDocExternalFilter {
    public MyDocExternalFilter(Project project) {
      super(project);
    }

    @Override
    protected void doBuildFromStream(String url, Reader input, StringBuilder data) throws IOException {
      try {
        // Looking up a method, field or constructor? If so we can use the
        // builtin support -- it works.
        if (ourAnchorSuffix.matcher(url).find()) {
          super.doBuildFromStream(url, input, data);
          return;
        }

        // For classes however we'll need to do our own filtering; the Android SDK
        // docs are quite different from the JDK docs so IntelliJ's built in javadoc
        // support doesn't work.

        try (BufferedReader buf = new BufferedReader(input)) {
          // Pull out the javadoc section.
          // The format has changed over time, so we need to look for different formats.
          // The document begins with a bunch of stuff we don't want to include (e.g.
          // page navigation etc); in all formats this seems to end with the following marker:
          @NonNls String startSection = "<!-- ======== START OF CLASS DATA ======== -->";
          // This doesn't appear anywhere in recent documentation,
          // but presumably was needed at one point; left for now
          // for users who have old documentation installed locally.
          @NonNls String skipHeader = "<!-- END HEADER -->";

          data.append(HTML);

          String read;

          do {
            read = buf.readLine();
          }
          while (read != null && !read.contains(startSection));

          if (read == null) {
            data.delete(0, data.length());
            return;
          }

          data.append(read).append("\n");

          // Read until we reach the class overview (if present); copy everything until we see the
          // optional marker skipHeader.
          boolean skip = false;
          while (((read = buf.readLine()) != null) &&
                 // Old format: class description follows <h2>Class Overview</h2>
                 !read.startsWith("<h2>Class Overview") &&
                 // New format: class description follows just a <br><hr>. These
                 // are luckily not present in the older docs.
                 !read.equals("<br><hr>")) {
            if (read.contains("<table class=")) {
              // Skip all tables until the beginning of the class description
              skip = true;
            }
            else if (read.startsWith("<h2 class=\"api-section\"")) {
              // Done; we've reached the section after the class description already.
              // Newer docs have no marker section or class attribute marking the
              // beginning of the class doc.
              read = null;
              break;
            }

            if (!skip && !read.isEmpty()) {
              data.append(read).append("\n");
            }
            if (read.contains(skipHeader)) {
              skip = true;
            }
          }

          // Now copy lines until the next <h2> section.
          // In older versions of the docs format, this was a "<h2>", but in recent
          // revisions (N+) it's <h2 class="api-section">
          if (read != null) {
            data.append("<br><div>\n");
            while (((read = buf.readLine()) != null) && (!read.startsWith("<h2>") && !read.startsWith("<h2 "))) {
              data.append(read).append("\n");
            }
            data.append("</div>\n");
          }
          data.append(HTML_CLOSE);
        }
      }
      catch (Exception e) {
        LOG.error(e.getMessage(), e, "URL: " + url);
      }
    }
  }
}
