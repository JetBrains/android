/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.npw;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Creates the wizard paths to be displayed in Android Studio's New Module dialog.
 *
 */
public interface NewModuleDynamicPathFactory {
  ExtensionPointName<NewModuleDynamicPathFactory> EP_NAME = ExtensionPointName.create("org.jetbrains.android.newModuleDynamicPathFactory");

  Collection<NewModuleDynamicPath> createWizardPaths(@Nullable Project project, @NotNull Disposable disposable);
}
