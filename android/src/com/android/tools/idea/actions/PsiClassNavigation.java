/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.actions;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Navigates to (goto) class source/definition using the psi data structure to resolve the location.
 * This navigation can also navigate to anonymous inner classes.
 */
public class PsiClassNavigation implements NavigationItem {
  @NotNull private PsiFile myPsiFile;
  private int myOffset;
  private int myLine;

  /**
   * @param offset -1 when not used, >= 0 to navigate to a particular offset in the file
   * @param line   -1 when not used, >= 0 to navigate to a particular line in the file
   */
  private PsiClassNavigation(@NotNull PsiFile file, int offset, int line) {
    myPsiFile = file;
    myOffset = offset;
    myLine = line;
  }

  /**
   * Resolves a fully qualified class name to a {@link com.intellij.pom.Navigatable}
   * @param className   A fully qualified class name.
   * @return an array of navigatable objects, resolving to all possible matches of the className
   */
  @Nullable
  public static PsiClassNavigation[] getNavigationForClass(@NotNull Project project, @Nullable String className) {
    if (className == null || className.isEmpty()) {
      return null;
    }

    PsiClass[] resolvedClasses = resolveClasses(project, className);
    PsiClassNavigation[] navigatables = new PsiClassNavigation[resolvedClasses.length];

    for (int i = 0; i < resolvedClasses.length; ++i) {
      PsiClass c = resolvedClasses[i];
      navigatables[i] = new PsiClassNavigation(c.getContainingFile(), c.getTextOffset(), -1);
    }

    return navigatables;
  }

  @Nullable
  public static PsiClassNavigation[] getNavigationForClass(@NotNull Project project, @Nullable String className, int line) {
    if (className == null || className.isEmpty()) {
      return null;
    }

    PsiClass[] psiClasses = getPsiClassesForOuterClass(project, className);
    PsiClassNavigation[] navigatables = new PsiClassNavigation[psiClasses.length];

    for (int i = 0; i < psiClasses.length; ++i) {
      PsiClass c = psiClasses[i];
      navigatables[i] = new PsiClassNavigation(c.getContainingFile(), -1, line);
    }

    return navigatables;
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(PsiClassNavigation.class);
  }

  /**
   * Resolve the inner class names by iteratively resolving each inner class individually. Note that inner classes can be
   * chained indefinitely: e.g. com.example.Foo$3$SubClass$AnotherSubClass$2$10
   */
  @NotNull
  private static PsiClass[] resolveClasses(@NotNull Project project, @NotNull String className) {
    PsiClass[] psiClasses = getPsiClassesForOuterClass(project, className);
    if (psiClasses.length == 0) {
      return psiClasses;
    }

    String[] classNameComponents = className.split("\\$");
    if (classNameComponents.length == 1) {
      return psiClasses;
    }

    for (int psiClassIndex = 0; psiClassIndex < psiClasses.length; ++psiClassIndex) {
      for (int i = 1; i < classNameComponents.length; ++i) {
        String innerClassName = classNameComponents[i];
        if (startsWithInteger(innerClassName)) {
          int innerClassIndex = convertToClassIndex(innerClassName);
          if (innerClassIndex < 0) {
            getLog().info("Attempted to resolve mismatched class name in hprof file: " + className);
            break; // Invalid class name, just use the (inner)classes resolved so far.
          }

          CountDownAnonymousClassVisitor visitor = new CountDownAnonymousClassVisitor(innerClassIndex);
          psiClasses[psiClassIndex].accept(visitor);
          PsiAnonymousClass anonymousClass = visitor.getAnonymousClass();
          if (anonymousClass != null) {
            psiClasses[psiClassIndex] = anonymousClass;
          }
          else {
            // We weren't able to get the anonymous class at the specified index, so the source file must differ from the hprof dump.
            // Therefore, abort early.
            break;
          }
        }
        else {
          PsiClass innerClass = psiClasses[psiClassIndex].findInnerClassByName(innerClassName, false);
          if (innerClass != null) {
            psiClasses[psiClassIndex] = innerClass;
          }
          else {
            break; // Couldn't find a named inner class, so we should stop searching deeper.
          }
        }
      }
    }

    return psiClasses;
  }

  @NotNull
  private static PsiClass[] getPsiClassesForOuterClass(@NotNull Project project, @NotNull String className) {
    String outerClassName = getOuterClassName(className);
    if (outerClassName == null) {
      return PsiClass.EMPTY_ARRAY;
    }

    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
    PsiClass[] psiClasses = javaPsiFacade.findClasses(outerClassName, GlobalSearchScope.projectScope(project));
    if (psiClasses.length == 0) {
      psiClasses = javaPsiFacade.findClasses(outerClassName, GlobalSearchScope.allScope(project));
    }

    // Try to get the source file, rather than working with a decompiled file.
    for (int i = 0; i < psiClasses.length; ++i) {
      PsiClass psiClass = psiClasses[i];
      if (psiClass.getQualifiedName() == null) {
        continue;
      }

      if (psiClass instanceof PsiCompiledElement) {
        PsiElement fileElement = psiClass.getContainingFile().getNavigationElement();
        if (!(fileElement instanceof PsiCompiledElement) && fileElement instanceof PsiJavaFile) {
          PsiClass[] sourcePsiClasses = ((PsiJavaFile)fileElement).getClasses();
          for (PsiClass sourcePsiClass : sourcePsiClasses) {
            if (psiClass.getQualifiedName().equals(sourcePsiClass.getQualifiedName())) {
              psiClasses[i] = sourcePsiClass;
              break;
            }
          }
        }
      }
    }

    return psiClasses;
  }

  @Nullable
  private static String getOuterClassName(@NotNull String className) {
    int innerClassSymbolIndex = className.indexOf('$');
    if (innerClassSymbolIndex > 0) {
      return className.substring(0, innerClassSymbolIndex);
    }
    else if (innerClassSymbolIndex == 0) {
      getLog().warn("Invalid class name: starts with '$'");
      return null;
    }
    return className;
  }

  private static int convertToClassIndex(@NotNull String innerClassNameString) {
    try {
      return Integer.parseInt(innerClassNameString) - 1; // Anonymous inner class indices start at 1.
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }

  private static boolean startsWithInteger(@NotNull String target) {
    return !target.isEmpty() && Character.isDigit(target.charAt(0));
  }

  @NotNull
  public PsiFile getPsiFile() {
    return myPsiFile;
  }

  @Nullable
  @Override
  public String getName() {
    return myPsiFile.getName();
  }

  @Nullable
  @Override
  public ItemPresentation getPresentation() {
    return myPsiFile.getPresentation();
  }

  @Override
  public void navigate(boolean requestFocus) {
    OpenFileDescriptor fileDescriptor = myOffset >= 0
                                        ? new OpenFileDescriptor(myPsiFile.getProject(), myPsiFile.getVirtualFile(), myOffset)
                                        : new OpenFileDescriptor(myPsiFile.getProject(), myPsiFile.getVirtualFile(), myLine, 0);
    fileDescriptor.navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return myPsiFile.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return myPsiFile.canNavigateToSource();
  }

  /**
   * A visitor that counts the number of anonymous classes that it has visited so far. When the count reaches 0, it will record which class
   * it is currently visiting. It will then keep counting down as it is forced to keep visiting, because no early termination visitors.
   */
  private static class CountDownAnonymousClassVisitor extends JavaRecursiveElementVisitor {
    @Nullable
    private PsiAnonymousClass myAnonymousClass;
    private int myAnonymousClassIndex;

    public CountDownAnonymousClassVisitor(int anonymousClassIndex) {
      myAnonymousClassIndex = anonymousClassIndex;
    }

    @Override
    public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
      super.visitAnonymousClass(aClass);
      if (myAnonymousClassIndex == 0) {
        myAnonymousClass = aClass;
      }
      myAnonymousClassIndex--;
    }

    @Nullable
    public PsiAnonymousClass getAnonymousClass() {
      return myAnonymousClass;
    }
  }
}