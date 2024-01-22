/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.intellij.android.safemode;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.ApplicationLoadListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.nio.file.Path;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import org.jetbrains.annotations.NotNull;

public class SafeMode implements ApplicationLoadListener {
  private static final Logger LOG = Logger.getInstance(SafeMode.class);

  @Override
  public void beforeApplicationLoaded(@NotNull Application application, @NotNull Path path) {
    checkSafeMode();
  }

  private void checkSafeMode() {
    if (safeModeDisabled()) {
      return;
    }

    File[] studioCrashFiles = getFiles(System.getProperty("java.io.tmpdir"), "android.studio");

    if (System.getProperty("studio.safe.mode") != null) {
      // If we entered safe mode, register a cleanup handler and return.
      registerCleanupHandler();
      return;
    }


    if (studioCrashFiles.length == 0) {
      // If there are no crash files, it means we're either starting for the first time or after a clean shutdown.
      // Create crash files to detect if we've crashed for next time.
      try {
        //noinspection ResultOfMethodCallIgnored
        File.createTempFile("android.studio", "");
      } catch (Exception e) {
        LOG.error("Unexpected error while creating safe mode crash detection file ", e);
      }
      // Register a cleanup handler and return.
      registerCleanupHandler();
      return;
    }

    // Otherwise we've found leftover crash files. Ask the user if they wish to start in safe mode.
    File[] safeModeScripts = getFiles(PathManager.getBinPath(), "studio_safe");
    if (safeModeScripts.length == 0) {
      return;
    }

    // Creating this frame so the safe mode dialog is not hidden behind the Android Studio Icon screen.
    JFrame frame = new JFrame("Safe Mode Frame");
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    frame.setVisible(true);
    String[] options = {"Safe Mode", "Cancel"};
    if (JOptionPane.showOptionDialog(frame,
                                     "Would you like to restart studio in Safe Mode?",
                                     "Crash detected! Studio did not close properly last time.",
                                     JOptionPane.DEFAULT_OPTION,
                                     JOptionPane.INFORMATION_MESSAGE,
                                     null,
                                     options,
                                     options[0]) != 0) {
      frame.dispose();
      return;
    }

    try {
      Process p = new ProcessBuilder(command(safeModeScripts[0].toString())).inheritIO().start();
      // This is needed to clear out the heap so the safe mode script can run.
      Thread inputStreamThread = new Thread(() -> {
        while (p.isAlive()) {
          try {
            p.getInputStream().readAllBytes();
            p.getErrorStream().readAllBytes();
          } catch (Exception ignored) {
          }
        }
      });

      inputStreamThread.start();
    } catch (Exception e) {
      LOG.error("Unexpected error while running safe mode ", e);
    }

    System.exit(0);
  }

  private static File[] getFiles(String root, String filter) {
    File[] files = new File(root).listFiles(file -> (file.isFile() && file.getName().contains(filter)));
    return files == null ? new File[0] : files;
  }

  private static boolean safeModeDisabled() {
    if (System.getProperty("disable.safe.mode") != null) {
      return true;
    }
    File[] files = getFiles(PathManager.getBinPath(), "disable_safe_mode");
    return files.length > 0;
  }

  private static void registerCleanupHandler() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appWillBeClosed(boolean isRestart) {
        //  SafeMode crash detection clean up
        File[] studioCrashFiles = getFiles(System.getProperty("java.io.tmpdir"), "android.studio");

        for (File f : studioCrashFiles) {
          //noinspection ResultOfMethodCallIgnored
          f.delete();
        }
      }
    });
  }

  private static String[] command(String safeModeScript) {
    return SystemInfo.isWindows ?
           new String[]{
             "C:\\Windows\\System32\\cmd.exe",
             "/c",
             safeModeScript,
           } :
           new String[]{
             "/bin/sh",
             "-x",
             safeModeScript,
           };
  }
}
