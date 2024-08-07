/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.actions.internal;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

class AswbDumpVfs extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
    new Task.Backgroundable(anActionEvent.getProject(), "Enumerating the VFS...") {
      @Override
      public void run(@NotNull ProgressIndicator progressIndicator) {
        try {
          dumpChildrenInDbRecursively(VfsUtil.findFile(Path.of("/"), false));
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }.queue();
  }

  private static void dumpChildrenInDbRecursively(VirtualFile dir) throws IOException {
    try (PrintStream out =
        new PrintStream(
            new BufferedOutputStream(
                Files.newOutputStream(PathManager.getLogDir().resolve("vfs.txt"))),
            false)) {
      if (!(dir instanceof NewVirtualFile)) {
        out.println(dir.getPresentableUrl() + ": not in db (" + dir.getClass().getName() + ")");
        return;
      }

      PersistentFS pfs = PersistentFS.getInstance();
      List<VirtualFile> dirs = new ArrayList<>();
      dirs.add(dir);
      while (!dirs.isEmpty()) {
        ProgressManager.checkCanceled();
        dir = dirs.remove(0);
        if (pfs.wereChildrenAccessed(dir)) {
          out.println(dir.getPath());
          for (String name : pfs.listPersisted(dir)) {
            NewVirtualFile child = ((NewVirtualFile) dir).findChildIfCached(name);
            if (child == null) {
              out.println(dir.getPath() + "/" + name + " - ?? (not found in db)");
              continue;
            }
            if (child.isDirectory()) {
              dirs.add(child);
            }
          }
        }
      }
    }
  }

  static class BulkListener implements BulkFileListener {
    private static final Logger vfsDiagLogger =
        Logger.getInstance("#" + BulkListener.class.getName() + "_vfs_diag");

    @Override
    public void before(List<? extends VFileEvent> events) {
      vfsDiagLogger.info(String.format("VFS Events: (%d)", events.size()));
      for (int i = 0; i < events.size(); i++) {
        VFileEvent event = events.get(i);
        vfsDiagLogger.info("    " + event);
        if (i > 1000) {
          vfsDiagLogger.info("    ...more");
          break;
        }
      }
      vfsDiagLogger.info("    end.");
    }
  }
}
