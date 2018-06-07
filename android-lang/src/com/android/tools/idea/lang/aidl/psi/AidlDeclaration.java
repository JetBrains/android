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
package com.android.tools.idea.lang.aidl.psi;

import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * AidlDeclaration is either a {@code AidlParcelableDeclaration}, {@code AidlInterfaceDeclaration} or {@code AidlMethodDeclaration}.
 */
public interface AidlDeclaration extends AidlPsiCompositeElement, PsiNameIdentifierOwner {
  @NotNull
  AidlDeclarationName getDeclarationName();

  @NotNull
  @Override
  String getName();

  @NotNull
  String getQualifiedName();

  /**
   * Get the PSI element of the generated Java code for this AIDL declaration.
   * @return
   */
  @Nullable
  PsiNameIdentifierOwner getGeneratedPsiElement();
}
