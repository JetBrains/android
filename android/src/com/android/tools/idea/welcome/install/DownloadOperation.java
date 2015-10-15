/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.install;

import com.android.tools.idea.welcome.wizard.WelcomeUIUtils;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

/**
 * Downloads files needed to setup Android Studio.
 */
public final class DownloadOperation extends InstallOperation<File, File> {
  @NotNull private final String myUrl;

  public DownloadOperation(@NotNull InstallContext context, @NotNull String url, double progressShare) {
    super(context, progressShare);
    myUrl = url;
  }

  private static void download(@NotNull String url, @NotNull File destination, @NotNull ProgressIndicator indicator) throws IOException {
    indicator.setText(String.format("Downloading %s", destination.getName()));
    HttpURLConnection connection = HttpConfigurable.getInstance().openHttpConnection(url);
    int contentLength = connection.getContentLength();
    if (contentLength <= 0) {
      indicator.setIndeterminate(true);
    }
    InputStream readStream = null;
    OutputStream stream = null;
    try {
      readStream = connection.getInputStream();
      //noinspection IOResourceOpenedButNotSafelyClosed
      stream = new BufferedOutputStream(new FileOutputStream(destination));
      byte[] buffer = new byte[2 * 1024 * 1024];
      int read;
      int totalRead = 0;
      final long startTime = System.currentTimeMillis();
      for (read = readStream.read(buffer); read > 0; read = readStream.read(buffer)) {
        totalRead += read;
        stream.write(buffer, 0, read);
        long duration = System.currentTimeMillis() - startTime; // Duration is in ms
        long downloadRate = duration == 0 ? 0 : (totalRead / duration);
        String message =
          String.format("Downloading %1$s (%2$s/s)", destination.getName(), WelcomeUIUtils.getSizeLabel(downloadRate * 1000));
        indicator.setText(message);
        if (contentLength > 0) {
          indicator.setFraction(((double)totalRead) / contentLength);
        }
        indicator.checkCanceled();
      }
    }
    finally {
      if (stream != null) {
        stream.close();
      }
      if (readStream != null) {
        readStream.close();
      }
    }
  }

  @NotNull
  private static String getFileName(@NotNull String urlString) {
    try {
      // In case we need to strip query string
      if (URLUtil.containsScheme(urlString)) {
        URL url = new URL(urlString);
        return PathUtil.getFileName(url.getPath());
      }
    }
    catch (MalformedURLException e) {
      // Ignore it
    }
    return PathUtil.getFileName(urlString);
  }

  @Override
  @NotNull
  protected File perform(@NotNull ProgressIndicator indicator, @NotNull File arg) throws WizardException, InstallationCancelledException {
    File file = new File(myContext.getTempDirectory(), getFileName(myUrl));
    myContext.print(String.format("Downloading %1$s from %2$s\n", file.getName(), myUrl), ConsoleViewContentType.SYSTEM_OUTPUT);
    indicator.start();
    try {
      //noinspection StatementWithEmptyBody
      while (!attemptDownload(file, indicator)) {
        // Nothing to do
      }
      return file;
    }
    catch (ProcessCanceledException e) {
      throw new InstallationCancelledException();
    }
  }


  private boolean attemptDownload(File file, ProgressIndicator indicator) throws WizardException {
    try {
      download(myUrl, file, indicator);
      return true;
    }
    catch (UnknownHostException e) { // Exception has most cryptic error message, containing only the host name.
      prompt(String.format("Unknown host: %s", e.getMessage()), e);
    }
    catch (IOException e) {
      String details = StringUtil.isEmpty(e.getMessage()) ? "Unable to download Android Studio components." : e.getMessage();
      prompt(details, e);
    }
    catch (ProcessCanceledException e) {
      // User cancelled the download. Catch and rethrow to prevent the RuntimeException catch block from catching it.
      throw e;
    }
    catch (RuntimeException e) {
      // "Proxy Vole" is a network layer used by Intellij.
      // This layer will throw a RuntimeException instead of an IOException on certain proxy misconfigurations.
      String details = StringUtil.isEmpty(e.getMessage()) ? "Unable to download Android Studio components." : e.getMessage();
      prompt(details, e);
    }
    return false;
  }

  private void prompt(String details, Exception e) throws WizardException {
    promptToRetry(details + "\n\nPlease check your Internet connection and retry.", details, e);
  }

  @Override
  public void cleanup(@NotNull File result) {
    if (result.isFile() && FileUtil.isAncestor(result, myContext.getTempDirectory(), false)) {
      FileUtil.delete(result);
    }
  }
}
