package org.jetbrains.jps.android;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.storage.StorageOwner;

import java.io.*;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidProGuardStateStorage implements StorageOwner {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.android.AndroidProGuardStateStorage");

  private final File myFile;

  public AndroidProGuardStateStorage(@NotNull File file) throws IOException {
    myFile = file;
  }

  @Override
  public void flush(boolean memoryCachesOnly) {
  }

  @Override
  public void clean() throws IOException {
    FileUtil.delete(myFile);
  }

  @Override
  public void close() throws IOException {
  }

  @Nullable
  public MyState read() {
    try {
      final DataInputStream input = new DataInputStream(new FileInputStream(myFile));
      try {
        final boolean hasValue = input.readBoolean();

        if (!hasValue) {
          return null;
        }
        final int n = input.readInt();
        final Map<String, Long> cfgFiles = new HashMap<String, Long>();

        for (int i = 0; i < n; i++) {
          final String path = input.readUTF();
          final long timestamp = input.readLong();
          cfgFiles.put(path, timestamp);
        }
        return new MyState(cfgFiles);
      }
      finally {
        input.close();
      }
    }
    catch (FileNotFoundException ignored) {
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return null;
  }

  public void update(@Nullable MyState state) {
    FileUtil.createParentDirs(myFile);
    try {
      final DataOutputStream output = new DataOutputStream(new FileOutputStream(myFile));
      try {
        output.writeBoolean(state != null);

        if (state != null) {
          output.writeInt(state.myProGuardConfigFiles.size());

          for (Map.Entry<String, Long> entry : state.myProGuardConfigFiles.entrySet()) {
            final String path = entry.getKey();
            final Long timestamp = entry.getValue();
            output.writeUTF(path);
            output.writeLong(timestamp);
          }
        }
      }
      finally {
        output.close();
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  public static class MyState {
    private final Map<String, Long> myProGuardConfigFiles;

    public MyState(@NotNull File[] proGuardCfgFiles) {
      myProGuardConfigFiles = new HashMap<String, Long>();

      for (File file : proGuardCfgFiles) {
        myProGuardConfigFiles.put(file.getPath(), FileSystemUtil.lastModified(file));
      }
    }

    private MyState(@NotNull Map<String, Long> proGuardConfigFiles) {
      myProGuardConfigFiles = proGuardConfigFiles;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MyState state = (MyState)o;

      if (!myProGuardConfigFiles.equals(state.myProGuardConfigFiles)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myProGuardConfigFiles.hashCode();
    }
  }
}
