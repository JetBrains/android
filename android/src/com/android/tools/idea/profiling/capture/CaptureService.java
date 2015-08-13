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
package com.android.tools.idea.profiling.capture;

import com.android.tools.idea.stats.UsageTracker;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class CaptureService {

  public static final String FD_CAPTURES = "captures";

  @NotNull private final Project myProject;
  @NotNull private Multimap<CaptureType, Capture> myCaptures;
  private List<CaptureListener> myListeners;

  public CaptureService(@NotNull Project project) {
    myProject = project;
    myCaptures = LinkedListMultimap.create();
    myListeners = new LinkedList<CaptureListener>();

    update();
  }

  @NotNull
  public static CaptureService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, CaptureService.class);
  }

  private static Set<VirtualFile> findCaptureFiles(@NotNull VirtualFile[] files, @NotNull CaptureType type) {
    Set<VirtualFile> set = new HashSet<VirtualFile>();
    for (VirtualFile file : files) {
      if (type.isValidCapture(file)) {
        set.add(file);
      }
    }
    return set;
  }

  public void update() {
    CaptureTypeService service = CaptureTypeService.getInstance();
    VirtualFile dir = getCapturesDirectory();
    Multimap<CaptureType, Capture> updated = LinkedListMultimap.create();
    if (dir != null) {
      VirtualFile[] children = VfsUtil.getChildren(dir);
      for (CaptureType type : service.getCaptureTypes()) {
        Set<VirtualFile> files = findCaptureFiles(children, type);
        for (Capture capture : myCaptures.get(type)) {
          // If an existing capture exists for a file, use it: Remove it from the files and add the already existing one.
          if (files.remove(capture.getFile())) {
            updated.put(type, capture);
          }
        }
        for (VirtualFile newFile : files) {
          updated.put(type, type.createCapture(newFile));
        }
      }
    }
    myCaptures = updated;
  }

  @NotNull
  public VirtualFile createCapturesDirectory() throws IOException {
    VirtualFile projectDir = LocalFileSystem.getInstance().findFileByPath(myProject.getBasePath());
    if (projectDir != null) {
      VirtualFile dir = projectDir.findChild(FD_CAPTURES);
      if (dir == null) {
        dir = projectDir.createChildDirectory(null, FD_CAPTURES);
      }
      return dir;
    }
    else {
      throw new IOException("Unable to create the captures directory: Project directory not found.");
    }
  }

  @Nullable
  public VirtualFile getCapturesDirectory() {
    VirtualFile projectDir = LocalFileSystem.getInstance().findFileByPath(myProject.getBasePath());
    return projectDir != null ? projectDir.findChild(FD_CAPTURES) : null;
  }

  @NotNull
  public Multimap<CaptureType, Capture> getCapturesByType() {
    return myCaptures;
  }

  @NotNull
  public Collection<Capture> getCaptures() {
    return myCaptures.values();
  }

  @NotNull
  public Collection<CaptureType> getTypes() {
    return myCaptures.keySet();
  }

  public Capture createCapture(Class<? extends CaptureType> clazz, byte[] data) throws IOException {

    CaptureType type = CaptureTypeService.getInstance().getType(clazz);
    assert type != null;

    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_PROFILING, UsageTracker.ACTION_PROFILING_CAPTURE, type.getName(), null);

    VirtualFile dir = createCapturesDirectory();
    File file = new File(dir.createChildData(null, type.createCaptureFileName()).getPath());
    FileUtil.writeToFile(file, data);
    final VirtualFile vf = VfsUtil.findFileByIoFile(file, true);
    if (vf == null) {
      throw new IOException("Cannot find virtual file for capture file " + file.getPath());
    }
    Capture capture = type.createCapture(vf);
    myCaptures.put(type, capture);

    OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, capture.getFile());
    FileEditorManager.getInstance(myProject).openEditor(descriptor, true);

    for (CaptureListener listener : myListeners) {
      listener.onCreate(capture);
    }
    return capture;
  }

  public interface CaptureListener {
    void onCreate(Capture capture);
  }

  public void addListener(CaptureListener listener) {
    myListeners.add(listener);
  }
}

