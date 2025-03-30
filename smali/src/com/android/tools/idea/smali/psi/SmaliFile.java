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
package com.android.tools.idea.smali.psi;

import com.android.tools.idea.smali.SmaliFileType;
import com.android.tools.idea.smali.SmaliIcons;
import com.android.tools.idea.smali.SmaliLanguage;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public class SmaliFile extends PsiFileBase {
  public SmaliFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, SmaliLanguage.getInstance());
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return SmaliFileType.getInstance();
  }

  @Override
  public String toString() {
    return "Smali File";
  }

  @Override
  public Icon getIcon(int flags) {
    return SmaliIcons.SmaliFile;
  }
}
