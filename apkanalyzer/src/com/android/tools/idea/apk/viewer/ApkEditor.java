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
package com.android.tools.idea.apk.viewer;

import static com.android.tools.idea.FileEditorUtil.DISABLE_GENERATED_FILE_NOTIFICATION_KEY;
import static com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerUtil.withChecksDisabledForSupplier;

import com.android.SdkConstants;
import com.android.tools.apk.analyzer.ApkSizeCalculator;
import com.android.tools.apk.analyzer.Archive;
import com.android.tools.apk.analyzer.ArchiveContext;
import com.android.tools.apk.analyzer.Archives;
import com.android.tools.apk.analyzer.BinaryXmlParser;
import com.android.tools.apk.analyzer.internal.ArchiveTreeNode;
import com.android.tools.idea.apk.viewer.arsc.ArscViewer;
import com.android.tools.idea.apk.viewer.dex.DexFileViewer;
import com.android.tools.idea.apk.viewer.diff.ApkDiffPanel;
import com.android.tools.idea.log.LogWrapper;
import com.android.utils.FileUtils;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.messages.MessageBusConnection;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.LayoutFocusTraversalPolicy;
import kotlin.io.FilesKt;
import kotlin.text.Charsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ApkEditor extends UserDataHolderBase implements FileEditor, ApkViewPanel.Listener {
  private final Project myProject;
  private final VirtualFile myBaseFile;
  private final VirtualFile myRoot;
  private ApkViewPanel myApkViewPanel;
  private ArchiveContext myArchiveContext;

  private final JBSplitter mySplitter;
  private ApkFileEditorComponent myCurrentEditor;

  public ApkEditor(@NotNull Project project, @NotNull VirtualFile baseFile, @NotNull VirtualFile root) {
    myProject = project;
    myBaseFile = baseFile;
    myRoot = root;

    DISABLE_GENERATED_FILE_NOTIFICATION_KEY.set(this, true);

    mySplitter = new JBSplitter(true, "android.apk.viewer", 0.62f);
    mySplitter.setName("apkViewerContainer");

    // Setup focus root for a11y purposes
    // Given that
    // 1) IdeFrameImpl sets up a custom focus traversal policy that unconditionally set the focus to the preferred component
    //    of the editor window.
    // 2) IdeFrameImpl is the default focus cycle root for editor windows
    // (see https://github.com/JetBrains/intellij-community/commit/65871b384739b52b1c0450235bc742d2ba7fb137#diff-5b11919bab177bf9ab13c335c32874be)
    //
    // We need to declare the root component of this custom editor to be a focus cycle root and
    // set up the default focus traversal policy (layout) to ensure the TAB key cycles through all
    // the components of this custom panel.
    mySplitter.setFocusCycleRoot(true);
    mySplitter.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());

    // The APK Analyzer uses a copy of the APK to open it as an Archive. It does so far two reasons:
    // 1. We don't want the editor holding a lock on an APK (applies only to Windows)
    // 2. Since an Archive creates a FileSystem under the hood, we don't want the zip file's contents
    // to change while the FileSystem is open, since this may lead to JVM crashes
    // But if we do a copy, we need to update it whenever the real file changes. So we listen to changes
    // in the VFS as long as this editor is open.
    MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        String basePath = myBaseFile.getPath();
        String contents = safeReadContents(myBaseFile.toNioPath());
        for (VFileEvent event : events) {
          if (FileUtil.pathsEqual(basePath, event.getPath())) {
            if (myBaseFile.isValid()) { // If the file is deleted, the editor is automatically closed.
              if (!contents.equals(safeReadContents(Path.of(event.getPath())))) {
                refreshApk(baseFile);
              }
            }
          }
        }
      }
    });

    refreshApk(myBaseFile);
    mySplitter.setSecondComponent(new JPanel());
  }

  private static String safeReadContents(Path path) {
    try {
      return Files.readString(path);
    }
    catch (IOException e) {
      return "";
    }
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(ApkEditor.class);
  }

  private void refreshApk(@NotNull VirtualFile apkVirtualFile) {
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Reading APK contents") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        disposeArchive();
        try {
          // This temporary copy is destroyed while disposing the archive, the disposeArchive method.
          Path copyOfApk = Files.createTempFile(apkVirtualFile.getNameWithoutExtension(), "." + apkVirtualFile.getExtension());
          FileUtils.copyFile(VfsUtilCore.virtualToIoFile(apkVirtualFile).toPath(), copyOfApk);
          myArchiveContext = Archives.open(copyOfApk, new LogWrapper(getLog()));
          // TODO(b/244771241) ApkViewPanel should be created on the UI thread
          myApkViewPanel = withChecksDisabledForSupplier(() ->
              new ApkViewPanel(ApkEditor.this.myProject, new ApkParser(myArchiveContext, ApkSizeCalculator.getDefault())));
          myApkViewPanel.setListener(ApkEditor.this);
          ApplicationManager.getApplication().invokeLater(() -> {
            mySplitter.setFirstComponent(myApkViewPanel.getContainer());
            selectionChanged(null);
          });
        }
        catch (IOException e) {
          getLog().error(e);
          disposeArchive();
          mySplitter.setFirstComponent(new JBLabel(e.toString()));
        }
      }
    });
  }

  /**
   * Changes the editor displayed based on the path selected in the tree.
   */
  @Override
  public void selectionChanged(@Nullable ArchiveTreeNode[] entries) {
    if (myCurrentEditor != null) {
      Disposer.dispose(myCurrentEditor);
      // Null out the field immediately after disposal, in case an exception is thrown later in the method.
      myCurrentEditor = null;
    }

    myCurrentEditor = getEditor(entries);
    mySplitter.setSecondComponent(myCurrentEditor.getComponent());
  }

  @Override
  public void selectApkAndCompare() {
    FileChooserDescriptor desc = new FileChooserDescriptor(true, false, false, false, false, false);
    desc.withFileFilter(file -> ApkFileSystem.EXTENSIONS.contains(file.getExtension()));
    VirtualFile file = FileChooser.chooseFile(desc, myProject, null);
    if (file == null) {
      return; // User canceled.
    }
    VirtualFile oldApk = ApkFileSystem.getInstance().getRootByLocal(file);
    assert oldApk != null;

    DialogBuilder builder = new DialogBuilder(myProject);
    builder.setTitle(oldApk.getName() + " (old) vs " + myRoot.getName() + " (new)");
    ApkDiffPanel panel = new ApkDiffPanel(oldApk, myRoot);
    builder.setCenterPanel(panel.getContainer());
    builder.setPreferredFocusComponent(panel.getPreferredFocusedComponent());
    builder.addCloseButton();
    builder.show();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return mySplitter;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myApkViewPanel == null) {
      return null;
    }
    return myApkViewPanel.getPreferredFocusedComponent();
  }

  @NotNull
  @Override
  public String getName() {
    return myBaseFile.getName();
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return myBaseFile.isValid();
  }

  @Override
  public void selectNotify() {
  }

  @Override
  public void deselectNotify() {
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myBaseFile;
  }

  @Override
  public void dispose() {
    if (myCurrentEditor != null) {
      Disposer.dispose(myCurrentEditor);
      myCurrentEditor = null;
    }
    getLog().info("Disposing ApkEditor with ApkViewPanel: " + myApkViewPanel);
    disposeArchive();
  }

  private void disposeArchive() {
    if (myApkViewPanel != null){
      myApkViewPanel.clearArchive();
    }
    if (myArchiveContext != null) {
      try {
        myArchiveContext.close();
        // The archive was constructed out of a temporary file.
        Files.deleteIfExists(myArchiveContext.getArchive().getPath());
      }
      catch (IOException e) {
        getLog().warn(e);
      }
      myArchiveContext = null;
    }
  }

  @NotNull
  private ApkFileEditorComponent getEditor(@Nullable ArchiveTreeNode[] nodes) {
    if (nodes == null || nodes.length == 0) {
      return new EmptyPanel();
    }

    // Check if multiple dex files are selected and return a multiple dex viewer.
    boolean allDex = true;
    for (ArchiveTreeNode path : nodes) {
       if (!path.getData().getPath().getFileName().toString().endsWith(SdkConstants.EXT_DEX)){
        allDex = false;
        break;
      }
    }

    if (allDex){
      Path[] paths = new Path[nodes.length];
      for (int i = 0; i < nodes.length; i++) {
        paths[i] = nodes[i].getData().getPath();
      }
      return new DexFileViewer(myProject, paths, myBaseFile.getParent());
    }

    // Only one file or many files with different extensions are selected. We can only show
    // a single editor for a single filetype, so arbitrarily pick the first file:
    ArchiveTreeNode n = nodes[0];
    Path p = n.getData().getPath();
    Path fileName = p.getFileName();
    if ("resources.arsc".equals(fileName.toString())) {
      byte[] arscContent;
      try {
        arscContent = Files.readAllBytes(p);
      }
      catch (IOException e) {
        return new EmptyPanel();
      }
      return new ArscViewer(arscContent);
    }

    if (p.toString().endsWith(SdkConstants.EXT_DEX)) {
      return new DexFileViewer(myProject, new Path[]{p}, myBaseFile.getParent());
    }

    // Attempting to view these kinds of files is going to trigger the Kotlin metadata decompilers, which all assume the .class files
    // accompanying them can be found next to them. But in our case the class files have been dexed, so the Kotlin compiler backend is going
    // to attempt code generation, and that will fail with some rather cryptic errors.
    if (p.toString().endsWith("kotlin_builtins") || p.toString().endsWith("kotlin_metadata")) {
      return new EmptyPanel();
    }

    VirtualFile file = createVirtualFile(n.getData().getArchive(), p);
    Optional<FileEditorProvider> providers = getFileEditorProviders(file);
    if (!providers.isPresent()) {
      return new EmptyPanel();
    }
    else if (file != null) {
      FileEditor editor = providers.get().createEditor(myProject, file);
      return new ApkFileEditorComponent() {
        @NotNull
        @Override
        public JComponent getComponent() {
          return editor.getComponent();
        }

        @Override
        public void dispose() {
          Disposer.dispose(editor);
        }
      };
    } else {
      return new EmptyPanel();
    }
  }

  @Nullable
  private VirtualFile createVirtualFile(@NotNull Archive archive, @NotNull Path p) {
    Path name = p.getFileName();
    if (name == null) {
      return null;
    }

    // No virtual file for directories.
    if (Files.isDirectory(p)) {
      return null;
    }

    // Read file contents and decode it.
    byte[] content;
    try {
      content = Files.readAllBytes(p);
    }
    catch (IOException e) {
      getLog().warn(String.format("Error loading entry \"%s\" from archive", p), e);
      return null;
    }

    if (archive.isBinaryXml(p, content)) {
      content = BinaryXmlParser.decodeXml(content);
      return ApkVirtualFile.create(p, content);
    }

    if (archive.isProtoXml(p, content)) {
      try {
        ProtoXmlPrettyPrinter prettyPrinter = new ProtoXmlPrettyPrinterImpl();
        String text = prettyPrinter.prettyPrint(content);
        return ApkVirtualFile.createText(p, text);
      }
      catch (IOException e) {
        // Ignore error, show encoded content.
        getLog().warn(String.format("Error decoding XML entry \"%s\" from archive", p), e);
      }
      return ApkVirtualFile.create(p, content);
    }

    if (archive.isBaselineProfile(p, content)) {
      String text = getPrettyPrintedBaseline(myBaseFile, content, p, FileUtilRt.LARGE_FOR_CONTENT_LOADING);
      if (text != null) {
        return ApkVirtualFile.createText(p, text);
      }
      else {
        return ApkVirtualFile.create(p, content);
      }
    }

    VirtualFile file = JarFileSystem.getInstance().findLocalVirtualFileByPath(archive.getPath().toString());
    if (file != null) {
      return file.findFileByRelativePath(p.toString());
    }
    else {
      return ApkVirtualFile.create(p, content);
    }
  }

  @Nullable
  public static String getPrettyPrintedBaseline(@NotNull VirtualFile basefile, byte[] content, @NotNull Path path, int maxChars) {
    String text;
    try {
      text = BaselineProfilePrettyPrinter.prettyPrint(basefile, path, content);
    }
    catch (IOException e) {
      getLog().warn(String.format("Error decoding baseline entry \"%s\" from archive", path), e);
      return null;
    }
    if (text.length() > maxChars) {
      StringBuilder truncated = new StringBuilder(100000);
      int length = text.getBytes(Charsets.UTF_8).length;
      truncated.append(
          "The contents of this baseline file is too large to show by default.\n" +
          "You can increase the maximum buffer size by setting the property\n" +
          "    idea.max.content.load.filesize=")
        .append(length)
        .append(
          "\n" +
          "(or higher)\n\n");

      try {
        File file = File.createTempFile("baseline", ".txt");
        FilesKt.writeText(file, text, Charsets.UTF_8);
        truncated.append(
            "Alternatively, the full contents have been written to the following\n" + "temp file:\n")
          .append(file.getPath())
          .append("\n\n");
      }
      catch (IOException ignore) {
        // Couldn't write temp file -- oh well, we just aren't including a mention of it.
      }

      // The header has taken up around 380 characters. It varies slightly, based on the path
      // to the temporary file. We want to make the test output stable (such that the "truncated X chars"
      // string doesn't vary from run to run) so we'll round up to say 450, instead of using
      // truncated.length() here.
      int truncateAt = Math.max(0, maxChars - 450);
      truncated.append(text, 0, truncateAt).append("\n....truncated ").append(length - truncateAt).append(" characters.");

      return truncated.toString();
    }

    return text;
  }

  @NotNull
  private Optional<FileEditorProvider> getFileEditorProviders(@Nullable VirtualFile file) {
    if (file == null || file.isDirectory()) {
      return Optional.empty();
    }

    List<FileEditorProvider> providers = FileEditorProviderManager.getInstance().getProviderList(myProject, file);

    // Skip 9 patch editor since nine patch information has been stripped out.
    return providers.stream().filter(
      fileEditorProvider -> !fileEditorProvider.getClass().getName().equals("com.android.tools.idea.editors.NinePatchEditorProvider")).findFirst();
  }
}
