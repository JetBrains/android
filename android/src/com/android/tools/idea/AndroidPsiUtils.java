/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.res.ResourceHelper;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.*;

public class AndroidPsiUtils {
  /**
   * Looks up the {@link PsiFile} for a given {@link VirtualFile} in a given {@link Project}, in
   * a safe way (meaning it will acquire a read lock first, and will check that the file is valid
   *
   * @param project the project
   * @param file the file
   * @return the corresponding {@link PsiFile}, or null if not found or valid
   */
  @Nullable
  public static PsiFile getPsiFileSafely(@NotNull final Project project, @NotNull final VirtualFile file) {
    return ApplicationManager.getApplication().runReadAction((Computable<PsiFile>)() -> {
      if (project.isDisposed()) {
        return null;
      }
      return file.isValid() ? PsiManager.getInstance(project).findFile(file) : null;
    });
  }

  /**
   * Looks up the {@link Module} for a given {@link PsiElement}, in a safe way (meaning it will
   * acquire a read lock first.
   *
   * @param element the element
   * @return the module containing the element, or null if not found
   */
  @Nullable
  public static Module getModuleSafely(@NotNull final PsiElement element) {
    return ApplicationManager.getApplication().runReadAction((Computable<Module>)() -> ModuleUtilCore.findModuleForPsiElement(element));
  }

  /**
   * Looks up the {@link Module} containing a given {@link VirtualFile} in a given {@link Project}, in
   * a safe way (meaning it will acquire a read lock first
   *
   * @param project the project
   * @param file the file
   * @return the corresponding {@link Module}, or null if not found
   */
  @Nullable
  public static Module getModuleSafely(@NotNull final Project project, @NotNull final VirtualFile file) {
    return ApplicationManager.getApplication().runReadAction((Computable<Module>)() -> {
      if (project.isDisposed()) {
        return null;
      }
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      return psiFile == null ? null : ModuleUtilCore.findModuleForPsiElement(psiFile);
    });
  }

  /**
   * Returns the root tag for the given {@link XmlFile}, if any, acquiring the read
   * lock to do so if necessary
   *
   * @param file the file to look up the root tag for
   * @return the corresponding root tag, if any
   */
  @Nullable
  public static XmlTag getRootTagSafely(@NotNull final XmlFile file) {
    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      return file.getRootTag();
    }
    return ApplicationManager.getApplication().runReadAction((Computable<XmlTag>)file::getRootTag);
  }

  /**
   * Get the value of an attribute in the {@link XmlFile} safely (meaning it will acquire the read lock first).
   */
  @Nullable
  public static String getRootTagAttributeSafely(@NotNull final XmlFile file,
                                                 @NotNull final String attribute,
                                                 @Nullable final String namespace) {
    Application application = ApplicationManager.getApplication();
    if (!application.isReadAccessAllowed()) {
      return application.runReadAction((Computable<String>)() -> getRootTagAttributeSafely(file, attribute, namespace));
    } else {
      XmlTag tag = file.getRootTag();
      if (tag != null) {
        XmlAttribute attr = namespace != null ? tag.getAttribute(attribute, namespace) : tag.getAttribute(attribute);
        if (attr != null) {
          return attr.getValue();
        }
      }
      return null;
    }
  }

  /**
   * Returns the {@link PsiDirectory} for the given {@link VirtualFile}, with a read lock.
   *
   * @param dir the file to look up the PSI directory for
   * @return the corresponding PSI directory, if any
   */
  @Nullable
  public static PsiDirectory getPsiDirectorySafely(@NotNull final Project project, @NotNull final VirtualFile dir) {
    return ApplicationManager.getApplication().runReadAction((Computable<PsiDirectory>)() -> {
      if (project.isDisposed()) {
        return null;
      }
      return PsiManager.getInstance(project).findDirectory(dir);
    });
  }

  /**
   * Returns the root tag for the given {@link PsiFile}, if any, acquiring the read
   * lock to do so if necessary
   *
   * @param file the file to look up the root tag for
   * @return the corresponding root tag, if any
   */
  @Nullable
  public static String getRootTagName(@NotNull PsiFile file) {
    if (ResourceHelper.getFolderType(file) == ResourceFolderType.XML) {
      if (file instanceof XmlFile) {
        XmlTag rootTag = getRootTagSafely(((XmlFile)file));
        return rootTag == null ? null : rootTag.getName();
      }
    }
    return null;
  }

  /** Type of resource reference: R.type.name or android.R.type.name or neither */
  public enum ResourceReferenceType { NONE, APP, FRAMEWORK }

  /**
   * Returns true if the given PsiElement is a reference to an Android Resource.
   * The element can either be an identifier such as y in R.x.y, or the expression R.x.y itself.
   */
  public static boolean isResourceReference(@NotNull PsiElement element) {
    return getResourceReferenceType(element) != ResourceReferenceType.NONE;
  }

  /**
   * Returns the type of resource reference for the given PSiElement; for R fields and android.R
   * fields it will return {@link ResourceReferenceType#APP} and {@link ResourceReferenceType#FRAMEWORK}
   * respectively, and otherwise it returns {@link ResourceReferenceType#NONE}.
   * <p>
   * The element can either be an identifier such as y in R.x.y, or the expression R.x.y itself.
   */
  @NotNull
  public static ResourceReferenceType getResourceReferenceType(@NotNull PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      return getResourceReferenceType((PsiReferenceExpression)element);
    }

    if (element instanceof PsiIdentifier && element.getParent() instanceof PsiReferenceExpression) {
      return getResourceReferenceType((PsiReferenceExpression)element.getParent());
    }

    return ResourceReferenceType.NONE;
  }

  /**
   * Returns the resource name; e.g. for "R.string.foo" it returns "foo".
   * NOTE: This method should only be called for elements <b>known</b> to be
   * resource references!
   * */
  @NotNull
  public static String getResourceName(@NotNull PsiElement element) {
    assert isResourceReference(element);
    if (element instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExp = (PsiReferenceExpression)element;
      String name = refExp.getReferenceName();
      if (name != null) {
        return name;
      }
    }

    return element.getText();
  }

  @NotNull
  public static ResourceReferenceType getResourceReferenceType(PsiReferenceExpression element) {
    PsiElement resolvedElement = element.resolve();
    if (resolvedElement == null) {
      return ResourceReferenceType.NONE;
    }

    // Examples of valid resources references are my.package.R.string.app_name or my.package.R.color.my_black
    // First parent is the resource type - eg string or color, etc
    PsiElement elementType = resolvedElement.getParent();
    if (!(elementType instanceof PsiClass)) {
      return ResourceReferenceType.NONE;
    }

    // Second parent is the package
    PsiElement elementPackage = elementType.getParent();
    if (!(elementPackage instanceof PsiClass)) {
      return ResourceReferenceType.NONE;
    }

    if (R_CLASS.equals(((PsiClass)elementPackage).getName())) {
      PsiElement elemParent3 = elementPackage.getParent();
      if (elemParent3 instanceof PsiClassOwner &&
          ANDROID_PKG.equals(((PsiClassOwner)elemParent3).getPackageName())) {
        return ResourceReferenceType.FRAMEWORK;
      } else {
        return ResourceReferenceType.APP;
      }
    }

    return ResourceReferenceType.NONE;
  }

  /** Returns the Android {@link ResourceType} given a PSI reference to an Android resource. */
  @Nullable
  public static ResourceType getResourceType(PsiElement resourceRefElement) {
    if (!isResourceReference(resourceRefElement)) {
      return null;
    }

    @SuppressWarnings("ConditionalExpressionWithIdenticalBranches")
    PsiReferenceExpression exp = resourceRefElement instanceof PsiReferenceExpression ?
                                 (PsiReferenceExpression)resourceRefElement :
                                 (PsiReferenceExpression)(resourceRefElement.getParent());

    PsiElement resolvedElement = exp.resolve();
    if (resolvedElement == null) {
      return null;
    }

    PsiElement elemParent = resolvedElement.getParent();
    if (!(elemParent instanceof PsiClass)) {
      return null;
    }

    return ResourceType.getEnum(((PsiClass)elemParent).getName());
  }

  /**
   * Looks up the declared associated context/activity for the given XML file and
   * returns the resolved fully qualified name if found
   *
   * @param module module containing the XML file
   * @param xmlFile the XML file
   * @return the associated fully qualified name, or null
   */
  @Nullable
  public static String getDeclaredContextFqcn(@NotNull Module module, @NotNull XmlFile xmlFile) {
    String context = getRootTagAttributeSafely(xmlFile, ATTR_CONTEXT, TOOLS_URI);
    if (context != null && !context.isEmpty()) {
      boolean startsWithDot = context.charAt(0) == '.';
      if (startsWithDot || context.indexOf('.') == -1) {
        // Prepend application package
        String pkg = MergedManifest.get(module).getPackage();
        return startsWithDot ? pkg + context : pkg + '.' + context;
      }

    }
    return null;
  }

  /**
   * Looks up the declared associated context/activity for the given XML file and
   * returns the associated class, if found
   *
   * @param module module containing the XML file
   * @param xmlFile the XML file
   * @return the associated class, or null
   */
  @Nullable
  public static PsiClass getContextClass(@NotNull Module module, @NotNull XmlFile xmlFile) {
    String fqn = getDeclaredContextFqcn(module, xmlFile);
    if (fqn != null) {
      Project project = module.getProject();
      return JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
    }
    return null;
  }

  /**
   * Returns the {@link PsiClass#getQualifiedName()} and acquires a read lock
   * if necessary
   *
   * @param psiClass the class to look up the qualified name for
   * @return the qualified name, or null
   */
  @Nullable
  public static String getQualifiedNameSafely(@NotNull final PsiClass psiClass) {
    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      return psiClass.getQualifiedName();
    } else {
      return ApplicationManager.getApplication().runReadAction((Computable<String>)psiClass::getQualifiedName);
    }
  }

  /**
   * Locates the given class by fully qualified name visible from the given module
   *
   * @param module    the module scope to search
   * @param className the class to find
   * @return the class, if found
   */
  @Nullable
  public static PsiClass getPsiClass(@NotNull Module module, @NotNull String className) {
    Project project = module.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope scope = module.getModuleWithLibrariesScope();
    return facade.findClass(className, scope);
  }

  /**
   * Returns the value of the given tag's attribute and acquires a read lock if necessary
   *
   * @param tag the tag to look up the attribute for
   * @return the attribute value, or null
   */
  @Nullable
  public static String getAttributeSafely(@NotNull final XmlTag tag, @Nullable final String namespace, @NotNull final String name) {
    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      return tag.getAttributeValue(name, namespace);
    } else {
      return ApplicationManager.getApplication().runReadAction((Computable<String>)() -> tag.getAttributeValue(name, namespace));
    }
  }

  public static boolean isValid(@NotNull final XmlTag tag) {
    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      return tag.isValid();
    }
    else {
      return ApplicationManager.getApplication().runReadAction((Computable<Boolean>)tag::isValid);
    }
  }
}
