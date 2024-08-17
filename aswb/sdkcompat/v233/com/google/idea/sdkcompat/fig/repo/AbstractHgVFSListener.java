package com.google.idea.sdkcompat.fig;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsVFSListener;
import java.util.Collection;
import java.util.List;

/** Compat class for HgVFSListener */
public abstract class AbstractHgVFSListener extends VcsVFSListener {
  protected AbstractHgVFSListener(final Project project, final AbstractVcs vcs) {
    super(project, vcs);
  }

  protected boolean shouldIgnoreDeletion(FileStatus status) {
    return status == FileStatus.UNKNOWN;
  }

  protected abstract void skipNotUnderHg(Collection<FilePath> filesToFilter);

  protected abstract List<FilePath> processAndGetVcsIgnored(List<FilePath> filePaths);

  protected void skipVcsIgnored(List<FilePath> filePaths) {}

  protected void acquireFilesToDelete(
      List<FilePath> filesToDelete, List<FilePath> filesToConfirmDeletion) {

    filesToConfirmDeletion.addAll(myProcessor.acquireDeletedFiles());

    // skip files which are not under Mercurial
    skipNotUnderHg(filesToConfirmDeletion);
    skipVcsIgnored(filesToConfirmDeletion);
  }
}
