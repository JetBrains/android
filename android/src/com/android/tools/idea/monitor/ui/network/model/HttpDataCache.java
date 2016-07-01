package com.android.tools.idea.monitor.ui.network.model;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class HttpDataCache {

  private final IDevice myDevice;

  // TODO: Delete files when the cache is destroyed before studio exits.
  private final Map<String, File> myFiles = new HashMap<>();

  public HttpDataCache(@NotNull IDevice device) {
    myDevice = device;
  }

  public File getFile(String path) {
    File tempFile = myFiles.get(path);
    if (tempFile == null) {
      int fileNameStartIndex = path.lastIndexOf('\\');
      String fileName = fileNameStartIndex >= 0 ? path.substring(fileNameStartIndex) : path;
      try {
        tempFile = FileUtil.createTempFile(fileName, null);
        tempFile.deleteOnExit();
        myDevice.pullFile(path, tempFile.getAbsolutePath());
        myFiles.put(path, tempFile);
      }
      catch (IOException | AdbCommandRejectedException | TimeoutException | SyncException e) {
        // TODO: Add logs
      }
    }
    return tempFile;
  }
}
