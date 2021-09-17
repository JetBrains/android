// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.compiler;

import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.compiler.server.BuildManager;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class AndroidPrecompileTask implements CompileTask {

  @Override
  public boolean execute(@NotNull CompileContext context) {
    final Project project = context.getProject();

    if (!ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return true;
    }
    BuildManager.forceModelLoading(context);

    // in out-of-process mode gen roots will be excluded by AndroidExcludedJavaSourceRootProvider
    // we do it here for internal mode and also to make there roots 'visibly excluded' in IDE settings
    createGenModulesAndSourceRoots(project);

    return true;
  }

  private static void createGenModulesAndSourceRoots(final Project project) {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    final List<AndroidFacet> facets = ProjectSystemUtil.getAndroidFacets(project);
    if (!facets.isEmpty()) {
      ApplicationManager.getApplication().invokeAndWait(() -> AndroidCompileUtil.createGenModulesAndSourceRoots(project, facets),
                                                        indicator != null ? indicator.getModalityState() : ModalityState.NON_MODAL);
    }
  }
}
