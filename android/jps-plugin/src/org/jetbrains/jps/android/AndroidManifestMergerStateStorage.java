package org.jetbrains.jps.android;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.TObjectLongHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.StorageProvider;
import org.jetbrains.jps.incremental.storage.StorageOwner;

import java.io.*;
import java.util.Collection;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidManifestMergerStateStorage implements StorageOwner {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.android.AndroidPackagingStateStorage");

  public static final StorageProvider<AndroidManifestMergerStateStorage> PROVIDER = new StorageProvider<AndroidManifestMergerStateStorage>() {
    @NotNull
    @Override
    public AndroidManifestMergerStateStorage createStorage(File targetDataDir) throws IOException {
      return new AndroidManifestMergerStateStorage(AndroidJpsUtil.getStorageFile(targetDataDir, "manifest_merger"));
    }
  };

  private final File myFile;

  private AndroidManifestMergerStateStorage(@NotNull File file) {
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
        return new MyState(input);
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

  public void saveState(@NotNull MyState state) {
    FileUtil.createParentDirs(myFile);
    try {
      final DataOutputStream output = new DataOutputStream(new FileOutputStream(myFile));
      try {
        output.writeLong(state.myManifestFileTimestamp);
        output.writeInt(state.myLibManifestsTimestamps.size());

        for (Object key : state.myLibManifestsTimestamps.keys()) {
          final String strKey = (String)key;
          output.writeUTF(strKey);
          output.writeLong(state.myLibManifestsTimestamps.get(strKey));
        }
        output.writeBoolean(state.myToMerge);
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
    private final long myManifestFileTimestamp;
    private final TObjectLongHashMap<String> myLibManifestsTimestamps;
    private final boolean myToMerge;

    public MyState(@NotNull File manifestFile, @NotNull Collection<File> libManifestFiles, boolean toMerge) {
      myManifestFileTimestamp = FileSystemUtil.lastModified(manifestFile);
      myLibManifestsTimestamps = new TObjectLongHashMap<String>(libManifestFiles.size());

      for (File libManifestFile : libManifestFiles) {
        myLibManifestsTimestamps.put(FileUtil.toCanonicalPath(libManifestFile.getPath()),
                                     FileSystemUtil.lastModified(libManifestFile));
      }
      myToMerge = toMerge;
    }

    private MyState(DataInput input) throws IOException {
      myManifestFileTimestamp = input.readLong();
      final int libManifestsCount = input.readInt();
      myLibManifestsTimestamps = new TObjectLongHashMap<String>(libManifestsCount);

      for (int i = 0; i < libManifestsCount; i++) {
        final String path = input.readUTF();
        final long timestamp = input.readLong();
        myLibManifestsTimestamps.put(path, timestamp);
      }
      myToMerge = input.readBoolean();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MyState state = (MyState)o;

      if (myManifestFileTimestamp != state.myManifestFileTimestamp) return false;
      if (myToMerge != state.myToMerge) return false;
      if (!myLibManifestsTimestamps.equals(state.myLibManifestsTimestamps)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = (int)(myManifestFileTimestamp ^ (myManifestFileTimestamp >>> 32));
      result = 31 * result + myLibManifestsTimestamps.hashCode();
      result = 31 * result + (myToMerge ? 1 : 0);
      return result;
    }
  }
}
