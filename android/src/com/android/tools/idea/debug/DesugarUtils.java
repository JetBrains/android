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
package com.android.tools.idea.debug;

import com.intellij.debugger.NoDataException;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
/**
 * Utility class to deal with desugaring.
 * <p>
 * Desugaring is a transformation process for Android applications to adapt new language features to prior versions of Android. One example
 * of desugaring is the support of non-abstract methods (default and static) in interface types. The transformation implies to synthesize
 * a companion class where the code of each non-abstract methods is moved into.
 */
public final class DesugarUtils {
  /**
   * Returns a list of {@link ClassPrepareRequest} that also contains references to types synthesized by desugaring.
   * <p>
   * If the given requests list contains an interface type that requires desugaring, this method will add a prepare request that matches
   * any inner type of the interface. Indeed desugaring may have synthesized an inner companion class that contains the given position.
   */
  @NotNull
  public static List<ClassPrepareRequest> addExtraPrepareRequestsIfNeeded(@NotNull DebugProcess debugProcess,
                                                                          @NotNull ClassPrepareRequestor requestor,
                                                                          @NotNull SourcePosition position,
                                                                          @NotNull List<ClassPrepareRequest> requests) {
    return ReadAction.compute(() -> {
      PsiClass classHolder = PsiTreeUtil.getParentOfType(position.getElementAt(), PsiClass.class);
      if (classHolder != null && classHolder.isInterface()) {
        PsiMethod methodHolder = PsiTreeUtil.getParentOfType(position.getElementAt(), PsiMethod.class);
        if (methodHolder != null && methodHolder.getBody() != null) {
          // Breakpoint in a non-abstract method in an interface. If desugaring is enabled, we should have a companion class with the
          // actual code.
          // The companion class should be an inner class of the interface. Let's get notified of any inner class that is loaded and
          // check if the class contains the position we're looking for.
          String classPattern = classHolder.getQualifiedName() + "$*";
          ClassPrepareRequestor trampolinePrepareRequestor = new ClassPrepareRequestor() {
            @Override
            public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
              List<Location> locationsOfPosition;
              try {
                locationsOfPosition = debuggerProcess.getPositionManager().locationsOfLine(referenceType, position);
              }
              catch (NoDataException e) {
                locationsOfPosition = Collections.emptyList();
              }
              if (!locationsOfPosition.isEmpty()) {
                requestor.processClassPrepare(debuggerProcess, referenceType);
              }
            }
          };
          ClassPrepareRequest request =
            debugProcess.getRequestsManager().createClassPrepareRequest(trampolinePrepareRequestor, classPattern);
          if (request != null) {
            requests.add(request);
          }
        }
      }
      return requests;
    });
  }
  /**
   * Returns a list of {@link ReferenceType} that also contains references to types synthesized by desugaring.
   * <p>
   * If the given types list contains an interface type that requires desugaring, this method will add to the returned list any inner type
   * that contains the given position in one of its methods.
   */
  @NotNull
  public static List<ReferenceType> addExtraClassesIfNeeded(@NotNull DebugProcess debugProcess,
                                                            @NotNull SourcePosition position,
                                                            @NotNull List<ReferenceType> types,
                                                            @NotNull PositionManager positionManager) throws NoDataException {
    // Find all interface classes that may have a companion class.
    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(debugProcess.getProject());
    GlobalSearchScope globalSearchScope = GlobalSearchScope.allScope(debugProcess.getProject());
    List<ReferenceType> candidatesForDesugaringCompanion = types.stream()
      .filter(type -> ReadAction.compute(() -> {
                PsiClass psiClass =
                  javaPsiFacade.findClass(type.name(), globalSearchScope);
                return psiClass != null && canBeTransformedForDesugaring(psiClass);
              })
      )
      .collect(Collectors.toList());
    if (candidatesForDesugaringCompanion.isEmpty()) {
      return types;
    }
    // There is at least one interface that may have a companion class synthesized by desugaring.
    List<ReferenceType> newReferenceTypes = new ArrayList<>(types);
    List<ReferenceType> allLoadedTypes = debugProcess.getVirtualMachineProxy().allClasses();
    for (ReferenceType candidateType : candidatesForDesugaringCompanion) {
      // Find inner classes in this type.
      final String innerClassNamePrefix = candidateType.name() + "$";
      for (ReferenceType type : allLoadedTypes) {
        if (!type.name().startsWith(innerClassNamePrefix)) {
          // Not an inner type of the candidate.
          continue;
        }
        if (!positionManager.locationsOfLine(type, position).isEmpty()) {
          // Found an inner type that contains the source position.
          newReferenceTypes.add(type);
        }
      }
    }
    return newReferenceTypes;
  }
  private static boolean canBeTransformedForDesugaring(@NotNull PsiClass psiClass) {
    if (!psiClass.isInterface()) {
      return false;
    }
    return StreamEx.of(psiClass.getMethods()).anyMatch(m -> m.getBody() != null);
  }
}