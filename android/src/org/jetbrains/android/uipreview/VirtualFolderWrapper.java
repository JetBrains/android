package org.jetbrains.android.uipreview;

import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VirtualFolderWrapper implements IAbstractFolder {
  private final Project myProject;
  private final VirtualFile myFolder;

  public VirtualFolderWrapper(@NotNull Project project, @NotNull VirtualFile folder) {
    myProject = project;
    myFolder = folder;
  }

  @Nullable
  @Override
  public IAbstractFile getFile(String name) {
    final VirtualFile child = myFolder.findChild(name);
    return child != null && !child.isDirectory()
           ? new VirtualFileWrapper(myProject, child)
           : null;
  }

  @Override
  public String getOsLocation() {
    return FileUtil.toSystemDependentName(myFolder.getPath());
  }

  @Override
  public boolean exists() {
    return myFolder.exists();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    VirtualFolderWrapper wrapper = (VirtualFolderWrapper)o;

    if (!myFolder.equals(wrapper.myFolder)) {
      return false;
    }
    if (!myProject.equals(wrapper.myProject)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = myProject.hashCode();
    result = 31 * result + myFolder.hashCode();
    return result;
  }
}
