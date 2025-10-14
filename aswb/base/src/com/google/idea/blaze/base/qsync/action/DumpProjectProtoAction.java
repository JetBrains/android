package com.google.idea.blaze.base.qsync.action;

import static com.google.idea.blaze.qsync.project.ProjectProtoDumperKt.formatTo;

import com.google.common.io.MoreFiles;
import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class DumpProjectProtoAction extends BlazeProjectAction implements DumbAware {

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.REQUIRED;
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    QuerySyncManager qsm = QuerySyncManager.getInstance(project);
    QuerySyncProjectSnapshot snapshot = qsm.getCurrentSnapshot().orElse(null);
    if (snapshot == null) {
      qsm.notifyError("Failed to dump project", "Not loaded");
      return;
    }
    try {
      Path dest = Files.createTempFile("project-dump", ".yaml");
      try (OutputStream out = MoreFiles.asByteSink(dest).openBufferedStream()) {
        formatTo(snapshot.getProject(), out);
      }
      qsm.notifyWarning("Wrote project proto", dest.toString());
      FileEditorManager.getInstance(project).openFile(VfsUtil.findFileByIoFile(dest.toFile(), true), true);
    } catch (IOException ioe) {
      Logger.getInstance(getClass()).warn("Failed to dump project", ioe);
      qsm.notifyError(
          "Failed to dump project", ioe.getClass().getSimpleName() + ": " + ioe.getMessage());
    }
  }
}
