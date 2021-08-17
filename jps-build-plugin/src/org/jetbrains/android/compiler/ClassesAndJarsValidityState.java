// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.compiler;

import com.intellij.compiler.CompilerIOUtil;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

class ClassesAndJarsValidityState implements ValidityState {
  private Map<String, Long> myFiles;

  private void fillMap(VirtualFile file, Set<VirtualFile> visited) {
    if (file.isDirectory() && visited.add(file)) {
      for (VirtualFile child : file.getChildren()) {
        fillMap(child, visited);
      }
    }
    else if (FileTypeRegistry.getInstance().isFileOfType(file, StdFileTypes.CLASS) || file.getFileType() instanceof ArchiveFileType) {
      if (file.isValid()) {
        myFiles.put(file.getPath(), file.getTimeStamp());
      }
    }
  }

  public ClassesAndJarsValidityState(@NotNull Collection<VirtualFile> files) {
    myFiles = new HashMap<>();
    Set<VirtualFile> visited = new HashSet<VirtualFile>();
    for (VirtualFile file : files) {
      fillMap(file, visited);
    }
  }

  public ClassesAndJarsValidityState(@NotNull DataInput in) throws IOException {
    myFiles = new HashMap<>();
    int size = in.readInt();
    while (size-- > 0) {
      final String path = CompilerIOUtil.readString(in);
      final long timestamp = in.readLong();
      myFiles.put(path, timestamp);
    }
  }

  @Override
  public boolean equalsTo(ValidityState otherState) {
    return otherState instanceof ClassesAndJarsValidityState
           && myFiles.equals(((ClassesAndJarsValidityState)otherState).myFiles);
  }

  @Override
  public void save(DataOutput out) throws IOException {
    out.writeInt(myFiles.size());
    for (String dependency : myFiles.keySet()) {
      CompilerIOUtil.writeString(dependency, out);
      out.writeLong(myFiles.get(dependency));
    }
  }
}
