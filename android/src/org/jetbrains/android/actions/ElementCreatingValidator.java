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
package org.jetbrains.android.actions;

import com.intellij.openapi.ui.InputValidator;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * InputValidator that creates elements when things are validated and the dialog closes
 * with an OK action.
 *
 * This allows a user to grab the created elements.
 */
public interface ElementCreatingValidator extends InputValidator {

  @NotNull
  PsiElement[] getCreatedElements();
}
