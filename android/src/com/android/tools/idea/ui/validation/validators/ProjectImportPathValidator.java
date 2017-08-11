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
package com.android.tools.idea.ui.validation.validators;

import com.android.tools.adtui.validation.Validator;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Checks the given file is a valid location for a existing project directory.
 * Used for Opening and Importing projects.
 * @param path is used as part of the output message.
 */
public class ProjectImportPathValidator implements Validator<File> {
  private final PathValidator myPathValidator;

  public ProjectImportPathValidator(@NotNull String path) {
    myPathValidator = new PathValidator.Builder().withCommonRules().build(path);
  }

  @NotNull
  @Override
  public Result validate(@NotNull File file) {
    return myPathValidator.validate(file);
  }

  @NotNull
  public Result validate(@NotNull String filePath) {
    return validate(new File(filePath));
  }
}
