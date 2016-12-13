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
package com.android.tools.idea.monitor.tool;

import com.android.annotations.VisibleForTesting;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CallStackLine {
  @NotNull private final Project myProject;
  @NotNull private final String myLine;

  public CallStackLine(@NotNull Project project, @NotNull String line) {
    myProject = project;
    myLine = line;
  }

  @VisibleForTesting
  @Nullable
  String getClassName() {
    int lastDot = getLastDot();
    if (lastDot == -1) {
      return null;
    }
    String name = myLine.substring(0, getLastDot());
    int dollarIndex = name.indexOf('$');
    return (dollarIndex != -1) ? name.substring(0, dollarIndex) : name;
  }

  @VisibleForTesting
  int getLineNumber() {
    int start = myLine.lastIndexOf(':');
    int end = getCloseBracket();
    if (start >= end || start == -1) {
      return -1;
    }

    try {
      return Integer.parseInt(myLine.substring(start + 1, end)) - 1;
    } catch (Exception e) {
      return -1;
    }
  }

  @Nullable
  public Navigatable getNavigatable() {
    VirtualFile file = findClassFile();
    if (file == null) return null;

    int lineNumber = getLineNumber();
    if (lineNumber == -1) {
      return new OpenFileDescriptor(myProject, file);
    } else {
      return new OpenFileDescriptor(myProject, file, lineNumber, 0);
    }
  }

  @Nullable
  private VirtualFile findClassFile() {
    String className = getClassName();
    if (className == null) {
      return null;
    }
    PsiClass psiClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
    if (psiClass == null) {
      return  null;
    }
    return psiClass.getContainingFile().getVirtualFile();
  }

  private int getLastDot() {
    return myLine.lastIndexOf('.', getOpenBracket());
  }

  private int getOpenBracket() {
    return myLine.indexOf('(');
  }

  private int getCloseBracket() {
    return myLine.indexOf(')');
  }
}
