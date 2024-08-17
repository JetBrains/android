package com.google.idea.sdkcompat.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.impl.LineStatusTrackerBaseContentUtil;
import com.intellij.openapi.vcs.impl.VcsBaseContentProvider;
import com.intellij.openapi.vfs.VirtualFile;

/** Compat class that provide VcsBaseContentProvider delegator. */
public class VcsBaseContentProviderDelegator {
  private final Project project;

  public VcsBaseContentProviderDelegator(Project project) {
    this.project = project;
  }

  public boolean isSupported(VirtualFile file) {
    return LineStatusTrackerBaseContentUtil.isSupported(project, file);
  }

  public VcsBaseContentProvider.BaseContent getBaseRevision(VirtualFile file) {
    return LineStatusTrackerBaseContentUtil.getBaseRevision(project, file);
  }
}
