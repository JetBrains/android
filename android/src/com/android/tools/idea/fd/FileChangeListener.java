/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.fd;

import com.android.annotations.concurrency.GuardedBy;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Gradle can optimize what it builds if it knows that only certain types of files have changed (for instance, only Java files, or only
 * resources). {@link FileChangeListener} listens to file change events in order to categorize them to specific type of file changes. This
 * information is then passed to Gradle while building the project.
 */
public class FileChangeListener implements BulkFileListener {
  private final Object myLock = new Object();
  private final Project myProject;

  /**
   * Whether, since the last build, we've seen changes to any files <b>other</b> than Java or XML files (since Java-only changes
   * can be processed faster by Gradle by skipping portions of the dependency graph, and similarly for resource files)
   */
  @GuardedBy("myLock")
  private boolean mySeenNonSourceChanges = true; // Initially true: on IDE start we don't know what you've done outside the IDE

  @GuardedBy("myLock")
  private boolean mySeenLocalJavaChanges;

  @GuardedBy("myLock")
  private boolean mySeenLocalResourceChanges;

  /** File listener connection */
  @Nullable private MessageBusConnection myConnection;

  public FileChangeListener(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public Changes getChangesAndReset() {
    Changes changes;

    synchronized (myLock) {
      changes = new Changes(mySeenNonSourceChanges, mySeenLocalResourceChanges, mySeenLocalJavaChanges);

      mySeenNonSourceChanges = false;
      mySeenLocalResourceChanges = false;
      mySeenLocalJavaChanges = false;

      if (changes.nonSourceChanges) {
        // listener was stopped as soon as we see the first non source change
        startFileListener();
      }
    }

    return changes;
  }

  public void setEnabled(boolean en) {
    if (en) {
      startFileListener();
    }
    else {
      stopFileListener();
    }
  }

  private void startFileListener() {
    synchronized (myLock) {
      if (myConnection == null) {
        myConnection = ApplicationManager.getApplication().getMessageBus().connect(myProject);
        myConnection.subscribe(VirtualFileManager.VFS_CHANGES, this);
      }
    }
  }

  private void stopFileListener() {
    synchronized (myLock) {
      if (myConnection != null) {
        myConnection.disconnect();
        myConnection = null;
      }
    }
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
  }

  @SuppressWarnings("IfStatementWithIdenticalBranches")
  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    if (myProject.isDisposed()) {
      return;
    }

    for (VFileEvent event : events) {
      VirtualFile file = event.getFile();

      // Determines whether changing the given file constitutes a "simple Java change" that Gradle
      // can process without involving the full dex dependency chain. This is true for changes to
      // Java files in an app module. It's also true for files <b>not</b> related to compilation.
      // Similarly, it tracks local resource changes.

      if (file == null) {
        continue;
      }

      ProjectFileIndex projectIndex = ProjectFileIndex.SERVICE.getInstance(myProject);
      if (!projectIndex.isInSource(file)) {
        // Ignore common file events -- such as workspace.xml on Window focus loss etc
        if (file.getName().endsWith(DOT_GRADLE)) {
          // build.gradle at the root level is not part of the project
          recordNonLocalChange();
          return;
        }
        continue;
      }

      // Make sure it's not something like for example
      //    .AndroidStudioX.Y/config/options/statistics.application.usages.xml
      Module module = projectIndex.getModuleForFile(file, false);
      if (module == null) {
        continue;
      }

      if (GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, myProject)) {
        // This filters out edits like .dex files in build etc
        continue;
      }

      // Make sure the editing is in an Android app module
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && !facet.isLibraryProject()) {
        FileType fileType = file.getFileType();
        if (fileType == StdFileTypes.JAVA) {
          recordSimpleJavaEdit();
          continue;
        }
        else if (fileType == StdFileTypes.XML &&
                 !file.getName().equals(ANDROID_MANIFEST_XML) &&
                 AndroidResourceUtil.isResourceFile(file, facet)) {
          recordSimpleResourceEdit();
          continue;
        }
        else if (fileType.isBinary() &&
                 fileType == FileTypeManager.getInstance().getFileTypeByExtension(EXT_PNG) &&
                 AndroidResourceUtil.isResourceFile(file, facet)) {
          // Drawable resource
          recordSimpleResourceEdit();
          continue;
        } // else: It's possible that it's an edit to a resource of an arbitrary file type in res/raw*/ or other assets;
        // for now, these will result in full incremental rebuilds.
      } // else: edit in a non-Android module or a library: these require full incremental builds

      recordNonLocalChange();
      break;
    }
  }

  /** Called when we've noticed an edit of a Java file that is in an app module */
  private void recordSimpleJavaEdit() {
    synchronized (myLock) {
      mySeenLocalJavaChanges = true;
    }
  }

  /** Called when we've noticed an edit of a resource file that is in an app module */
  private void recordSimpleResourceEdit() {
    synchronized (myLock) {
      mySeenLocalResourceChanges = true;
    }
  }

  /** Called when we've noticed an edit outside of an app module, or in something other than a resource file or a Java file */
  private void recordNonLocalChange() {
    synchronized (myLock) {
      mySeenNonSourceChanges = true;
      // We no longer need to listen for changes to files while the user continues editing until the next build
      stopFileListener();
    }
  }

  public static final class Changes {
    public final boolean nonSourceChanges;
    public final boolean localResourceChanges;
    public final boolean localJavaChanges;

    public Changes(boolean nonSourceChanges, boolean localResourceChanges, boolean localJavaChanges) {
      this.nonSourceChanges = nonSourceChanges;
      this.localResourceChanges = localResourceChanges;
      this.localJavaChanges = localJavaChanges;
    }
  }
}
