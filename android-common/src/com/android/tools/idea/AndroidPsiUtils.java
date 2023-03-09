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

package com.android.tools.idea;

import static com.android.SdkConstants.ANDROID_PKG;
import static com.android.SdkConstants.R_CLASS;
import static com.intellij.psi.util.PsiTreeUtil.getParentOfType;
import static kotlin.sequences.SequencesKt.generateSequence;

import com.android.resources.ResourceType;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import kotlin.sequences.Sequence;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementKt;
import org.jetbrains.uast.UastContext;
import org.jetbrains.uast.UastUtils;

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
   * Looks up the {@link PsiFile} for a given {@link SmartPsiElementPointer}, in a safe way (meaning it will acquire a read lock first,
   * and will check that the file is valid
   * @param psiFilePointer smart pointer to the {@link PsiFile}
   * @return the corresponding {@link PsiFile}, or null if not found or valid
   */
  @Nullable
  public static PsiFile getPsiFileSafely(@NotNull SmartPsiElementPointer<PsiFile> psiFilePointer) {
    PsiFile psiFile = ReadAction.compute(() -> psiFilePointer.getElement());
    if (psiFile == null || !psiFile.isValid()) {
      return null;
    }
    return psiFile;
  }


  /**
   * Looks up the {@link PsiFile} for a given {@link Document} in a given {@link Project}, in
   * a safe way (meaning it will acquire a read lock first, and will check that the file is valid
   *
   * @param project the project
   * @param document the document
   * @return the corresponding {@link PsiFile}, or null if not found or valid
   */
  @Nullable
  public static PsiFile getPsiFileSafely(@NotNull final Project project, @NotNull final Document document) {
    return ApplicationManager.getApplication().runReadAction((Computable<PsiFile>)() -> {
      if (project.isDisposed()) {
        return null;
      }
      return PsiDocumentManager.getInstance(project).getPsiFile(document);
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
    if (file.getProject().isDisposed()) {
      return null;
    }
    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      return file.getRootTag();
    }
    return ApplicationManager.getApplication().runReadAction((Computable<XmlTag>)file::getRootTag);
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
   * Returns the {@link PsiDirectory} for the given {@link PsiFile}, with a read lock.
   *
   * @param file the file to look up the PSI directory for
   */
  @Nullable
  public static PsiDirectory getPsiDirectorySafely(@NotNull final PsiFile file) {
    return ApplicationManager.getApplication().runReadAction((Computable<PsiDirectory>)file::getParent);
  }

  /**
   * Returns the parent element of the given {@link PsiElement} acquiring the read lock to do so
   * if necessary.
   */
  @Nullable
  public static PsiElement getPsiParentSafely(@NotNull PsiElement element) {
    return ApplicationManager.getApplication().runReadAction((Computable<PsiElement>)element::getParent);
  }

  /**
   * This is similar to {@link com.intellij.psi.util.PsiTreeUtil#getParentOfType(PsiElement, Class, boolean)} with the addition
   * that the method uses the UAST tree (if available) as a fallback mechanism. This is useful if {@code element} originates
   * from a Kotlin {@link PsiFile}.
   */
  @Nullable
  @Contract("null, _, _ -> null")
  public static <T extends PsiElement> T getPsiParentOfType(@Nullable PsiElement element,
                                                            @NotNull Class<T> parentClass,
                                                            @SuppressWarnings("SameParameterValue") boolean strict) {
    if (element == null) {
      return null;
    }

    T parentElement = getParentOfType(element, parentClass, strict);
    if (parentElement != null) {
      return parentElement;
    }

    // UElement heritage tree is not necessarily a subset of the corresponding PsiElement tree
    // e.g. if PsiIdentifier has PsiMethod as parent, converting it to UElement gives us a UIdentifier with null parent
    for (PsiElement psiElement = element; psiElement != null; psiElement = psiElement.getParent()) {
      UElement uElement =
        element.getProject().getService(UastContext.class).convertElementWithParent(psiElement, UElement.class);
      if (uElement != null) {
        T parentPsiElement = getPsiParentOfType(uElement, parentClass, strict && (psiElement == element));
        if (parentPsiElement != null) {
          return parentPsiElement;
        }
      }
    }
    return null;
  }


  /**
   * Returns a lazy {@link Sequence} of parent elements of the given type, using the UAST tree as a fallback mechanism.
   *
   * <p>This is similar to {@link com.intellij.psi.util.PsiTreeUtilKt#parentsOfType(PsiElement, Class)} but uses
   * {@link #getPsiParentOfType(PsiElement, Class, boolean)} from this class, which means it uses UAST where necessary.
   *
   * @see #getPsiParentOfType(PsiElement, Class, boolean)
   * @see com.intellij.psi.util.PsiTreeUtilKt#parentsOfType(com.intellij.psi.PsiElement, java.lang.Class<? extends T>)
   */
  @NotNull
  public static <T extends PsiElement> Sequence<T> getPsiParentsOfType(@NotNull PsiElement element,
                                                                       @NotNull Class<T> parentClass,
                                                                       boolean strict) {
    return generateSequence(getPsiParentOfType(element, parentClass, strict),
                            e -> getPsiParentOfType(e, parentClass, true));
  }

  /**
   * This is similar to {@link UastUtils#getParentOfType(UElement, Class, boolean)}, except {@code parentClass}
   * is of type {@link PsiElement} instead of {@link UElement}.
   */
  @Nullable
  @Contract("null, _, _ -> null")
  public static <T extends PsiElement> T getPsiParentOfType(@Nullable UElement element,
                                                            @NotNull Class<T> parentClass,
                                                            boolean strict) {
    if (element == null) {
      return null;
    }
    if (strict) {
      element = element.getUastParent();
    }

    while (element != null) {
      PsiElement psiElement = UElementKt.getAsJavaPsiElement(element, parentClass);
      if (psiElement != null) {
        return parentClass.cast(psiElement);
      }
      element = element.getUastParent();
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

    return ResourceType.fromClassName(((PsiClass)elemParent).getName());
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

  /** Returns a modification tracker which tracks changes only to physical XML PSI. */
  @NotNull
  public static ModificationTracker getXmlPsiModificationTracker(@NotNull Project project) {
    PsiModificationTrackerImpl psiTracker = (PsiModificationTrackerImpl)PsiManager.getInstance(project).getModificationTracker();
    return psiTracker.forLanguage(XMLLanguage.INSTANCE);
  }

  /**
   * Returns a modification tracker which tracks changes to all physical PSI *except* XML PSI.
   */
  @NotNull
  public static ModificationTracker getPsiModificationTrackerIgnoringXml(@NotNull Project project) {
    // Note: we also ignore the Language.ANY modification count, because that modification count
    // is incremented unconditionally on every PSI change (see PsiModificationTrackerImpl#incLanguageCounters).
    PsiModificationTrackerImpl psiTracker = (PsiModificationTrackerImpl)PsiManager.getInstance(project).getModificationTracker();
    return psiTracker.forLanguages(lang -> !lang.is(XMLLanguage.INSTANCE) && !lang.is(Language.ANY));
  }

  @Nullable
  public static PsiType toPsiType(@NotNull PsiClass clazz) {
    return JavaPsiFacade.getElementFactory(clazz.getProject()).createType(clazz);
  }

  /**
   * When given an element in a qualified chain expression (eg. `activity` in `R.layout.activity`) for a Java file, this finds the previous
   * element in the chain (in this case `layout`).
   */
  @Nullable
  public static PsiReferenceExpression getPreviousInQualifiedChain(PsiReferenceExpression referenceExpression) {
    PsiExpression expression = referenceExpression.getQualifierExpression();
    return expression instanceof PsiReferenceExpression ? (PsiReferenceExpression)expression : null;
  }
}
