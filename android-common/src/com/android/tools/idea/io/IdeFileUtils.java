/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.io;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.util.ExecUtil;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.SystemProperties;
import com.intellij.util.system.OS;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.ShlObj;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * File-related methods dependent on the IntelliJ Platform.
 */
public class IdeFileUtils {
  /**
   * Returns the Desktop directory for the current user. The directory may not exist.
   * Copied from {@link GotoDesktopDirAction} with minor changes.
   */
  public static @NotNull Path getDesktopDirectory() {
    if (OS.CURRENT == OS.Windows && JnaLoader.isLoaded()) {
      char[] path = new char[WinDef.MAX_PATH];
      WinNT.HRESULT res = Shell32.INSTANCE.SHGetFolderPath(null, ShlObj.CSIDL_DESKTOP, null, ShlObj.SHGFP_TYPE_CURRENT, path);
      if (WinError.S_OK.equals(res)) {
        int len = 0;
        while (len < path.length && path[len] != 0) len++;
        return Path.of(new String(path, 0, len));
      }
    }
    else if (OS.CURRENT == OS.macOS && JnaLoader.isLoaded()) {
      ID manager = Foundation.invoke(Foundation.getObjcClass("NSFileManager"), "defaultManager");
      ID url = Foundation.invoke(manager, "URLForDirectory:inDomain:appropriateForURL:create:error:",
                                 12 /*NSDesktopDirectory*/, 1 /*NSUserDomainMask*/, null, false, null);
      String path = Foundation.toStringViaUTF8(Foundation.invoke(url, "path"));
      if (path != null) {
        return Path.of(path);
      }
    }
    else if (PathEnvironmentVariableUtil.isOnPath("xdg-user-dir")) {
      var path = ExecUtil.execAndReadLine(new GeneralCommandLine("xdg-user-dir", "DESKTOP"));
      if (path != null && !path.isBlank()) {
        try {
          return Path.of(path);
        }
        catch (InvalidPathException e) {
          Logger.getInstance(IdeFileUtils.class).error("str='" + path + "' JNU=" + System.getProperty("sun.jnu.encoding"), e);
        }
      }
    }

    return Path.of(SystemProperties.getUserHome(), "Desktop");
  }

  /**
   * Returns the Desktop directory for the current user, or null if it doesn't exist.
   */
  public static @Nullable Path getDesktopDirectoryIfExists() {
    Path dir = getDesktopDirectory();
    return Files.isDirectory(dir) ? dir : null;
  }

  /**
   * Returns the Desktop directory for the current user, or null if it doesn't exist.
   */
  public static @Nullable VirtualFile getDesktopDirectoryVirtualFile() {
    Path desktop = getDesktopDirectoryIfExists();
    return desktop == null ? null : LocalFileSystem.getInstance().refreshAndFindFileByIoFile(desktop.toFile());
  }
}
