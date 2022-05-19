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

import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;

/**
 * Spelling strategy for text and property files which is used to mask out typos in
 * specific files that we know we don't want to spell check, such as the "gradlew"
 * launcher script and various property files. In a default project, you end up with
 * over 80 spelling errors from these files!
 */
public class AndroidTextSpellcheckingStrategy extends SpellcheckingStrategy {
  private static final Key<Boolean> mySpellcheckingIgnoredStateKey = Key.create("android.spellchecking.ignored.state");

  @Override
  public boolean isMyContext(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null) {
      return false;
    }

    return isIgnored(file.getViewProvider().getVirtualFile());
  }

  private static boolean isIgnored(@NotNull VirtualFile virtualFile) {
    Boolean spellcheckingIgnoredState = virtualFile.getUserData(mySpellcheckingIgnoredStateKey);

    if (spellcheckingIgnoredState == null) {
      synchronized (mySpellcheckingIgnoredStateKey) {
        spellcheckingIgnoredState = virtualFile.getUserData(mySpellcheckingIgnoredStateKey);

        if (spellcheckingIgnoredState == null) {
          FileType fileType = virtualFile.getFileType();
          boolean lastIgnore = false;

          if (fileType == FileTypes.PLAIN_TEXT) {
            String name = virtualFile.getName();
            if (Comparing.equal(name, FN_RESOURCE_TEXT, virtualFile.isCaseSensitive()) ||
                Comparing.equal(name, FN_GRADLE_WRAPPER_UNIX, virtualFile.isCaseSensitive()) ||
                Comparing.equal(name, FN_GRADLE_WRAPPER_WIN, virtualFile.isCaseSensitive()) ||
                Comparing.equal(name, "import-summary.txt", virtualFile.isCaseSensitive()) ||
                Comparing.equal(name, ".gitignore", virtualFile.isCaseSensitive())) {
              lastIgnore = true;
            }
          }
          //else if (fileType == PropertiesFileType.INSTANCE) {
          //  String name = virtualFile.getName();
          //  if (Comparing.equal(name, FN_GRADLE_WRAPPER_PROPERTIES, virtualFile.isCaseSensitive()) ||
          //      Comparing.equal(name, FN_LOCAL_PROPERTIES, virtualFile.isCaseSensitive()) ||
          //      Comparing.equal(name, FN_GRADLE_PROPERTIES, virtualFile.isCaseSensitive())) {
          //    lastIgnore = true;
          //  }
          //}

          spellcheckingIgnoredState = Boolean.valueOf(lastIgnore);
          virtualFile.putUserData(mySpellcheckingIgnoredStateKey, spellcheckingIgnoredState);
        }
      }
    }

    return spellcheckingIgnoredState;
  }

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    return EMPTY_TOKENIZER;
  }
}
