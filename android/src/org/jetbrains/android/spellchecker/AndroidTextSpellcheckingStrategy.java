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
package org.jetbrains.android.spellchecker;

import com.android.tools.idea.gradle.eclipse.GradleImport;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.*;

/**
 * Spelling strategy for text and property files which is used to mask out typos in
 * specific files that we know we don't want to spell check, such as the "gradlew"
 * launcher script and various property files. In a default project, you end up with
 * over 80 spelling errors from these files!
 */
public class AndroidTextSpellcheckingStrategy extends SpellcheckingStrategy {
  private PsiFile myLastPsiFile;
  private boolean myLastIgnore;

  @Override
  public boolean isMyContext(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null) {
      return false;
    }

    return isIgnored(file);
  }

  private boolean isIgnored(@Nullable PsiFile psiFile) {
    if (psiFile == null) {
      return false;
    }
    if (psiFile == myLastPsiFile) {
      return myLastIgnore;
    }

    myLastPsiFile = psiFile;

    FileType fileType = psiFile.getFileType();
    if (fileType == FileTypes.PLAIN_TEXT) {
      String name = psiFile.getName();
      if (Comparing.equal(name, FN_RESOURCE_TEXT, SystemInfo.isFileSystemCaseSensitive) ||
          Comparing.equal(name, FN_GRADLE_WRAPPER_UNIX, SystemInfo.isFileSystemCaseSensitive) ||
          Comparing.equal(name, FN_GRADLE_WRAPPER_WIN, SystemInfo.isFileSystemCaseSensitive) ||
          Comparing.equal(name, GradleImport.IMPORT_SUMMARY_TXT, SystemInfo.isFileSystemCaseSensitive) ||
          Comparing.equal(name, ".gitignore", SystemInfo.isFileSystemCaseSensitive)) {
        return myLastIgnore = true;
      }
    }
    else if (fileType == StdFileTypes.PROPERTIES) {
      String name = psiFile.getName();
      if (Comparing.equal(name, FN_GRADLE_WRAPPER_PROPERTIES, SystemInfo.isFileSystemCaseSensitive) ||
          Comparing.equal(name, FN_LOCAL_PROPERTIES, SystemInfo.isFileSystemCaseSensitive) ||
          Comparing.equal(name, FN_GRADLE_PROPERTIES, SystemInfo.isFileSystemCaseSensitive)) {
        return myLastIgnore = true;

      }
    }

    return false;
  }

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    return EMPTY_TOKENIZER;
  }
}
