/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.refactoring.rtl;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RtlSupportUsageViewDescriptor implements UsageViewDescriptor {
  public RtlSupportUsageViewDescriptor() {
  }

  @NotNull
  @Override
  public PsiElement[] getElements() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  public String getProcessedElementsHeader() {
    return "Items to be converted";
  }

  @Override
  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return String.format("RTL References in code %1$s", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getInfo() {
    return AndroidBundle.message("android.refactoring.rtl.addsupport.dialog.apply.button.text");
  }
}
