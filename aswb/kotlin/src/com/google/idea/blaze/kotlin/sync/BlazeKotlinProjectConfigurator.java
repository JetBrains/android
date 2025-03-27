/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.kotlin.sync;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.psi.PsiElement;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.config.ApiVersion;
import org.jetbrains.kotlin.config.LanguageFeature;
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootGroup;
import org.jetbrains.kotlin.idea.configuration.ConfigureKotlinStatus;
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurator;
import org.jetbrains.kotlin.idea.projectConfiguration.LibraryJarDescriptor;
import org.jetbrains.kotlin.platform.TargetPlatform;
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms;

/** A kotlin project configurator to suppress "Configure Kotlin" banner. */
public class BlazeKotlinProjectConfigurator implements KotlinProjectConfigurator {

  @NotNull
  @Override
  public String getName() {
    return "Bazel";
  }

  @NotNull
  @Override
  public String getPresentableText() {
    return "Bazel";
  }

  @NotNull
  @Override
  public TargetPlatform getTargetPlatform() {
    return JvmPlatforms.INSTANCE.getUnspecifiedJvmPlatform();
  }

  @Override
  public void addLibraryDependency(
    @NotNull Module module,
    @NotNull PsiElement psiElement,
    @NotNull ExternalLibraryDescriptor externalLibraryDescriptor,
    @NotNull LibraryJarDescriptor libraryJarDescriptor,
    @NotNull DependencyScope dependencyScope) {}

  @Override
  public void changeGeneralFeatureConfiguration(
    @NotNull Module module,
    @NotNull LanguageFeature languageFeature,
    @NotNull LanguageFeature.State state,
    boolean b) {}

  @Override
  public void configure(@NotNull Project project, @NotNull Collection<Module> collection) {}

  @NotNull
  @Override
  public ConfigureKotlinStatus getStatus(@NotNull ModuleSourceRootGroup moduleSourceRootGroup) {
    return getStatus(moduleSourceRootGroup.getBaseModule());
  }

  @Override
  public void updateLanguageVersion(
    @NotNull Module module,
    @Nullable String languageVersion,
    @Nullable String apiVersion,
    @NotNull ApiVersion requiredStdlibVersion,
    boolean forTests) {}

  @NotNull
  public ConfigureKotlinStatus getStatus(@NotNull Module module) {
    return ConfigureKotlinStatus.CONFIGURED;
  }
}
