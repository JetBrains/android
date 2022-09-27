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
package com.android.tools.idea.res;

import static com.android.SdkConstants.EXT_GRADLE_KTS;
import static com.android.SdkConstants.FD_RES_RAW;
import static com.android.SdkConstants.FN_GRADLE_PROPERTIES;
import static com.android.SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES;

import com.android.SdkConstants;
import com.android.annotations.concurrency.Slow;
import com.android.annotations.concurrency.UiThread;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.fileTypes.FontFileType;
import com.android.tools.idea.lang.aidl.AidlFileType;
import com.android.tools.idea.lang.rs.AndroidRenderscriptFileType;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.util.FileExtensions;
import com.intellij.AppTopics;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBusConnection;
import java.util.List;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.plugins.gradle.config.GradleFileType;

/**
 * Project component that tracks events that are potentially relevant to Android-specific IDE features.
 *
 * <p>It dispatches these events to other services and components. Having such a centralized dispatcher means we can reuse code written over
 * time to correctly (hopefully) handle different supported scenarios:
 *
 * <ul>
 *   <li>Files being created, deleted or moved are handled on the VFS level by a {@link BulkFileListener}.
 *   <li>Changes to files with no cached {@link Document} or binary files are handled by a {@link FileDocumentManagerListener}.
 *   <li>Changes to files with a {@link Document} but no cached {@link PsiFile} are handled by {@link DocumentListener}.
 *   <li>Changes to files with a cached {@link PsiFile} are handled by a {@link PsiTreeChangeListener}.
 * </ul>
 *
 * <p>Note that these cases are exclusive, so only one event is actually handled by the receiver, no matter what action the user took. This
 * includes cases like user typing with auto-save off (modifies Document and PSI but not VFS), background git checkouts (modifies VFS, but
 * not Document or PSI in some cases).
 *
 * <p>Information is forwarded to:
 * <ul>
 *   <li>{@link ResourceFolderRegistry} and from there to {@link ResourceFolderRepository} and {@link LayoutLibrary}
 *   <li>{@link SampleDataListener} and from there to {@link SampleDataResourceRepository}
 *   <li>{@link ResourceNotificationManager}
 *   <li>{@link EditorNotifications} when a Gradle file is modified
 * </ul>
 */
public class AndroidFileChangeListener implements Disposable {
  private ResourceFolderRegistry myRegistry;
  private Project myProject;
  private ResourceNotificationManager myResourceNotificationManager;
  private EditorNotifications myEditorNotifications;

  @Nullable private SampleDataListener mySampleDataListener;

  @NotNull
  public static AndroidFileChangeListener getInstance(@NotNull Project project) {
    return project.getService(AndroidFileChangeListener.class);
  }

  public static class MyStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      AndroidFileChangeListener listener = getInstance(project);
      listener.onProjectOpened(project);
    }
  }

  private void onProjectOpened(@NotNull Project project) {
    myProject = project;
    myResourceNotificationManager = ResourceNotificationManager.getInstance(myProject);
    myRegistry = ResourceFolderRegistry.getInstance(myProject);
    myEditorNotifications = EditorNotifications.getInstance(myProject);

    PsiManager.getInstance(myProject).addPsiTreeChangeListener(new MyPsiListener(), this);
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new MyDocumentListener(myProject, myRegistry), this);

    MessageBusConnection connection = myProject.getMessageBus().connect(this);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new MyVfsListener(myRegistry));
    connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new MyFileDocumentManagerListener(myRegistry));
  }

  @Override
  public void dispose() {}

  /**
   * Registers a {@link SampleDataListener} to be notified of possibly relevant PSI events.
   * Because there should only be a single instance of {@link SampleDataListener} per project
   * (it's a project service), this method can only be called once.
   *
   * <p>We register the listener with this method instead of doing it right away in the constructor
   * because {@link SampleDataListener} only needs to know about PSI updates if the user is working
   * with resource or activity files.
   *
   * @param sampleDataListener the project's {@link SampleDataListener}
   */
  void setSampleDataListener(SampleDataListener sampleDataListener) {
    assert mySampleDataListener == null : "SampleDataListener already set!";
    mySampleDataListener = sampleDataListener;
  }

  static boolean isRelevantFileType(@NotNull FileType fileType) {
    if (fileType == JavaFileType.INSTANCE || fileType == KotlinFileType.INSTANCE) { // fail fast for vital file type
      return false;
    }
    if (fileType == XmlFileType.INSTANCE) {
      return true;
    }

    // TODO: ensure that only android compatible images are recognized.
    if (fileType.isBinary()) {
      return fileType == ImageFileTypeManager.getInstance().getImageFileType() ||
             fileType == FontFileType.INSTANCE;
    }

    return false;
  }

  /**
   * Quickly checks if the file might be relevant based on the file extension without ever reading
   * the contents of the file. For a more accurate check use {@link #isRelevantFile(VirtualFile)}.
   */
  public static boolean isPossiblyRelevantFile(@NotNull VirtualFile file) {
    String extension = file.getExtension();
    if (StringUtil.isEmpty(extension)) {
      return false;
    }

    return !JavaFileType.INSTANCE.getDefaultExtension().equals(extension) && !KotlinFileType.EXTENSION.equals(extension);
  }

  /**
   * Checks if the file is relevant. May perform file I/O. For a faster approximate check use
   * {@link #isPossiblyRelevantFile(VirtualFile)}.
   */
  @Slow
  public static boolean isRelevantFile(@NotNull VirtualFile file) {
    // VirtualFile.getFileType will try to read from the file the first time it's
    // called so we try to avoid it as much as possible. Instead we will just
    // try to infer the type based on the extension.
    String extension = file.getExtension();
    if (StringUtil.isEmpty(extension)) {
      return false;
    }

    if (JavaFileType.DEFAULT_EXTENSION.equals(extension) || KotlinFileType.EXTENSION.equals(extension)) {
      return false;
    }

    if (XmlFileType.DEFAULT_EXTENSION.equals(extension)) {
      return true;
    }

    if (SdkConstants.FN_ANDROID_MANIFEST_XML.equals(file.getName())) {
      return true;
    }

    if (AidlFileType.DEFAULT_ASSOCIATED_EXTENSION.equals(extension)) {
      return true;
    }

    VirtualFile parent = file.getParent();
    if (parent != null) {
      String parentName = parent.getName();
      if (parentName.startsWith(FD_RES_RAW)) {
        return true;
      }
    }

    // Unable to determine based on filename, use the slow method.
    FileType fileType = file.getFileType();
    return fileType.equals(AndroidRenderscriptFileType.INSTANCE) || isRelevantFileType(fileType);
  }

  static boolean isRelevantFile(@NotNull PsiFile file) {
    FileType fileType = file.getFileType();
    if (fileType == JavaFileType.INSTANCE || fileType == KotlinFileType.INSTANCE) {
      return false;
    }

    if (isRelevantFileType(fileType)) {
      return true;
    }

    PsiDirectory parent = file.getParent();
    if (parent == null) {
      return false;
    }

    String parentName = parent.getName();
    return parentName.startsWith(FD_RES_RAW);
  }

  public static boolean isGradleFile(@NotNull PsiFile psiFile) {
    if (GradleFileType.isGradleFile(psiFile)) {
      return true;
    }
    FileType fileType = psiFile.getFileType();
    if (fileType.getName().equals("Kotlin") && psiFile.getName().endsWith(EXT_GRADLE_KTS)) {
      return true;
    }
    // Do not test getFileType() as this will differ depending on whether the toml plugin is active or not.
    if (psiFile.getName().endsWith(".versions.toml")) {
      return true;
    }
    if (fileType == PropertiesFileType.INSTANCE &&
        (FN_GRADLE_PROPERTIES.equals(psiFile.getName()) || FN_GRADLE_WRAPPER_PROPERTIES.equals(psiFile.getName()))) {
      return true;
    }

    return false;
  }

  private void dispatch(@Nullable VirtualFile file, @NotNull Consumer<PsiTreeChangeListener> invokeCallback) {
    if (file != null) {
      myRegistry.dispatchToRepositories(file, invokeCallback);
    }
    dispatchToResourceNotificationManager(invokeCallback);
  }

  private void dispatchToResourceNotificationManager(@NotNull Consumer<PsiTreeChangeListener> invokeCallback) {
    PsiTreeChangeListener resourceNotificationPsiListener = myResourceNotificationManager.getPsiListener();
    if (resourceNotificationPsiListener != null) {
      invokeCallback.consume(resourceNotificationPsiListener);
    }
  }

  /**
   * {@link BulkFileListener} which handles {@link VFileEvent}s for resource folder.
   * When an event happens on a file within a folder with a corresponding
   * {@link ResourceFolderRepository}, the event is delegated to it.
   */
  static class MyVfsListener implements BulkFileListener {

    private final ResourceFolderRegistry myRegistry;

    private MyVfsListener(@NotNull ResourceFolderRegistry registry) {
      myRegistry = registry;
    }

    @UiThread
    @Override
    public void before(@NotNull List<? extends @NotNull VFileEvent> events) {
      for (VFileEvent event : events) {
        if (event instanceof VFileMoveEvent) {
          onFileOrDirectoryRemoved(((VFileMoveEvent)event).getFile());
        }
        else if (event instanceof VFileDeleteEvent) {
          onFileOrDirectoryRemoved(((VFileDeleteEvent)event).getFile());
        }
        else if (event instanceof VFilePropertyChangeEvent &&
                 ((VFilePropertyChangeEvent)event).getPropertyName().equals(VirtualFile.PROP_NAME)) {
          onFileOrDirectoryRemoved(((VFilePropertyChangeEvent)event).getFile());
        }
      }
    }

    @Override
    public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
      for (VFileEvent event : events) {
        if (event instanceof VFileCreateEvent) {
          VFileCreateEvent createEvent = (VFileCreateEvent)event;
          onFileOrDirectoryCreated(createEvent.getParent(), createEvent.getChildName());
        }
        else if (event instanceof VFileCopyEvent) {
          VFileCopyEvent copyEvent = (VFileCopyEvent)event;
          onFileOrDirectoryCreated(copyEvent.getNewParent(), copyEvent.getNewChildName());
        }
        else if (event instanceof VFileMoveEvent) {
          VFileMoveEvent moveEvent = (VFileMoveEvent)event;
          onFileOrDirectoryCreated(moveEvent.getNewParent(), moveEvent.getFile().getName());
        }
        else if (event instanceof VFilePropertyChangeEvent &&
                 ((VFilePropertyChangeEvent)event).getPropertyName().equals(VirtualFile.PROP_NAME)) {
          VFilePropertyChangeEvent renameEvent = (VFilePropertyChangeEvent)event;
          VirtualFile parentFile = renameEvent.getFile().getParent();
          if (parentFile != null) {
            onFileOrDirectoryCreated(parentFile, (String)renameEvent.getNewValue());
          }
        }
        // VFileContentChangeEvent changes are not handled at the VFS level, but either in
        // fileWithNoDocumentChanged, documentChanged or MyPsiListener.
      }
    }

    private void onFileOrDirectoryCreated(@Nullable VirtualFile parent, @NotNull String childName) {
      ResourceUpdateTracer.log(() -> "AndroidFileChangeListener.MyVfsListener.onFileOrDirectoryCreated(" +
                                     pathForLogging(parent, childName) + ")");
      if (parent == null || !parent.exists()) {
        return;
      }
      VirtualFile created = parent.findChild(childName);
      if (created == null) {
        return;
      }

      VirtualFile resDir = created.isDirectory() ? parent : parent.getParent();
      if (resDir != null) {
        myRegistry.dispatchToRepositories(resDir, (repository, dir) -> onFileOrDirectoryCreated(created, repository));
      }
    }

    private @NotNull String pathForLogging(@Nullable VirtualFile parent, @NotNull String childName) {
      if (parent == null) {
        return childName;
      }
      return ResourceUpdateTracer.pathForLogging(FileExtensions.toPathString(parent).resolve(childName), myRegistry.getProject());
    }

    private static void onFileOrDirectoryCreated(@NotNull VirtualFile created, @Nullable ResourceFolderRepository repository) {
      if (repository == null) {
        return;
      }

      ResourceUpdateTracer.log(() -> "AndroidFileChangeListener.MyVfsListener.onFileOrDirectoryCreated(" + created + ", " +
                                     repository.getDisplayName() + ")");
      if (!created.isDirectory()) {
        repository.onFileCreated(created);
      }
      else {
        // ResourceFolderRepository doesn't handle event on a whole folder so we pass all the children.
        for (VirtualFile child : created.getChildren()) {
          if (!child.isDirectory()) {
            // There is no need to visit subdirectories because Android does not support them.
            // If a base resource directory is created (e.g res/), a whole
            // ResourceFolderRepository will be created separately so we don't need to handle
            // this case here.
            repository.onFileCreated(child);
          }
        }
      }
    }

    private void onFileOrDirectoryRemoved(@NotNull VirtualFile file) {
      myRegistry.dispatchToRepositories(file, ResourceFolderRepository::onFileOrDirectoryRemoved);
    }
  }

  static class MyFileDocumentManagerListener implements FileDocumentManagerListener {
    private final ResourceFolderRegistry myRegistry;

    private MyFileDocumentManagerListener(@NotNull ResourceFolderRegistry registry) {
      myRegistry = registry;
    }

    @Override
    public void fileWithNoDocumentChanged(@NotNull VirtualFile file) {
      myRegistry.dispatchToRepositories(file, ResourceFolderRepository::scheduleScan);
    }
  }

  static class MyDocumentListener implements DocumentListener {
    private final Project myProject;
    private final FileDocumentManager myFileDocumentManager;
    private final PsiDocumentManager myPsiDocumentManager;
    private final ResourceFolderRegistry myRegistry;

    private MyDocumentListener(@NotNull Project project, @NotNull ResourceFolderRegistry registry) {
      myProject = project;
      myPsiDocumentManager = PsiDocumentManager.getInstance(project);
      myFileDocumentManager = FileDocumentManager.getInstance();
      myRegistry = registry;
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      if (myProject.isDisposed()) {
        // note that event may arrive from any project, not only from myProject
        // myProject can be temporarily disposed in light tests
        return;
      }

      Document document = event.getDocument();
      PsiFile psiFile = myPsiDocumentManager.getCachedPsiFile(document);
      if (psiFile == null) {
        VirtualFile virtualFile = myFileDocumentManager.getFile(document);
        if (virtualFile != null) {
          runInWriteAction(() -> myRegistry.dispatchToRepositories(virtualFile, ResourceFolderRepository::scheduleScan));
        }
      }
    }

    private void runInWriteAction(@NotNull Runnable runnable) {
      Application application = ApplicationManager.getApplication();
      if (application.isWriteAccessAllowed()) {
        runnable.run();
      }
      else {
        application.invokeLater(() -> application.runWriteAction(runnable));
      }
    }
  }

  private class MyPsiListener implements PsiTreeChangeListener {

    @Override
    public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
    }

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
      PsiFile psiFile = event.getFile();
      if (psiFile == null) {
        PsiElement child = event.getChild();
        if (child instanceof PsiFile) {
          VirtualFile file = ((PsiFile)child).getVirtualFile();
          if (file != null) {
            computeModulesToInvalidateAttributeDefinitions(file);
            if (isRelevantFile(file)) {
              dispatchChildAdded(event, file);
            }
          }
        }
        else if (child instanceof PsiDirectory) {
          PsiDirectory directory = (PsiDirectory)child;
          dispatchChildAdded(event, directory.getVirtualFile());
        }
      }
      else if (isRelevantFile(psiFile)) {
        dispatchChildAdded(event, psiFile.getVirtualFile());
      }
      else if (isGradleFile(psiFile)) {
        notifyGradleEdit();
      }

      if (mySampleDataListener != null) {
        mySampleDataListener.childAdded(event);
      }
    }

    private void dispatchChildAdded(@NotNull PsiTreeChangeEvent event, @Nullable VirtualFile virtualFile) {
      dispatch(virtualFile, listener -> listener.childAdded(event));
    }

    @Override
    public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {}

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
      PsiFile psiFile = event.getFile();

      if (psiFile != null && psiFile.getVirtualFile() != null) {
        computeModulesToInvalidateAttributeDefinitions(psiFile.getVirtualFile());
      }

      if (psiFile == null) {
        PsiElement child = event.getChild();
        if (child instanceof PsiFile) {
          VirtualFile file = ((PsiFile)child).getVirtualFile();
          if (file != null && isRelevantFile(file)) {
            dispatchChildRemoved(event, file);
          }
        }
        else if (child instanceof PsiDirectory) {
          PsiDirectory directory = (PsiDirectory)child;
          if (ResourceFolderType.getFolderType(directory.getName()) != null) {
            VirtualFile file = directory.getVirtualFile();
            dispatchChildRemoved(event, file);
          }
        }
      }
      else if (isRelevantFile(psiFile)) {
        VirtualFile file = psiFile.getVirtualFile();
        dispatchChildRemoved(event, file);
      }
      else if (isGradleFile(psiFile)) {
        notifyGradleEdit();
      }

      if (mySampleDataListener != null) {
        mySampleDataListener.childRemoved(event);
      }
    }

    private void dispatchChildRemoved(@NotNull PsiTreeChangeEvent event, @Nullable VirtualFile virtualFile) {
      dispatch(virtualFile, listener -> listener.childRemoved(event));
    }

    @Override
    public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
      PsiFile psiFile = event.getFile();
      if (psiFile != null) {
        VirtualFile file = psiFile.getVirtualFile();
        if (file != null) {
          computeModulesToInvalidateAttributeDefinitions(file);
        }

        if (isRelevantFile(psiFile)) {
          dispatchChildReplaced(event, file);
        }
        else if (isGradleFile(psiFile)) {
          notifyGradleEdit();
        }

        if (mySampleDataListener != null) {
          mySampleDataListener.childReplaced(event);
        }
      }
      else {
        PsiElement parent = event.getParent();
        if (parent instanceof PsiDirectory) {
          PsiDirectory directory = (PsiDirectory)parent;
          dispatchChildReplaced(event, directory.getVirtualFile());
        }
      }
    }

    private void dispatchChildReplaced(@NotNull PsiTreeChangeEvent event, @Nullable VirtualFile virtualFile) {
      dispatch(virtualFile, listener -> listener.childReplaced(event));
    }

    private void notifyGradleEdit() {
      myEditorNotifications.updateAllNotifications();
    }

    @Override
    public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
      PsiFile psiFile = event.getFile();
      if (psiFile != null && isRelevantFile(psiFile)) {
        VirtualFile file = psiFile.getVirtualFile();
        dispatchBeforeChildrenChange(event, file);
      }
    }

    private void dispatchBeforeChildrenChange(@NotNull PsiTreeChangeEvent event, @Nullable VirtualFile virtualFile) {
      dispatch(virtualFile, listener -> listener.beforeChildrenChange(event));
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
      PsiFile psiFile = event.getFile();
      if (psiFile != null) {
        VirtualFile file = psiFile.getVirtualFile();
        if (file != null) {
          computeModulesToInvalidateAttributeDefinitions(file);
        }

        if (isRelevantFile(psiFile)) {
          dispatchChildrenChanged(event, file);
        }

        if (mySampleDataListener != null) {
          mySampleDataListener.childrenChanged(event);
        }
      }
    }

    private void dispatchChildrenChanged(@NotNull PsiTreeChangeEvent event, @Nullable VirtualFile virtualFile) {
      dispatch(virtualFile, listener -> listener.childrenChanged(event));
    }

    @Override
    public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent event) {
      PsiElement child = event.getChild();
      PsiFile psiFile = event.getFile();
      if (psiFile == null) {
        if (child instanceof PsiFile && isRelevantFile((PsiFile)child)) {
          VirtualFile file = ((PsiFile)child).getVirtualFile();
          if (file != null) {
            dispatchChildMoved(event, file);
            return;
          }

          PsiElement oldParent = event.getOldParent();
          if (oldParent instanceof PsiDirectory) {
            PsiDirectory directory = (PsiDirectory)oldParent;
            VirtualFile dir = directory.getVirtualFile();
            dispatchChildMoved(event, dir);
          }
        }
      }
      else {
        // Change inside a file
        VirtualFile file = psiFile.getVirtualFile();
        if (file != null) {
          computeModulesToInvalidateAttributeDefinitions(file);
          if (isRelevantFile(file)) {
            dispatchChildMoved(event, file);
          }

          if (mySampleDataListener != null) {
            mySampleDataListener.childMoved(event);
          }
        }
      }
    }

    private void dispatchChildMoved(@NotNull PsiTreeChangeEvent event, @Nullable VirtualFile virtualFile) {
      dispatch(virtualFile, listener -> listener.childMoved(event));

      // If you moved the file between resource directories, potentially notify that previous repository as well
      if (event.getFile() == null) {
        PsiElement oldParent = event.getOldParent();
        if (oldParent instanceof PsiDirectory) {
          PsiDirectory sourceDir = (PsiDirectory)oldParent;
          dispatch(sourceDir.getVirtualFile(), listener -> listener.childMoved(event));
        }
      }
    }

    @Override
    public void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {
      if (PsiTreeChangeEvent.PROP_FILE_NAME.equals(event.getPropertyName())) {
        PsiElement child = event.getChild();
        if (child instanceof PsiFile) {
          PsiFile psiFile = (PsiFile)child;
          if (isRelevantFile(psiFile)) {
            VirtualFile file = psiFile.getVirtualFile();
            dispatchBeforePropertyChange(event, file);
          }
        }
      }
    }

    private void dispatchBeforePropertyChange(@NotNull PsiTreeChangeEvent event, @Nullable VirtualFile virtualFile) {
      dispatch(virtualFile, listener -> listener.beforePropertyChange(event));
    }

    @Override
    public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
      if (PsiTreeChangeEvent.PROP_FILE_NAME.equals(event.getPropertyName())) {
        PsiElement child = event.getElement();
        if (child instanceof PsiFile) {
          PsiFile psiFile = (PsiFile)child;
          if (isRelevantFile(psiFile)) {
            VirtualFile file = psiFile.getVirtualFile();
            dispatchPropertyChanged(event, file);
          }
        }
      }

      // TODO: Do we need to handle PROP_DIRECTORY_NAME for users renaming any of the resource folders?
      // and what about PROP_FILE_TYPES -- can users change the type of an XML File to something else?
    }

    private void dispatchPropertyChanged(@NotNull PsiTreeChangeEvent event, @Nullable VirtualFile virtualFile) {
      dispatch(virtualFile, listener -> listener.propertyChanged(event));
    }

    /**
     * Invalidates attribute definitions of relevant modules after changes to a given file
     */
    private void computeModulesToInvalidateAttributeDefinitions(@NotNull VirtualFile file) {
      if (!isRelevantFile(file)) {
        return;
      }

      AndroidFacet facet = AndroidFacet.getInstance(file, myProject);
      if (facet != null) {
        for (Module module : AndroidUtils.getSetWithBackwardDependencies(facet.getModule())) {
          AndroidFacet moduleFacet = AndroidFacet.getInstance(module);

          if (moduleFacet != null) {
            ModuleResourceManagers.getInstance(moduleFacet).getLocalResourceManager().invalidateAttributeDefinitions();
          }
        }
      }
    }
  }
}

