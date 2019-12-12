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
package com.android.tools.idea.mlkit;

import com.android.SdkConstants;
import com.google.common.collect.ImmutableSet;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a light class auto-generated for a specific model file in the assets folder.
 */
public class LightModelClass extends AndroidLightClassBase {
  private final LightModelClassConfig myClassConfig;
  private final PsiJavaFile myContainingFile;

  public LightModelClass(@NotNull Module module, @NotNull LightModelClassConfig classConfig) {
    super(PsiManager.getInstance(module.getProject()), ImmutableSet.of(PsiModifier.PUBLIC, PsiModifier.FINAL));
    myClassConfig = classConfig;

    myContainingFile = (PsiJavaFile)PsiFileFactory.getInstance(module.getProject()).createFileFromText(
      classConfig.myModelMetadata.myClassName + SdkConstants.DOT_JAVA,
      JavaFileType.INSTANCE,
      "// This class is generated on-the-fly by the IDE.");
    myContainingFile.setPackageName(classConfig.myPackageName);

    setModuleInfo(module, false);
  }

  @Override
  public String getName() {
    return myClassConfig.myModelMetadata.myClassName;
  }

  @NotNull
  @Override
  public String getQualifiedName() {
    return myClassConfig.myPackageName + "." + getName();
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  @Override
  public PsiFile getContainingFile() {
    return myContainingFile;
  }

  @Override
  public boolean isValid() {
    return true;
  }
}
