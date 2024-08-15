/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python.resolve;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.python.resolve.provider.PyImportResolverStrategy;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.codeInsight.imports.PyImportCandidateProvider;
import com.jetbrains.python.psi.impl.PyImportResolver;

/**
 * Adds extensions related to python import resolution at startup.<br>
 * We dynamically register the extension points to avoid adding several additional classes for each
 * provider.
 */
public class PyDynamicImportResolverInitializer implements ApplicationComponent {

  @Override
  public void initComponent() {
    registerImportResolvers();
    registerImportCandidateProviders();
  }

  private static void registerImportResolvers() {
    ExtensionPoint<PyImportResolver> ep =
        Extensions.getRootArea().getExtensionPoint(PyImportResolver.EP_NAME);

    // First, look for matches brought in during blaze sync (project-specific resolvers)
    for (PyImportResolverStrategy provider : PyImportResolverStrategy.EP_NAME.getExtensions()) {
      ep.registerExtension(
          (qualifiedName, context, withRoots) -> {
            if (!providerEnabled(context.getProject(), provider)) {
              return null;
            }
            return provider.resolveFromSyncData(qualifiedName, context);
          });
    }
    // Fall back to a workspace-wide resolver (not limited to the .blazeproject targets).
    // This handles both new packages which weren't in the last sync, and genfiles which weren't
    // explicitly enumerated during the previous sync (i.e. not in the ide-info proto).
    for (PyImportResolverStrategy provider : PyImportResolverStrategy.EP_NAME.getExtensions()) {
      ep.registerExtension(
          (qualifiedName, context, withRoots) -> {
            if (!providerEnabled(context.getProject(), provider)) {
              return null;
            }
            return provider.resolveToWorkspaceSource(qualifiedName, context);
          });
    }
  }

  private static void registerImportCandidateProviders() {
    ExtensionPoint<PyImportCandidateProvider> ep =
        Extensions.getRootArea().getExtensionPoint(PyImportCandidateProvider.EP_NAME);

    for (PyImportResolverStrategy provider : PyImportResolverStrategy.EP_NAME.getExtensions()) {
      ep.registerExtension(
          (reference, name, quickFix) -> {
            if (!providerEnabled(reference.getElement().getProject(), provider)) {
              return;
            }
            provider.addImportCandidates(reference, name, quickFix);
          });
    }
  }

  private static boolean providerEnabled(Project project, PyImportResolverStrategy provider) {
    return Blaze.isBlazeProject(project)
        && provider.appliesToBuildSystem(Blaze.getBuildSystemName(project));
  }
}
