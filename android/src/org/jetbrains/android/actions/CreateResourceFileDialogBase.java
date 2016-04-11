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

import com.android.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for dialogs that create new resource files.
 */
public abstract class CreateResourceFileDialogBase extends DialogWrapper {

  protected CreateResourceFileDialogBase(@Nullable Project project) {
    super(project);
  }

  /**
   * After a user clicks OK and the dialog is validated, call this to get the created resource file.
   *
   * @return the created resource file (or an empty array if failed and the dialog closed).
   */
  @NotNull
  public abstract PsiElement[] getCreatedElements();

  /**
   * Get the name of the new resource file.
   *
   * @return filename
   */
  @VisibleForTesting
  @NotNull
  public abstract String getFileName();

  /**
   * Depending on context, the validation rules may be different, or the context may want to perform other actions before validation.
   * This provides a hook to create a custom validator.
   */
  public interface ValidatorFactory {

    /**
     * Create the validator, given the final chosen parameters
     *
     * @param resourceDirectory the chosen res/ directory
     * @param subdirName        the chosen sub directory (e.g., color)
     * @param rootElement       the chosen root tag (e.g., <selector>)
     * @return a validator
     */
    @NotNull
    ElementCreatingValidator create(@NotNull PsiDirectory resourceDirectory, @NotNull String subdirName,
                                    @Nullable String rootElement);
  }
}
