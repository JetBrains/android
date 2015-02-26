/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android;

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.javadoc.AndroidJavaDocRenderer;
import com.intellij.codeInsight.javadoc.JavaDocExternalFilter;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Locale;

import static com.android.SdkConstants.CLASS_R;
import static com.android.tools.idea.AndroidPsiUtils.ResourceReferenceType;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDocumentationProvider implements DocumentationProvider, ExternalDocumentationProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.AndroidDocumentationProvider");

  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return null;
  }

  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    return null;
  }

  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (originalElement == null) {
      return null;
    }
    ResourceReferenceType referenceType = AndroidPsiUtils.getResourceReferenceType(originalElement);
    if (referenceType == ResourceReferenceType.NONE) {
      return null;
    }

    Module module = ModuleUtilCore.findModuleForPsiElement(originalElement);
    if (module == null) {
      return null;
    }

    ResourceType type = AndroidPsiUtils.getResourceType(originalElement);
    if (type == null) {
      return null;
    }

    String name = AndroidPsiUtils.getResourceName(originalElement);
    boolean isFrameworkResource = referenceType == ResourceReferenceType.FRAMEWORK;
    return AndroidJavaDocRenderer.render(module, type, name, isFrameworkResource);
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return null;
  }

  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return null;
  }

  @Override
  public String fetchExternalDocumentation(final Project project, final PsiElement element, final List<String> docUrls) {
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
          ResourceType type = ResourceType.getEnum(containingClass.getName());
          if (module != null && type != null) {
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
    if (module == null) {
      Module[] modules = ModuleManager.getInstance(project).getModules();
      for (Module m : modules) {
        if (AndroidFacet.getInstance(m) != null) {
          module = m;
          break;
        }
      }
      if (module == null) {
        return null;
      }
    }
    return module;
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
      return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          PsiFile file = element.getContainingFile();
          if (file == null) {
            return false;
          }
          VirtualFile vFile = file.getVirtualFile();
          if (vFile == null) {
            return false;
          }
          String path = FileUtil.toSystemIndependentName(vFile.getPath());
          if (path.toLowerCase(Locale.US).contains("/" + SdkConstants.FN_FRAMEWORK_LIBRARY + "!/")) {
            if (ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).size() > 0) {
              VirtualFile jarFile = JarFileSystem.getInstance().getVirtualFileForJar(vFile);
              return jarFile != null && SdkConstants.FN_FRAMEWORK_LIBRARY.equals(jarFile.getName());
            }
          }
          return false;
        }
      });
    }
    return false;
  }

  private static class MyDocExternalFilter extends JavaDocExternalFilter {
    public MyDocExternalFilter(Project project) {
      super(project);
    }

    @Override
    protected void doBuildFromStream(String url, Reader input, StringBuilder data) throws IOException {
      try {
        if (ourAnchorsuffix.matcher(url).find()) {
          super.doBuildFromStream(url, input, data);
          return;
        }
        final BufferedReader buf = new BufferedReader(input);
        try {
          @NonNls String startSection = "<!-- ======== START OF CLASS DATA ======== -->";
          @NonNls String endHeader = "<!-- END HEADER -->";

          data.append(HTML);

          String read;

          do {
            read = buf.readLine();
          }
          while (read != null && !read.toUpperCase(Locale.US).contains(startSection));

          if (read == null) {
            data.delete(0, data.length());
            return;
          }

          data.append(read).append("\n");

          boolean skip = false;
          while (((read = buf.readLine()) != null) && !read.toLowerCase(Locale.US).contains("class overview")) {
            if (!skip && read.length() > 0) {
              data.append(read).append("\n");
            }
            if (read.toUpperCase(Locale.US).contains(endHeader)) {
              skip = true;
            }
          }

          if (read != null) {
            data.append("<br><div>\n");
            while (((read = buf.readLine()) != null) && !read.toUpperCase(Locale.ENGLISH).startsWith("<H2>")) {
              data.append(read).append("\n");
            }
            data.append("</div>\n");
          }
          data.append(HTML_CLOSE);
        }
        finally {
          buf.close();
        }
      }
      catch (Exception e) {
        LOG.error(e.getMessage(), e, "URL: " + url);
      }
    }
  }
}
