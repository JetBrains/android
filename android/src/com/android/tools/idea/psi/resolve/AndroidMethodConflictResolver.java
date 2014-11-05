/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.psi.resolve;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import org.jetbrains.android.AndroidSdkResolveScopeProvider;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * There is an android specifics that either <code>*.class</code> or <code>*.java</code> files from android sdk
 * are included to the resolve scope. That might produce errors like
 * <a href="https://code.google.com/p/android/issues/detail?id=70135">this</a> or
 * <a href="https://youtrack.jetbrains.com/issue/IDEA-131368">this one</a>.
 * <p/>
 * Android integration tries to prevent that via {@link AndroidSdkResolveScopeProvider.MyJdkScope#compare(VirtualFile, VirtualFile)}
 * by excluding <code>*.class</code> files from the resolve scope. However, it doesn't work for situation when a super-class' method
 * is called inside an object of an android-specific class - resolve process goes up the hierarchy and reaches <code>*.class</code>
 * file then. <code>*.java</code> file is also in resolve scope and we have a conflict.
 * <p/>
 * Current conflict resolver encapsulates logic which prefers <code>*.java</code> android sdk info to <code>*.class</code>
 * android sdk info.
 */
public class AndroidMethodConflictResolver implements PsiConflictResolver {

  @Nullable
  @Override
  public CandidateInfo resolveConflict(@NotNull List<CandidateInfo> conflicts) {
    if (conflicts.isEmpty()) {
      return null;
    }

    ProjectFileIndex index = null;
    for (CandidateInfo conflict : conflicts) {
      PsiElement element = conflict.getElement();
      if (element != null) {
        index = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
        break;
      }
    }

    if (index == null) {
      return null;
    }

    CandidateInfo sourceInfo = null;
    Sdk sdk = null;
    for (CandidateInfo conflict : conflicts) {
      PsiElement element = conflict.getElement();
      if (element == null) {
        continue;
      }
      PsiFile psiFile = element.getContainingFile();
      if (psiFile == null) {
        continue;
      }
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) {
        continue;
      }

      // We want to process a situation only with android sdk classes (either binary or source) which participate in method resolving.
      List<OrderEntry> orderEntries = index.getOrderEntriesForFile(virtualFile);
      if (orderEntries.size() != 1) {
        return null;
      }
      OrderEntry orderEntry = orderEntries.get(0);
      if (!(orderEntry instanceof JdkOrderEntry)) {
        return null;
      }
      Sdk currentSdk = ((JdkOrderEntry)orderEntry).getJdk();
      if (!AndroidSdkUtils.isAndroidSdk(currentSdk) || (sdk != null && !sdk.equals(currentSdk))) {
        return null;
      }
      sdk = currentSdk;

      if (index.isInLibrarySource(virtualFile)) {
        if (sourceInfo == null) {
          sourceInfo = conflict;
        }
        else {
          // Adjust only 'a *.class VS single *.java' case.
          return null;
        }
      }
    }
    return sourceInfo;
  }
}
