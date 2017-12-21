/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.profilers.stacktrace;

import com.android.tools.idea.profilers.TraceSignatureConverter;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.Processor;
import com.jetbrains.cidr.lang.symbols.OCQualifiedName;
import com.jetbrains.cidr.lang.symbols.OCSymbol;
import com.jetbrains.cidr.lang.symbols.cpp.OCDeclaratorSymbol;
import com.jetbrains.cidr.lang.symbols.cpp.OCFunctionSymbol;
import com.jetbrains.cidr.lang.symbols.symtable.OCGlobalProjectSymbolsCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A {@link CodeNavigator} with logic to jump to code inside of an IntelliJ code editor.
 */
public final class IntellijCodeNavigator extends CodeNavigator {
  private final Project myProject;

  public IntellijCodeNavigator(@NotNull Project project, @NotNull FeatureTracker featureTracker) {
    super(featureTracker);
    myProject = project;
  }

  @Override
  protected void handleNavigate(@NotNull CodeLocation location) {
    Navigatable nav = getNavigatable(location);
    if (nav != null) {
      nav.navigate(true);
    }
  }

  @Override
  public boolean isNavigatable(@NotNull CodeLocation location) {
    return getNavigatable(location) != null;
  }

  @Nullable
  private Navigatable getNavigatable(@NotNull CodeLocation location) {
    if (location.isNativeCode()) {
      return getNativeNavigatable(location);
    }

    PsiClass psiClass = ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myProject), location.getClassName());
    if (psiClass == null) {
      if (location.getLineNumber() >= 0) {
        // There has been at least one case where the PsiManager could not find an inner class in
        // Kotlin code, which caused us to abort navigating. However, if we have the outer class
        // (which is easier for PsiManager to find) and a line number, that's enough information to
        // help us navigate. So, to be more robust against PsiManager error, we try one more time.
        psiClass = ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myProject), location.getOuterClassName());
      }
    }

    if (psiClass == null) {
      return null;
    }
    else if (location.getLineNumber() >= 0) {
      // If the specified CodeLocation has a line number, navigatable is that line
      return new OpenFileDescriptor(myProject, psiClass.getNavigationElement().getContainingFile().getVirtualFile(),
                                    location.getLineNumber(), 0);
    }
    else if (location.getMethodName() != null && location.getSignature() != null) {
      // If it has both method name and signature, navigatable is the corresponding method
      PsiMethod method = findMethod(psiClass, location.getMethodName(), location.getSignature());
      return method != null ? method : psiClass;
    }
    else {
      // Otherwise, navigatable is the class
      return psiClass;
    }
  }

  /**
   * Tries to find and return the method's corresponding {@link Navigatable} within the project. Returns null if the method is not found.
   */
  @Nullable
  private Navigatable getNativeNavigatable(@NotNull CodeLocation location) {
    // We use OCGlobalProjectSymbolsCache#processByQualifiedName to look for the target method. If it finds symbols that match the target
    // method name, it will iterate the list of matched symbols and use the processor below in each one of them, until the processor returns
    // false.
    Navigatable[] navigatable = new Navigatable[1]; // Workaround to set the navigatable inside the processor.

    Processor<OCSymbol> processor = symbol -> {
      if (!(symbol instanceof OCFunctionSymbol)) {
        return true; // Symbol is not a function. Continue the processing.
      }
      OCFunctionSymbol function = ((OCFunctionSymbol)symbol);
      if (!function.getName().equals(location.getMethodName())) {
        return true; // Method name does not match. Continue the processing.
      }
      OCQualifiedName qualifier = function.getQualifiedName().getQualifier();
      String qualifierName = (qualifier == null || qualifier.getName() == null) ? "" : qualifier.getName();
      if (!qualifierName.equals(location.getClassName())) {
        return true; // Class name does not match. Continue the processing.
      }

      // Check if method parameters match the function's
      List<String> parameters = location.getMethodParameters();
      if (parameters != null) {
        List<OCDeclaratorSymbol> functionParams = function.getParameterSymbols();
        if (functionParams.size() != parameters.size()) {
          return true; // Parameters count don't match. Continue the processing.
        }

        boolean match = true;
        for (int i = 0; i < parameters.size(); i++) {
          if (!parameters.get(i).equals(functionParams.get(i).getType().getName())) {
            match = false;
            break;
          }
        }
        if (!match) {
          return true; // Parameters don't match. Continue the processing.
        }
      }

      // We have found a match. Return it.
      navigatable[0] = function;
      //String name = function.getQualifiedName().getQualifier().getName();
      return false;
    };

    assert location.getMethodName() != null;
    OCGlobalProjectSymbolsCache.processByQualifiedName(myProject, processor, location.getMethodName());

    return navigatable[0];
  }

  @Nullable
  private static PsiMethod findMethod(@NotNull PsiClass psiClass, @NotNull String methodName, @NotNull String signature) {
    for (PsiMethod method : psiClass.findMethodsByName(methodName, true)) {
      if (signature.equals(TraceSignatureConverter.getTraceSignature(method))) {
        return method;
      }
    }
    return null;
  }
}
