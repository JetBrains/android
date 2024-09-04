package com.google.idea.blaze.base.qsync.action;

import com.google.common.io.MoreFiles;
import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.QuerySyncProject;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.SnapshotHolder;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class DumpProjectProtoAction extends BlazeProjectAction {

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.REQUIRED;
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    QuerySyncManager qsm = QuerySyncManager.getInstance(project);
    QuerySyncProjectSnapshot snapshot =
        qsm.getLoadedProject()
            .map(QuerySyncProject::getSnapshotHolder)
            .flatMap(SnapshotHolder::getCurrent)
            .orElse(null);
    if (snapshot == null) {
      qsm.notifyError("Failed to dump project", "Not loaded");
      return;
    }
    try {
      Path dest = Files.createTempFile("project-dump", "proto");
      try (OutputStream out = MoreFiles.asByteSink(dest).openBufferedStream()) {
        snapshot.project().writeTo(out);
      }
      qsm.notifyWarning("Wrote project proto", dest.toString());
    } catch (IOException ioe) {
      Logger.getInstance(getClass()).warn("Failed to dump project", ioe);
      qsm.notifyError(
          "Failed to dump project", ioe.getClass().getSimpleName() + ": " + ioe.getMessage());
    }
  }
}
