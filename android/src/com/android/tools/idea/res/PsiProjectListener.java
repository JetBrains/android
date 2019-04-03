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

import static com.android.SdkConstants.FD_RES_RAW;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.fileTypes.FontFileType;
import com.android.tools.idea.gradle.project.sync.GradleFiles;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.Consumer;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.jetbrains.android.compiler.AndroidResourceFilesListener;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A project-wide {@link PsiTreeChangeListener} that tracks events that are potentially relevant to
 * the {@link ResourceFolderRepository}, {@link com.android.ide.common.resources.ResourceRepository}
 * and/or {@link SampleDataResourceRepository} corresponding to the file being changed.
 *
 * For {@link ResourceFolderRepository}, this is accomplished by passing the event to {{@link ResourceFolderRepository#getPsiListener()}}.
 * In the case of sample data, the event is forwarded to the project's {@link SampleDataListener}.
 *
 * All event happening on resources file are also forwarded to the {@link ResourceNotificationManager}.
 *
 * PsiProjectListener also notifies {@link EditorNotifications} when it detects that a Gradle file has been modified.
 */
public class PsiProjectListener implements PsiTreeChangeListener {
  private final ResourceFolderRegistry myRegistry;
  private SampleDataListener mySampleDataListener;
  @NotNull private final Project myProject;
  @NotNull private final ResourceNotificationManager myResourceNotificationManager;

  @NotNull
  public static PsiProjectListener getInstance(@NotNull Project project) {
    return project.getComponent(PsiProjectListener.class);
  }

  public PsiProjectListener(@NotNull Project project) {
    myProject = project;
    myResourceNotificationManager = ResourceNotificationManager.getInstance(project);
    PsiManager.getInstance(project).addPsiTreeChangeListener(this);
    myRegistry = ResourceFolderRegistry.getInstance(project);
  }

  /**
   * Registers a {@link SampleDataListener} to be notified of possibly relevant PSI events.
   * Because there should only be a single instance of {@link SampleDataListener} per project
   * (it's a project service), this method can only be called once.
   *
   * We register the listener with this method instead of doing it right away in the constructor
   * because {@link SampleDataListener} only needs to know about PSI updates if the user is working
   * with resource or activity files.
   *
   * @param sampleDataListener the project's {@link SampleDataListener}
   */
   void setSampleDataListener(SampleDataListener sampleDataListener) {
    assert mySampleDataListener == null: "SampleDataListener already set!";
    mySampleDataListener = sampleDataListener;
  }

  static boolean isRelevantFileType(@NotNull FileType fileType) {
    if (fileType == StdFileTypes.JAVA) { // fail fast for vital file type
      return false;
    }
    if (fileType == StdFileTypes.XML) {
      return true;
    }

    // TODO: ensure that only android compatible images are recognized.
    if (fileType.isBinary()) {
      return fileType == ImageFileTypeManager.getInstance().getImageFileType() ||
             fileType == FontFileType.INSTANCE;
    }

    return false;
  }

  static boolean isRelevantFile(@NotNull VirtualFile file) {
    FileType fileType = file.getFileType();
    if (fileType == StdFileTypes.JAVA) {
      return false;
    }

    if (isRelevantFileType(fileType)) {
      return true;
    } else {
      VirtualFile parent = file.getParent();
      if (parent != null) {
        String parentName = parent.getName();
        if (parentName.startsWith(FD_RES_RAW)) {
          return true;
        }
      }
    }

    return false;
  }

  static boolean isRelevantFile(@NotNull PsiFile file) {
    FileType fileType = file.getFileType();
    if (fileType == StdFileTypes.JAVA) {
      return false;
    }

    if (isRelevantFileType(fileType)) {
      return true;
    } else {
      PsiDirectory parent = file.getParent();
      if (parent != null) {
        String parentName = parent.getName();
        if (parentName.startsWith(FD_RES_RAW)) {
          return true;
        }
      }
    }

    return false;
  }


  private void dispatch(@Nullable VirtualFile file, @NotNull Consumer<PsiTreeChangeListener> invokeCallback) {
    if (file != null) {
      dispatchToRegistry(file, invokeCallback);
    }
    dispatchToResourceNotificationManager(invokeCallback);
  }

  private void dispatchToRegistry(@NotNull VirtualFile file,
                                  @NotNull Consumer<PsiTreeChangeListener> invokeCallback) {
    while (file != null) {
      ResourceFolderRegistry.CachedRepositories cached = myRegistry.getCached(file);
      if (cached != null) {
        if (cached.namespaced != null) {
          invokeCallback.consume(cached.namespaced.getPsiListener());
        }
        if (cached.nonNamespaced != null) {
          invokeCallback.consume(cached.nonNamespaced.getPsiListener());
        }
        return;
      }

      file = file.getParent();
    }
  }

  private void dispatchToResourceNotificationManager(@NotNull Consumer<PsiTreeChangeListener> invokeCallback) {
    PsiTreeChangeListener resourceNotificationPsiListener = myResourceNotificationManager.getPsiListener();
    if (resourceNotificationPsiListener != null) {
      invokeCallback.consume(resourceNotificationPsiListener);
    }
  }

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
          computeModulesToInvalidateAttributeDefs(file);
          if (isRelevantFile(file)) {
           dispatchChildAdded(event, file);
          }
        }
      } else if (child instanceof PsiDirectory) {
        PsiDirectory directory = (PsiDirectory)child;
        dispatchChildAdded(event, directory.getVirtualFile());
      }
    } else if (isRelevantFile(psiFile)) {
      dispatchChildAdded(event, psiFile.getVirtualFile());
    } else if (isGradleFileEdit(psiFile)) {
      notifyGradleEdit(psiFile);
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
      computeModulesToInvalidateAttributeDefs(psiFile.getVirtualFile());
    }

    if (psiFile == null) {
      PsiElement child = event.getChild();
      if (child instanceof PsiFile) {
        VirtualFile file = ((PsiFile)child).getVirtualFile();
        if (file != null && isRelevantFile(file)) {
          dispatchChildRemoved(event, file);
        }
      } else if (child instanceof PsiDirectory) {
        PsiDirectory directory = (PsiDirectory)child;
        if (ResourceFolderType.getFolderType(directory.getName()) != null) {
          VirtualFile file = directory.getVirtualFile();
          dispatchChildRemoved(event, file);
        }
      }
    } else if (isRelevantFile(psiFile)) {
      VirtualFile file = psiFile.getVirtualFile();
      dispatchChildRemoved(event, file);
    } else if (isGradleFileEdit(psiFile)) {
      notifyGradleEdit(psiFile);
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
        computeModulesToInvalidateAttributeDefs(file);
      }

      if (isRelevantFile(psiFile)) {
        dispatchChildReplaced(event, file);
      } else if (isGradleFileEdit(psiFile)) {
        notifyGradleEdit(psiFile);
      }

      if (mySampleDataListener != null) {
        mySampleDataListener.childReplaced(event);
      }
    } else {
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

  private boolean isGradleFileEdit(@NotNull PsiFile psiFile) {
    return GradleFiles.getInstance(myProject).isGradleFile(psiFile);
  }

  private static void notifyGradleEdit(@NotNull PsiFile psiFile) {
    EditorNotifications.getInstance(psiFile.getProject()).updateAllNotifications();
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
        computeModulesToInvalidateAttributeDefs(file);
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
    } else {
      // Change inside a file
      VirtualFile file = psiFile.getVirtualFile();
      if (file != null) {
        computeModulesToInvalidateAttributeDefs(file);
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
    if (PsiTreeChangeEvent.PROP_FILE_NAME == event.getPropertyName()) {
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
    if (PsiTreeChangeEvent.PROP_FILE_NAME == event.getPropertyName()) {
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
  private void computeModulesToInvalidateAttributeDefs(@NotNull VirtualFile file) {
    if (!AndroidResourceFilesListener.shouldScheduleUpdate(file)) {
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

