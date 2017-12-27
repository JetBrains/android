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
package com.android.tools.idea.npw.module;

import com.android.tools.idea.npw.WizardUtils;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * This Expression takes the Application name (eg: "My Application"), and returns a valid module name (eg: "myapplication").
 * It also makes sure that the module name is unique.
 * Further validation of the module name may be needed, if the name is used to create a directory
 * @see PathValidator
 */
public class AppNameToModuleNameExpression extends Expression<String> {
  @Nullable private final Project myProject;
  @NotNull private final StringProperty myApplicationName;

  public AppNameToModuleNameExpression(@Nullable Project project, @NotNull StringProperty applicationName) {
    super(applicationName);

    myProject = project;
    myApplicationName = applicationName;
  }

  @NotNull
  @Override
  public String get() {
    String moduleName = myApplicationName.get()
      .toLowerCase(Locale.US)
      .replaceAll("\\s", "");;

    int i = 2;
    String uniqueModuleName = moduleName;
    while (!WizardUtils.isUniqueModuleName(uniqueModuleName, myProject)) {
      uniqueModuleName = moduleName + Integer.toString(i);
      i++;
    }

    return uniqueModuleName;
  }
}
