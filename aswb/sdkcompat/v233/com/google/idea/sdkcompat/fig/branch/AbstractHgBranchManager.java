package com.google.idea.sdkcompat.fig.branch;

import com.intellij.dvcs.branch.BranchType;
import com.intellij.dvcs.branch.DvcsBranchManager;
import com.intellij.dvcs.branch.DvcsBranchSettings;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.openapi.project.Project;

/** Branch manager for fig projects. */
public class AbstractHgBranchManager extends DvcsBranchManager {
  protected AbstractHgBranchManager(
      Project project,
      DvcsBranchSettings settings,
      BranchType[] branchTypes,
      AbstractRepositoryManager repositoryManager) {
    super(project, settings, branchTypes, repositoryManager);
  }
}
