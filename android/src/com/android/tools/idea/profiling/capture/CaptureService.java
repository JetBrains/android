/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.profiling.capture;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.Client;
import com.android.tools.idea.stats.UsageTracker;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * A service responsible for writing data to "capture" files and opening them with a suitable editor after the files are done writing to.
 * <p/>
 * This service operates in two modes, synchronous or asynchronous.
 * To use this service synchronously, call {@link #createCapture(Class, byte[])}.
 * To use this service asynchronously, do the following in order:
 * 1) Call {@link #startCaptureFile(Class)} on the EDT thread.
 * 2) Call {@link #appendData(CaptureHandle, byte[])} as many times as needed in any other thread (you're responsible for synchronizing the writes between your own threads), passing in the return value from {@link #startCaptureFile(Class)}.
 * 3) Call {@link #cancelCaptureFile(CaptureHandle)} if an error occurs on the caller end and wish to cancel the capture.
 * 4) Call {@link #finalizeCaptureFileAsynchronous(CaptureHandle, FutureCallback, Executor)} when done with writing.
 */
public class CaptureService {
  public static final String FD_CAPTURES = "captures";

  @NotNull private final Project myProject;
  @NotNull private Multimap<CaptureType, Capture> myCaptures;
  private List<CaptureListener> myListeners;
  @Nullable private AsyncWriterDelegate myAsyncWriterDelegate;
  @NotNull private Set<CaptureHandle> myOpenCaptureHandles;

  public CaptureService(@NotNull Project project) {
    myProject = project;
    myCaptures = LinkedListMultimap.create();
    myListeners = new LinkedList<CaptureListener>();
    myOpenCaptureHandles = new HashSet<CaptureHandle>();

    update();
  }

  @NotNull
  public static CaptureService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, CaptureService.class);
  }

  private static Set<VirtualFile> findCaptureFiles(@NotNull VirtualFile[] files, @NotNull CaptureType type) {
    Set<VirtualFile> set = new HashSet<VirtualFile>();
    for (VirtualFile file : files) {
      if (type.isValidCapture(file)) {
        set.add(file);
      }
    }
    return set;
  }

  /**
   * Returns the suggested capture name for the given project and client.
   * The returned suggested name uses the client's description if the client and description are
   * not null, otherwise the suggested name uses the project name. The suggested name is always
   * suffixed with the current data and time.
   *
   * @param client the current client.
   * @return the suggested capture name.
   */
  @NotNull
  public String getSuggestedName(@Nullable Client client) {
    String timestamp = new SimpleDateFormat("yyyy.MM.dd_HH.mm").format(new Date());
    if (client != null) {
      String name = client.getClientData().getClientDescription();
      if (name != null && name.length() > 0) {
        return name + "_" + timestamp;
      }
    }
    return myProject.getName() + "_" + timestamp;
  }

  public void update() {
    CaptureTypeService service = CaptureTypeService.getInstance();
    VirtualFile dir = getCapturesDirectory();
    Multimap<CaptureType, Capture> updated = LinkedListMultimap.create();
    if (dir != null) {
      VirtualFile[] children = VfsUtil.getChildren(dir);
      for (CaptureType type : service.getCaptureTypes()) {
        Set<VirtualFile> files = findCaptureFiles(children, type);
        for (Capture capture : myCaptures.get(type)) {
          // If an existing capture exists for a file, use it: Remove it from the files and add the already existing one.
          if (files.remove(capture.getFile())) {
            updated.put(type, capture);
          }
        }
        for (VirtualFile newFile : files) {
          updated.put(type, type.createCapture(newFile));
        }
      }
    }
    myCaptures = updated;
  }

  @NotNull
  public VirtualFile createCapturesDirectory() throws IOException {
    assert myProject.getBasePath() != null;
    VirtualFile projectDir = LocalFileSystem.getInstance().findFileByPath(myProject.getBasePath());
    if (projectDir != null) {
      VirtualFile dir = projectDir.findChild(FD_CAPTURES);
      if (dir == null) {
        dir = projectDir.createChildDirectory(null, FD_CAPTURES);
      }
      return dir;
    }
    else {
      throw new IOException("Unable to create the captures directory: Project directory not found.");
    }
  }

  @Nullable
  public VirtualFile getCapturesDirectory() {
    assert myProject.getBasePath() != null;
    VirtualFile projectDir = LocalFileSystem.getInstance().findFileByPath(myProject.getBasePath());
    return projectDir != null ? projectDir.findChild(FD_CAPTURES) : null;
  }

  @NotNull
  public Multimap<CaptureType, Capture> getCapturesByType() {
    return myCaptures;
  }

  @NotNull
  public Collection<Capture> getCaptures() {
    return myCaptures.values();
  }

  @NotNull
  public Collection<CaptureType> getTypes() {
    return myCaptures.keySet();
  }

  /**
   * Opens a capture file for asynchronous writing. Use {@link #appendData(CaptureHandle, byte[]) appendData},
   * {@link #cancelCaptureFile(CaptureHandle) finalizeCaptureOnError},
   * and {@link #finalizeCaptureFile(CaptureHandle, FutureCallback, Executor) finalizeCapture} to work with this handle.
   * <p/>
   * MUST be called on event dispatch thread.
   *
   * @param clazz the type of file file to create
   * @param name  the name of the capture file. This will be appended with a unique number if a capture with the name already exists.
   * @return the handle for working with this file asynchronously
   * @throws IOException when there is an error opening the file
   */
  public CaptureHandle startCaptureFile(@NotNull Class<? extends CaptureType> clazz, @NotNull String name) throws IOException {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myAsyncWriterDelegate == null) {
      myAsyncWriterDelegate = new AsyncWriterDelegate();
      ApplicationManager.getApplication().executeOnPooledThread(myAsyncWriterDelegate);
    }

    CaptureHandle handle = startCaptureFileSynchronous(clazz, name);
    myOpenCaptureHandles.add(handle);
    return handle;
  }

  /**
   * Copies and appends {@code data} to the backing file represented by {@code captureHandle}. Useful when {@code data} is reused by caller.
   *
   * @param captureHandle the handle returned by {@link #startCaptureFile(Class)}
   * @param data          the data to be appended to the file
   * @throws IOException when there is an error writing to the file
   */
  public void appendDataCopy(@NotNull CaptureHandle captureHandle, @NotNull byte[] data) throws IOException {
    try {
      assert myAsyncWriterDelegate != null;
      myAsyncWriterDelegate.queueWrite(captureHandle, Arrays.copyOf(data, data.length));
    }
    catch (InterruptedException ignored) {}
  }

  /**
   * Appends {@code data} to the backing file represented by {@code captureHandle}. {@code data} SHOULD NOT be modified after this.
   *
   * @param captureHandle the handle returned by {@link #startCaptureFile(Class)}
   * @param data          the data to be appended to the file
   * @throws IOException when there is an error writing to the file
   */
  public void appendData(@NotNull CaptureHandle captureHandle, @NotNull byte[] data) throws IOException {
    try {
      assert myAsyncWriterDelegate != null;
      myAsyncWriterDelegate.queueWrite(captureHandle, data);
    }
    catch (InterruptedException ignored) {}
  }

  /**
   * Cleans up and closes the file when there is an unrecoverable error on the caller side.
   * <p/>
   * MUST be called on EDT.
   *
   * @param captureHandle is the handle returned by {@link #startCaptureFile(Class)}
   */
  public void cancelCaptureFile(@NotNull final CaptureHandle captureHandle) {
    finalizeCaptureFileAsynchronous(captureHandle, null, null);
  }

  /**
   * ONLY VISIBLE FOR TESTS. Closes the file and synchronously returns the generate {@code Capture}.
   *
   * @param captureHandle is the handle returned by {@link #startCaptureFile(Class)}
   * @return the generated {@code Capture} for the given {@code CaptureHandle}
   * @throws InterruptedException if something interrupts this thread when waiting for the close to finish on the async thread
   * @throws IOException if there is a problem generating the Capture
   */
  @VisibleForTesting
  Capture finalizeCaptureFileSynchronous(@NotNull CaptureHandle captureHandle) throws InterruptedException, IOException {
    final CountDownLatch latch = new CountDownLatch(1);
    closeCaptureFileInternal(captureHandle, new Runnable() {
      @Override
      public void run() {
        latch.countDown();
      }
    });

    latch.await();
    return createCapture(captureHandle);
  }

  /**
   * Closes the file and asynchronously returns the generated {@code Capture} on the EDT within the given {@code onCompletion} callback.
   *
   * @param captureHandle is the handle returned by {@link #startCaptureFile(Class)}
   * @param onCompletion  will be called when the asynchronous closing of the file and generating the {@code Capture} is completed or error'ed
   * @param executor is the executor to run the onCompletion callbacks
   */
  public void finalizeCaptureFileAsynchronous(@NotNull final CaptureHandle captureHandle,
                                              @Nullable FutureCallback<Capture> onCompletion,
                                              @Nullable Executor executor) {
    final ListenableFutureTask<Capture> task = ListenableFutureTask.create(new Callable<Capture>() {
      @Override
      public Capture call() throws Exception {
        ApplicationManager.getApplication().assertIsDispatchThread();
        return createCapture(captureHandle);
      }
    });

    if (onCompletion != null) {
      assert executor != null;
      Futures.addCallback(task, onCompletion, executor);
    }

    closeCaptureFileInternal(captureHandle, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().invokeLater(task);
      }
    });
  }

  /**
   * captureExists checks to see if a capture with the specified name already exists.
   *
   * @param name the capture name to test for existence.
   * @return true if a capture with the specified name already exists.
   */
  public boolean captureExists(String name) throws IOException {
    VirtualFile dir = createCapturesDirectory();
    return dir.findChild(name) != null;
  }

  /**
   * Queues closing the file and perform post-close task on the async writer thread.
   * <p/>
   * MUST be called on EDT.
   *
   * @param captureHandle the handle returned by {@link #startCaptureFile(Class)}
   * @param onCompletion  the callback when this method completes both successfully and unsuccessfully, or null when no callbacks are needed
   * @param executor      the thread to execute the completion callback on, or null if and only if no callbacks are needed
   */
  private void closeCaptureFileInternal(@NotNull final CaptureHandle captureHandle, @NotNull Runnable postCloseTask) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    assert myOpenCaptureHandles.contains(captureHandle);
    assert captureHandle.isWritable();
    assert myAsyncWriterDelegate != null;

    try {
      myAsyncWriterDelegate.closeFileAndRunTaskAsynchronously(captureHandle, postCloseTask);
    }
    catch (InterruptedException ignored) {}

    myOpenCaptureHandles.remove(captureHandle);
    if (myOpenCaptureHandles.isEmpty()) {
      // Opportunistically shut down the asynchronous writer delegate.
      try {
        assert myAsyncWriterDelegate != null;
        myAsyncWriterDelegate.queueExit();
        myAsyncWriterDelegate = null;
      }
      catch (InterruptedException ignored) {}
    }
  }

  /**
   * Synchronous creation, writing, and closing of a capture file.
   *
   * @param clazz the type of file to create
   * @param data  the data to write to the file
   * @param name  the name of the capture file. This will be appended with a unique number if a capture with the name already exists.
   * @return the {@link Capture} to work with
   * @throws IOException when there is an error with opening, writing, or closing the file
   */
  @NotNull
  public Capture createCapture(Class<? extends CaptureType> clazz, byte[] data, @NotNull String name) throws IOException {
    CaptureHandle captureHandle = startCaptureFileSynchronous(clazz, name);
    try {
      appendDataSynchronous(captureHandle, data);
    }
    finally {
      captureHandle.closeFileOutputStream();
    }
    return createCapture(captureHandle);
  }

  public void addListener(@NotNull CaptureListener listener) {
    myListeners.add(listener);
  }

  /**
   * Notifies listeners of the {@link Capture} being ready, and opens the file with the appropriate editor.
   *
   * @param capture the {@link Capture} to notify listeners of and to open
   */
  public void notifyCaptureReady(@NotNull final Capture capture) {
    for (CaptureListener listener : myListeners) {
      listener.onReady(capture);
    }

    OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, capture.getFile());
    FileEditorManager.getInstance(myProject).openEditor(descriptor, true);
  }

  /**
   * Synchronously opens a new file associated with the {@link CaptureType} for writing.
   *
   * @param name the name of the capture file. This will be appended with a unique number if a capture with the name already exists.
   */
  @NotNull
  private CaptureHandle startCaptureFileSynchronous(@NotNull Class<? extends CaptureType> clazz, @Nullable final String name)
    throws IOException {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final CaptureType type = CaptureTypeService.getInstance().getType(clazz);
    assert type != null;

    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_PROFILING, UsageTracker.ACTION_PROFILING_CAPTURE, type.getName(), null);

    File file = ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<File, IOException>() {
      @Override
      public File compute() throws IOException {
        VirtualFile dir = createCapturesDirectory();
        return new File(dir.createChildData(null, getCaptureFileName(name, type.getCaptureExtension())).getPath());
      }
    });

    return new CaptureHandle(file, type);
  }

  /**
   * Returns the filename of a capture based on its name and extension.
   * If the capture file name is already taken by an existing capture then it is suffixed with a unique number.
   *
   * @param name      the capture name.
   * @param extension the capture file extension including the '.' prefix.
   * @return the unique capture file name.
   */
  @NotNull
  private String getCaptureFileName(@Nullable String name, @NotNull String extension) throws IOException {
    // Try the name unaltered.
    String filename = name + extension;
    if (!captureExists(filename)) {
      return filename;
    }
    // Name taken. Add a number suffix.
    for (int i = 1; true; i++) {
      filename = String.format("%s-%d%s", name, i, extension);
      if (!captureExists(filename)) {
        return filename;
      }
    }
  }

  /**
   * Synchronously appends to the file referenced by {@code captureHandle}.
   */
  static void appendDataSynchronous(@NotNull CaptureHandle captureHandle, @NotNull byte[] data) throws IOException {
    appendDataSynchronous(captureHandle, data, 0, data.length);
  }

  /**
   * Synchronously appends to the file referenced by {@code captureHandle}.
   */
  public static void appendDataSynchronous(@NotNull CaptureHandle captureHandle, @NotNull byte[] data, int offset, int length) throws IOException {
    FileOutputStream localFileOutputStream = captureHandle.getFileOutputStream();
    assert localFileOutputStream != null;
    localFileOutputStream.write(data, offset, length);
  }

  /**
   * Synchronously generates the {@code Capture} from the {@code captureHandle}.
   */
  @NotNull
  private Capture createCapture(@NotNull CaptureHandle captureHandle) throws IOException {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assert !captureHandle.isWritable();

    final File file = captureHandle.getFile();
    final VirtualFile vf = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        return VfsUtil.findFileByIoFile(file, true);
      }
    });
    if (vf == null) {
      throw new IOException("Cannot find virtual file for capture file \"" + file.getPath() + "\"");
    }

    CaptureType type = captureHandle.getCaptureType();

    // Attempt to find an existing Capture that symbolizes the file.
    for (Capture capture : myCaptures.get(type)) {
      if (vf.equals(capture.getFile())) {
        return capture;
      }
    }

    // If we can't find a Capture that symbolizes the file, we'll create a capture instead.
    Capture capture = type.createCapture(vf);
    myCaptures.put(type, capture);
    return capture;
  }

  public interface CaptureListener {
    void onReady(Capture capture);
  }
}

