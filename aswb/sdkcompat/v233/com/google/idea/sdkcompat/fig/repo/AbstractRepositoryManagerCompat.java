package com.google.idea.sdkcompat.fig.repo;

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsKey;

/** Compat class for AbstractRepositoryManager constructor changes. */
public abstract class AbstractRepositoryManagerCompat<T extends Repository>
    extends AbstractRepositoryManager<T> {
  protected AbstractRepositoryManagerCompat(
      AbstractVcs abstractVcs, Project project, VcsKey vcsKey, String repoDirName) {
    super(project, vcsKey, repoDirName);
  }
}
