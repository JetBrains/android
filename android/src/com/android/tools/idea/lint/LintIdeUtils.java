/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.lint;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.*;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.ide.util.JavaAnonymousClassesHelper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.inspections.lint.AndroidLintUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.CONSTRUCTOR_NAME;

/**
 * Common utilities for handling lint within IntelliJ
 * TODO: Merge with {@link AndroidLintUtil}
 */
public class LintIdeUtils {
  private LintIdeUtils() {
  }

  /**
   * Gets the location of the given element
   *
   * @param file the file containing the location
   * @param element the element to look up the location for
   * @return the location of the given element
   */
  @NonNull
  public static Location getLocation(@NonNull File file, @NonNull PsiElement element) {
    //noinspection ConstantConditions
    assert element.getContainingFile().getVirtualFile() == null
           || FileUtil.filesEqual(VfsUtilCore.virtualToIoFile(element.getContainingFile().getVirtualFile()), file);

    if (element instanceof PsiClass) {
      // Point to the name rather than the beginning of the javadoc
      PsiClass clz = (PsiClass)element;
      PsiIdentifier nameIdentifier = clz.getNameIdentifier();
      if (nameIdentifier != null) {
        element = nameIdentifier;
      }
    }

    TextRange textRange = element.getTextRange();
    Position start = new DefaultPosition(-1, -1, textRange.getStartOffset());
    Position end = new DefaultPosition(-1, -1, textRange.getEndOffset());
    return Location.create(file, start, end).withSource(element);
  }

  /**
   * Returns the {@link PsiFile} associated with a given lint {@link Context}
   *
   * @param context the context to look up the file for
   * @return the corresponding {@link PsiFile}, or null
   */
  @Nullable
  public static PsiFile getPsiFile(@NonNull Context context) {
    LintRequest request = context.getDriver().getRequest();
    Project project = ((LintIdeRequest)request).getProject();
    if (project.isDisposed()) {
      return null;
    }

    VirtualFile file = VfsUtil.findFileByIoFile(context.file, false);
    if (file == null) {
      return null;
    }

    return AndroidPsiUtils.getPsiFileSafely(project, file);
  }

  /** Returns the internal method name */
  @NonNull
  public static String getInternalMethodName(@NonNull PsiMethod method) {
    if (method.isConstructor()) {
      return SdkConstants.CONSTRUCTOR_NAME;
    }
    else {
      return method.getName();
    }
  }

  /**
   * Computes the internal class name of the given class.
   * For example, for PsiClass foo.bar.Foo.Bar it returns foo/bar/Foo$Bar.
   *
   * @param psiClass the class to look up the internal name for
   * @return the internal class name
   * @see ClassContext#getInternalName(String)
   */
  @Nullable
  public static String getInternalName(@NonNull PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass) {
      PsiClass parent = PsiTreeUtil.getParentOfType(psiClass, PsiClass.class);
      if (parent != null) {
        String internalName = getInternalName(parent);
        if (internalName == null) {
          return null;
        }
        return internalName + JavaAnonymousClassesHelper.getName((PsiAnonymousClass)psiClass);
      }
    }
    String sig = ClassUtil.getJVMClassName(psiClass);
    if (sig == null) {
      String qualifiedName = psiClass.getQualifiedName();
      if (qualifiedName != null) {
        return ClassContext.getInternalName(qualifiedName);
      }
      return null;
    } else if (sig.indexOf('.') != -1) {
      // Workaround -- ClassUtil doesn't treat this correctly!
      // .replace('.', '/');
      sig = ClassContext.getInternalName(sig);
    }
    return sig;
  }

  /**
   * Computes the internal JVM description of the given method. This is in the same
   * format as the ASM desc fields for methods; meaning that a method named foo which for example takes an
   * int and a String and returns a void will have description {@code foo(ILjava/lang/String;):V}.
   *
   * @param method the method to look up the description for
   * @param includeName whether the name should be included
   * @param includeReturn whether the return type should be included
   * @return the internal JVM description for this method
   */
  @Nullable
  public static String getInternalDescription(@NonNull PsiMethod method, boolean includeName, boolean includeReturn) {
    assert !includeName; // not yet tested
    assert !includeReturn; // not yet tested

    StringBuilder signature = new StringBuilder();

    if (includeName) {
      if (method.isConstructor()) {
        final PsiClass declaringClass = method.getContainingClass();
        if (declaringClass != null) {
          final PsiClass outerClass = declaringClass.getContainingClass();
          if (outerClass != null) {
            // declaring class is an inner class
            if (!declaringClass.hasModifierProperty(PsiModifier.STATIC)) {
              if (!appendJvmTypeName(signature, outerClass)) {
                return null;
              }
            }
          }
        }
        signature.append(CONSTRUCTOR_NAME);
      } else {
        signature.append(method.getName());
      }
    }

    signature.append('(');

    for (PsiParameter psiParameter : method.getParameterList().getParameters()) {
      if (!appendJvmSignature(signature, psiParameter.getType())) {
        return null;
      }
    }
    signature.append(')');
    if (includeReturn) {
      if (!method.isConstructor()) {
        if (!appendJvmSignature(signature, method.getReturnType())) {
          return null;
        }
      }
      else {
        signature.append('V');
      }
    }
    return signature.toString();
  }

  private static boolean appendJvmTypeName(@NonNull StringBuilder signature, @NonNull PsiClass outerClass) {
    String className = getInternalName(outerClass);
    if (className == null) {
      return false;
    }
    signature.append('L').append(className.replace('.', '/')).append(';');
    return true;
  }

  private static boolean appendJvmSignature(@NonNull StringBuilder buffer, @Nullable PsiType type) {
    if (type == null) {
      return false;
    }
    final PsiType psiType = TypeConversionUtil.erasure(type);
    if (psiType instanceof PsiArrayType) {
      buffer.append('[');
      appendJvmSignature(buffer, ((PsiArrayType)psiType).getComponentType());
    }
    else if (psiType instanceof PsiClassType) {
      PsiClass resolved = ((PsiClassType)psiType).resolve();
      if (resolved == null) {
        return false;
      }
      if (!appendJvmTypeName(buffer, resolved)) {
        return false;
      }
    }
    else if (psiType instanceof PsiPrimitiveType) {
      buffer.append(JVMNameUtil.getPrimitiveSignature(psiType.getCanonicalText()));
    }
    else {
      return false;
    }
    return true;
  }

  /** Returns the resource directories to use for the given module */
  @NotNull
  public static List<File> getResourceDirectories(@NotNull AndroidFacet facet) {
    if (facet.requiresAndroidModel()) {
      AndroidModel androidModel = facet.getAndroidModel();
      if (androidModel != null) {
        List<File> resDirectories = new ArrayList<>();
        List<SourceProvider> sourceProviders = androidModel.getActiveSourceProviders();
        for (SourceProvider provider : sourceProviders) {
          for (File file : provider.getResDirectories()) {
            if (file.isDirectory()) {
              resDirectories.add(file);
            }
          }
        }
        return resDirectories;
      }
    }
    return new ArrayList<>(facet.getMainSourceProvider().getResDirectories());
  }

  public static boolean isApiLevelAtLeast(@Nullable PsiFile file, int minApiLevel, boolean defaultValue) {
    if (file != null) {
      AndroidFacet facet = AndroidFacet.getInstance(file);
      if (facet != null && !facet.isDisposed()) {
        AndroidModuleInfo info = AndroidModuleInfo.getInstance(facet);
        return info.getMinSdkVersion().getApiLevel() >= minApiLevel;
      }
    }

    return defaultValue;
  }
}
