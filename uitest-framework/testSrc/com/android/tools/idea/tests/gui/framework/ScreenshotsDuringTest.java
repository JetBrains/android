/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework;

import com.intellij.openapi.util.io.FileUtilRt;
import org.fest.swing.image.ScreenshotTaker;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import static com.intellij.openapi.util.io.FileUtil.ensureExists;

/**
 * Rule that takes a screenshot every second.
 */
public class ScreenshotsDuringTest extends TestWatcher implements ActionListener {
  private final ScreenshotTaker myScreenshotTaker = new ScreenshotTaker();
  private final Timer myTimer = new Timer(100, this);
  private File myFolder;
  private boolean myCurrentlyTakingScreenshot;

  @Override
  protected void starting(Description description) {
    String folderName = description.getTestClass().getSimpleName() + "-" + description.getMethodName();
    try {
      myFolder = new File(IdeTestApplication.getFailedTestScreenshotDirPath(), folderName);
      ensureExists(myFolder);
    }
    catch (IOException e) {
      System.out.println("Could not create folder " + folderName);
    }
    myTimer.start();
  }

  @Override
  protected void finished(Description description) {
    myTimer.stop();
  }

  @Override
  protected void succeeded(Description description) {
    if (myFolder != null) {
      FileUtilRt.delete(myFolder);
    }
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (myCurrentlyTakingScreenshot) {
      // Do not start taking a screenshot if one is in progress, that can cause a deadlock
      return;
    }
    SwingWorker worker = new SwingWorker<Void, Object>() {
      @Override
      protected Void doInBackground() throws Exception {
        myCurrentlyTakingScreenshot = true;
        myScreenshotTaker.saveDesktopAsPng(new File(myFolder, System.currentTimeMillis() + ".png").getPath());
        myCurrentlyTakingScreenshot = false;
        return null;
      }
    };
    worker.execute();
  }
}
