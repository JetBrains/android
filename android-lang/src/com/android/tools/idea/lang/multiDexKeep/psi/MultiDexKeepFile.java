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
package com.android.tools.idea.lang.multiDexKeep.psi;

import com.android.tools.idea.lang.multiDexKeep.MultiDexKeepFileType;
import com.android.tools.idea.lang.multiDexKeep.MultiDexKeepLanguage;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public class MultiDexKeepFile extends PsiFileBase {
  public MultiDexKeepFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, MultiDexKeepLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return MultiDexKeepFileType.INSTANCE;
  }

  @Override
  public String toString() {
    return "MultiDexKeep File";
  }
}
