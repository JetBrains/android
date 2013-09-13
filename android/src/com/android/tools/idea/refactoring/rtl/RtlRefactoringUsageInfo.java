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
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

class RtlRefactoringUsageInfo extends UsageInfo {

  private RtlRefactoringType myType = RtlRefactoringType.UNDEFINED;
  private boolean myCreateV17;
  private int myAndroidManifestMinSdkVersion;

  public enum RtlRefactoringType {
    UNDEFINED,
    MANIFEST_SUPPORTS_RTL,
    MANIFEST_TARGET_SDK,
    LAYOUT_FILE_ATTRIBUTE,
    STYLE
  }

  public RtlRefactoringUsageInfo(@NotNull PsiElement element, int startOffset, int endOffset) {
    super(element, startOffset, endOffset);
  }

  RtlRefactoringType getType() {
    return myType;
  }

  void setType(RtlRefactoringType type) {
    myType = type;
  }

  boolean isCreateV17() {
    return myCreateV17;
  }

  void setCreateV17(boolean createV17) {
    myCreateV17 = createV17;
  }

  int getAndroidManifestMinSdkVersion() {
    return myAndroidManifestMinSdkVersion;
  }

  void setAndroidManifestMinSdkVersion(int version) {
    myAndroidManifestMinSdkVersion = version;
  }
}
