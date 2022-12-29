// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.fonts;

import com.google.common.util.concurrent.AtomicDouble;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.io.HttpRequests;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is a copy of FileDownloaderImpl with a change in downloadFile to attempt
 * to download again without using compression when a download error happens.
 * This is a temporary fix until we found and fixed the problem with compressed
 * content. (It may be a bug in NetUtils or it may be a bug on the server).
 */
public class FontFileDownloader {
  private static final Logger LOG = Logger.getInstance(FontFileDownloader.class);
  private static final String LIB_SCHEMA = "lib://";

  private final List<? extends DownloadableFileDescription> myFileDescriptions;

  public FontFileDownloader(@NotNull List<? extends DownloadableFileDescription> fileDescriptions) {
    myFileDescriptions = fileDescriptions;
  }

  @NotNull
  public List<Pair<File, DownloadableFileDescription>> download(@NotNull final File targetDir) throws IOException {
    List<Pair<File, DownloadableFileDescription>> downloadedFiles = Collections.synchronizedList(new ArrayList<>());
    List<Pair<File, DownloadableFileDescription>> existingFiles = Collections.synchronizedList(new ArrayList<>());
    ProgressIndicator parentIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (parentIndicator == null) {
      parentIndicator = new EmptyProgressIndicator();
    }

    try {
      final ConcurrentTasksProgressManager progressManager = new ConcurrentTasksProgressManager(parentIndicator, myFileDescriptions.size());
      parentIndicator.setText(IdeBundle.message("progress.downloading.0.files.text", myFileDescriptions.size()));
      int maxParallelDownloads = Runtime.getRuntime().availableProcessors();
      LOG.debug("Downloading " + myFileDescriptions.size() + " files using " + maxParallelDownloads + " threads");
      long start = System.currentTimeMillis();
      ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("FileDownloaderImpl pool", maxParallelDownloads);
      List<Future<Void>> results = new ArrayList<>();
      final AtomicLong totalSize = new AtomicLong();
      for (final DownloadableFileDescription description : myFileDescriptions) {
        results.add(executor.submit(() -> {
          SubTaskProgressIndicator indicator = progressManager.createSubTaskIndicator();
          indicator.checkCanceled();

          final File existing = new File(targetDir, description.getDefaultFileName());
          final String url = description.getDownloadUrl();
          if (url.startsWith(LIB_SCHEMA)) {
            final String path = FileUtilRt.toSystemDependentName(StringUtil.trimStart(url, LIB_SCHEMA));
            final File file = PathManager.findFileInLibDirectory(path);
            existingFiles.add(Pair.create(file, description));
          }
          else if (url.startsWith(LocalFileSystem.PROTOCOL_PREFIX)) {
            String path = FileUtilRt.toSystemDependentName(StringUtil.trimStart(url, LocalFileSystem.PROTOCOL_PREFIX));
            File file = new File(path);
            if (file.exists()) {
              existingFiles.add(Pair.create(file, description));
            }
          }
          else {
            File downloaded;
            try {
              downloaded = downloadFile(description, existing, indicator);
            }
            catch (IOException e) {
              throw new IOException(IdeBundle.message("error.file.download.failed", description.getDownloadUrl(),
                                                      e.getMessage()), e);
            }
            if (FileUtil.filesEqual(downloaded, existing)) {
              existingFiles.add(Pair.create(existing, description));
            }
            else {
              totalSize.addAndGet(downloaded.length());
              downloadedFiles.add(Pair.create(downloaded, description));
            }
          }
          indicator.finished();
          return null;
        }));
      }

      for (Future<Void> result : results) {
        try {
          result.get();
        }
        catch (InterruptedException e) {
          throw new ProcessCanceledException();
        }
        catch (ExecutionException e) {
          if (e.getCause() instanceof IOException) {
            throw ((IOException)e.getCause());
          }
          if (e.getCause() instanceof ProcessCanceledException) {
            throw ((ProcessCanceledException)e.getCause());
          }
          LOG.error(e);
        }
      }
      long duration = System.currentTimeMillis() - start;
      LOG.debug("Downloaded " + StringUtil.formatFileSize(totalSize.get()) + " in " + StringUtil.formatDuration(duration) + "(" + duration + "ms)");

      List<Pair<File, DownloadableFileDescription>> localFiles = new ArrayList<>();
      localFiles.addAll(moveToDir(downloadedFiles, targetDir));
      localFiles.addAll(existingFiles);
      return localFiles;
    }
    catch (ProcessCanceledException | IOException e) {
      deleteFiles(downloadedFiles);
      throw e;
    }
  }

  private static List<Pair<File, DownloadableFileDescription>> moveToDir(List<Pair<File, DownloadableFileDescription>> downloadedFiles,
                                                                         final File targetDir) throws IOException {
    FileUtil.createDirectory(targetDir);
    List<Pair<File, DownloadableFileDescription>> result = new ArrayList<>();
    for (Pair<File, DownloadableFileDescription> pair : downloadedFiles) {
      final DownloadableFileDescription description = pair.getSecond();
      final String fileName = description.generateFileName(s -> !new File(targetDir, s).exists());
      final File toFile = new File(targetDir, fileName);
      FileUtil.rename(pair.getFirst(), toFile);
      result.add(Pair.create(toFile, description));
    }
    return result;
  }

  private static void deleteFiles(final List<Pair<File, DownloadableFileDescription>> pairs) {
    for (Pair<File, DownloadableFileDescription> pair : pairs) {
      FileUtil.delete(pair.getFirst());
    }
  }

  // b/69217300
  //
  // When we are downloading fonts we are experiencing IOExceptions coming from
  //    NetUtils.copyStreamContent
  // where the compressed stream length doesn't match the content length in the header of the response.
  // The error is something like:  "Connection closed at byte 3548. Expected 3551 bytes."
  // This only happens with some of the servers responding to requests on https://fonts.gstatic.com/
  // And only with some of the fonts. It is unknown if the server content length is wrong or if there
  // is a problem in the logic in NetUtils.
  // Other servers doesn't report a content length, which will circumvent this problem.
  //
  // The fix used below is to try downloading a file again this time without compression.
  // This hopefully will also circumvent the problem.
  @NotNull
  private static File downloadFile(@NotNull final DownloadableFileDescription description,
                                   @NotNull final File existingFile,
                                   @NotNull final ProgressIndicator indicator) throws IOException {
    try {
       return downloadFile(description, existingFile, indicator, true);
    }
    catch (IOException ex) {
      return downloadFile(description, existingFile, indicator, false);
    }
  }

  @NotNull
  private static File downloadFile(@NotNull final DownloadableFileDescription description,
                                   @NotNull final File existingFile,
                                   @NotNull final ProgressIndicator indicator,
                                   boolean compressed) throws IOException {
    final String presentableUrl = description.getPresentableDownloadUrl();
    indicator.setText2(IdeBundle.message("progress.connecting.to.download.file.text", presentableUrl));
    indicator.setIndeterminate(true);

    return HttpRequests.request(description.getDownloadUrl()).gzip(compressed).connect(new HttpRequests.RequestProcessor<File>() {
      @Override
      public File process(@NotNull HttpRequests.Request request) throws IOException {
        int size = request.getConnection().getContentLength();
        if (existingFile.exists() && size == existingFile.length()) {
          return existingFile;
        }

        indicator.setText2(IdeBundle.message("progress.download.file.text", description.getPresentableFileName(), presentableUrl));
        return request.saveToFile(FileUtil.createTempFile("download.", ".tmp"), indicator);
      }
    });
  }

  private static class ConcurrentTasksProgressManager {
    private final ProgressIndicator myParent;
    private final int myTasksCount;
    private final AtomicDouble myTotalFraction;
    private final Object myLock = new Object();
    @SuppressWarnings("SSBasedInspection")
    private final Object2ObjectLinkedOpenHashMap<SubTaskProgressIndicator, String> myText2Stack = new Object2ObjectLinkedOpenHashMap<>();

    private ConcurrentTasksProgressManager(ProgressIndicator parent, int tasksCount) {
      myParent = parent;
      myTasksCount = tasksCount;
      myTotalFraction = new AtomicDouble();
    }

    public void updateFraction(double delta) {
      myTotalFraction.addAndGet(delta / myTasksCount);
      myParent.setFraction(myTotalFraction.get());
    }

    public SubTaskProgressIndicator createSubTaskIndicator() {
      return new SubTaskProgressIndicator(this);
    }

    public void setText2(@NotNull SubTaskProgressIndicator subTask, @Nullable String text) {
      if (text != null) {
        synchronized (myLock) {
          myText2Stack.put(subTask, text);
        }
        myParent.setText2(text);
      }
      else {
        String prev;
        synchronized (myLock) {
          myText2Stack.remove(subTask);
          prev = myText2Stack.isEmpty() ? null : myText2Stack.get(myText2Stack.lastKey());
        }
        if (prev != null) {
          myParent.setText2(prev);
        }
      }
    }
  }

  private static class SubTaskProgressIndicator extends SensitiveProgressWrapper {
    private final AtomicDouble myFraction;
    private final ConcurrentTasksProgressManager myProgressManager;

    private SubTaskProgressIndicator(ConcurrentTasksProgressManager progressManager) {
      super(progressManager.myParent);
      myProgressManager = progressManager;
      myFraction = new AtomicDouble();
    }

    @Override
    public void setFraction(double newValue) {
      double oldValue = myFraction.getAndSet(newValue);
      myProgressManager.updateFraction(newValue - oldValue);
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {
      if (myProgressManager.myTasksCount > 1) return;
      super.setIndeterminate(indeterminate);
    }

    @Override
    public void setText2(String text) {
      myProgressManager.setText2(this, text);
    }

    @Override
    public double getFraction() {
      return myFraction.get();
    }

    public void finished() {
      setFraction(1);
      myProgressManager.setText2(this, null);
    }
  }
}
