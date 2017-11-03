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

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.fileTypes.FontFileType;
import com.android.tools.idea.gradle.project.sync.GradleFiles;
import com.google.common.collect.Maps;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.EditorNotifications;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.android.SdkConstants.FD_RES_RAW;

public class PsiProjectListener extends AbstractProjectComponent implements PsiTreeChangeListener {
  @NotNull private final Map<VirtualFile, ResourceFolderRepository> myListeners = Maps.newHashMap();

  public static void addRoot(@NotNull Project project, @NotNull VirtualFile root, @NotNull ResourceFolderRepository repository) {
    synchronized (PsiProjectListener.class) {
      getInstance(project).addRoot(root, repository);
    }
  }

  public static void removeRoot(@NotNull Project project, @NotNull VirtualFile root, @NotNull ResourceFolderRepository repository) {
    synchronized (PsiProjectListener.class) {
      getInstance(project).removeRoot(root, repository);
    }
  }

  @NotNull
  public static PsiProjectListener getInstance(@NotNull Project project) {
    return project.getComponent(PsiProjectListener.class);
  }

  public PsiProjectListener(@NotNull Project project) {
    super(project);
    PsiManager.getInstance(project).addPsiTreeChangeListener(this);
  }

  private void addRoot(@NotNull VirtualFile root, @NotNull ResourceFolderRepository repository) {
    assert myListeners.get(root) == null; // Repositories should be unique.
    // TODO: Walk up in the chain and make sure they aren't nested either!

    myListeners.put(root, repository);
  }

  private void removeRoot(@NotNull VirtualFile root, @NotNull ResourceFolderRepository repository) {
    assert myListeners.get(root) == repository : repository;
    myListeners.remove(root);
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


  @Nullable
  private ResourceFolderRepository findRepository(@Nullable VirtualFile file) {
    if (file == null) {
      return null;
    }
    while (file != null) {
      ResourceFolderRepository repository = myListeners.get(file);
      if (repository != null) {
        return repository;
      }

      file = file.getParent();
    }

    return null;
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
        if (file != null && isRelevantFile(file)) {
          dispatchChildAdded(event, file);
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
  }

  private void dispatchChildAdded(@NotNull PsiTreeChangeEvent event, @Nullable VirtualFile virtualFile) {
    ResourceFolderRepository repository = findRepository(virtualFile);
    if (repository != null) {
      repository.getPsiListener().childAdded(event);
    }
  }

  @Override
  public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
  }

  @Override
  public void childRemoved(@NotNull PsiTreeChangeEvent event) {
    PsiFile psiFile = event.getFile();
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
  }

  private void dispatchChildRemoved(@NotNull PsiTreeChangeEvent event, @Nullable VirtualFile virtualFile) {
    ResourceFolderRepository repository = findRepository(virtualFile);
    if (repository != null) {
      repository.getPsiListener().childRemoved(event);
    }
  }

  @Override
  public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
  }

  @Override
  public void childReplaced(@NotNull PsiTreeChangeEvent event) {
    PsiFile psiFile = event.getFile();
    if (psiFile != null) {
      if (isRelevantFile(psiFile)) {
        dispatchChildReplaced(event, psiFile.getVirtualFile());
      } else if (isGradleFileEdit(psiFile)) {
        notifyGradleEdit(psiFile);
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
    ResourceFolderRepository repository = findRepository(virtualFile);
    if (repository != null) {
      repository.getPsiListener().childReplaced(event);
    }
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
    ResourceFolderRepository repository = findRepository(virtualFile);
    if (repository != null) {
      repository.getPsiListener().beforeChildrenChange(event);
    }
  }

  @Override
  public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
    PsiFile psiFile = event.getFile();
    if (psiFile != null && isRelevantFile(psiFile)) {
      VirtualFile file = psiFile.getVirtualFile();
      dispatchChildrenChanged(event, file);
    }
  }

  private void dispatchChildrenChanged(@NotNull PsiTreeChangeEvent event, @Nullable VirtualFile virtualFile) {
    ResourceFolderRepository repository = findRepository(virtualFile);
    if (repository != null) {
      repository.getPsiListener().childrenChanged(event);
    }
  }

  @Override
  public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
  }

  private void dispatchBeforeChildMovement(@NotNull PsiTreeChangeEvent event, @Nullable VirtualFile virtualFile) {
    ResourceFolderRepository repository = findRepository(virtualFile);
    if (repository != null) {
      repository.getPsiListener().beforeChildrenChange(event);
    }
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
      if (file != null && isRelevantFile(file)) {
        dispatchChildMoved(event, file);
      }
    }
  }

  private void dispatchChildMoved(@NotNull PsiTreeChangeEvent event, @Nullable VirtualFile virtualFile) {
    ResourceFolderRepository repository = findRepository(virtualFile);
    if (repository != null) {
      repository.getPsiListener().childMoved(event);
    }

    // If you moved the file between resource directories, potentially notify that previous repository as well
    if (event.getFile() == null) {
      PsiElement oldParent = event.getOldParent();
      if (oldParent instanceof PsiDirectory) {
        PsiDirectory sourceDir = (PsiDirectory)oldParent;
        ResourceFolderRepository targetRepository = findRepository(sourceDir.getVirtualFile());
        if (targetRepository != null && targetRepository != repository) {
          targetRepository.getPsiListener().childMoved(event);
        }
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
    ResourceFolderRepository repository = findRepository(virtualFile);
    if (repository != null) {
      repository.getPsiListener().beforePropertyChange(event);
    }
  }

  @Override
  public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
    if (PsiTreeChangeEvent.PROP_FILE_NAME == event.getPropertyName()) {
      PsiElement child = event.getElement();
      if (child instanceof PsiFile) {
        PsiFile psiFile = (PsiFile)child;
        if (isRelevantFile(psiFile)) {
          VirtualFile file = psiFile.getVirtualFile();
          dispatchPropertyChange(event, file);
        }
      }
    }

    // TODO: Do we need to handle PROP_DIRECTORY_NAME for users renaming any of the resource folders?
    // and what about PROP_FILE_TYPES -- can users change the type of an XML File to something else?
  }

  private void dispatchPropertyChange(@NotNull PsiTreeChangeEvent event, @Nullable VirtualFile virtualFile) {
    ResourceFolderRepository repository = findRepository(virtualFile);
    if (repository != null) {
      repository.getPsiListener().propertyChanged(event);
    }
  }
}

