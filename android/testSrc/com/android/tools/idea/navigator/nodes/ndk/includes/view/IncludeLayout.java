/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.ndk.includes.view;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeFile;
import com.android.builder.model.NativeFolder;
import com.android.builder.model.NativeSettings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.containers.hash.HashMap;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

/**
 * Test utility class to synthesize a particular layout of include folders.
 */
public class IncludeLayout {
  @NotNull private Map<String, NativeSettings> settings = new HashMap<>();
  @NotNull private List<NativeArtifact> artifacts = new ArrayList<>();
  @NotNull private Multimap<String, String> artifactCompilerFlags = ArrayListMultimap.create();
  @NotNull List<File> filesCreated = new ArrayList<>();
  @NotNull List<File> sourceFilesCreated = new ArrayList<>();
  @NotNull List<File> headerFilesCreated = new ArrayList<>();
  @NotNull List<File> extraFilesCreated = new ArrayList<>();
  @NotNull private final File root;
  @NotNull private final File includesRoot;
  @NotNull private final File sourcesRoot;

  public IncludeLayout() throws IOException {
    this.root = Files.createTempDirectory(getCallingMethodName()).toFile();
    this.includesRoot = new File(this.root, "includes");
    this.sourcesRoot = new File(this.root, "sources");
    createDirs(this.includesRoot);
    createDirs(this.sourcesRoot);
  }

  /**
   * Create a local directory and at the same time also create the directory within the virtual file system
   */
  void createDirs(@NotNull File folder) {
    folder.mkdirs();
    String path = FileUtil.toSystemIndependentName(folder.getAbsolutePath());
    String normalizedPath = FileUtil.normalize(path);
    String basePath = StringUtil.startsWithChar(normalizedPath, '/') ? "/" : "";
    ManagingFS managingFS = ManagingFS.getInstance();
    LocalFileSystem vfs = LocalFileSystem.getInstance();
    NewVirtualFile root = managingFS.findRoot(basePath, vfs); // prepare
    Pair<NewVirtualFile, Iterable<String>>
      data = Pair.create(root, StringUtil.tokenize(normalizedPath.substring(basePath.length()), "/" + File.separator));
    NewVirtualFile file = data.first;
    for (String pathElement : data.second) {
      NewVirtualFile prior = file;
      file = file.refreshAndFindChild(pathElement);
      if (file == null) {
        try {
          ApplicationManager.getApplication().runWriteAction((ThrowableComputable<Boolean, Throwable>)() -> {
            prior.createChildDirectory(null, pathElement);
            return true;
          });
        }
        catch (Throwable throwable) {
          throwable.printStackTrace();
          throw new RuntimeException();
        }
        file = prior.findChild(pathElement);
      }
    }
  }

  /**
   * Create an empty local file and at the same time also create the file (and parent directories) within the virtual file system
   */
  void createFile(@NotNull File file) throws IOException {
    String basePath = file.getParent();
    String baseName = file.getName();
    createDirs(file.getParentFile());
    file.createNewFile();
    ManagingFS managingFS = ManagingFS.getInstance();
    LocalFileSystem vfs = LocalFileSystem.getInstance();
    NewVirtualFile root = managingFS.findRoot(basePath, vfs);
    NewVirtualFile child = root.refreshAndFindChild(baseName);
    assertThat(child).isNotNull();
  }

  @NotNull
  public IncludeLayout addRemoteHeaders(@NotNull String... files) throws IOException {
    headerFilesCreated.addAll(add(this.includesRoot, files));
    return this;
  }

  @NotNull
  IncludeLayout addRemoteExtraFiles(@NotNull String... files) throws IOException {
    extraFilesCreated.addAll(add(this.includesRoot, files));
    return this;
  }

  @NotNull
  IncludeLayout addLocalHeaders(@NotNull String... files) throws IOException {
    headerFilesCreated.addAll(add(this.sourcesRoot, files));
    return this;
  }

  @NotNull
  public IncludeLayout addRemoteArtifactIncludePaths(@NotNull String artifactName, @NotNull String... paths) {
    for (String path : paths) {
      artifactCompilerFlags.put(artifactName + "-settings", "-I" + new File(this.includesRoot, path));
    }
    return this;
  }

  @NotNull
  public IncludeLayout addArtifact(@NotNull String artifactName, String... files) throws IOException {
    sourceFilesCreated.addAll(add(this.sourcesRoot, files));

    String settingName = artifactName + "-settings";

    List<NativeFile> nativeFiles = new ArrayList<>();
    List<NativeFolder> nativeFolders= new ArrayList<>();
    for (String file : files) {
      File sourceFile = new File(this.sourcesRoot, file);
      nativeFiles.add(new NativeFile() {
        @NotNull
        @Override
        public File getFilePath() {
          return sourceFile;
        }

        @NotNull
        @Override
        public String getSettingsName() {
          return settingName;
        }

        @Override
        public File getWorkingDirectory() {
          return sourcesRoot;
        }
      });
      createFile(sourceFile);
      nativeFolders.add(new NativeFolder() {
        @NonNull
        @Override
        public File getFolderPath() {
          return sourceFile.getParentFile();
        }

        @NonNull
        @Override
        public Map<String, String> getPerLanguageSettings() {
          throw new RuntimeException();
        }

        @Nullable
        @Override
        public File getWorkingDirectory() {
          throw new RuntimeException();
        }
      });
    }

    this.settings.put(settingName, new NativeSettings() {
      @NotNull
      @Override
      public String getName() {
        return settingName;
      }

      @NotNull
      @Override
      public List<String> getCompilerFlags() {
        return Lists.newArrayList(artifactCompilerFlags.get(settingName));
      }
    });

    NativeArtifact artifact = new NativeArtifact() {
      @NonNull
      @Override
      public String getName() {
        return artifactName;
      }

      @NonNull
      @Override
      public String getToolChain() {
        throw new RuntimeException();
      }

      @NonNull
      @Override
      public String getGroupName() {
        throw new RuntimeException();
      }

      @NonNull
      @Override
      public String getAssembleTaskName() {
        throw new RuntimeException();
      }

      @NonNull
      @Override
      public Collection<NativeFolder> getSourceFolders() {
        return nativeFolders;
      }

      @NonNull
      @Override
      public Collection<NativeFile> getSourceFiles() {
        return nativeFiles;
      }

      @NonNull
      @Override
      public Collection<File> getExportedHeaders() {
        return new ArrayList<>();
      }

      @NonNull
      @Override
      public String getAbi() {
        throw new RuntimeException();
      }

      @NonNull
      @Override
      public String getTargetName() {
        throw new RuntimeException();
      }

      @NonNull
      @Override
      public File getOutputFile() {
        throw new RuntimeException();
      }

      @NonNull
      @Override
      public Collection<File> getRuntimeFiles() {
        throw new RuntimeException();
      }
    };

    artifacts.add(artifact);
    return this;
  }

  @NotNull
  private Collection<File> add(@NotNull File root, @NotNull String... files) throws IOException {
    List<File> result = new ArrayList<>();
    for (String file : files) {
      File candidate = new File(root, file);
      if (!candidate.exists()) {
        File parent = candidate.getParentFile();
        createDirs(parent);
        FileUtils.writeStringToFile(candidate, "Created by " + IncludeLayout.class.toString());
        result.add(candidate);
        filesCreated.add(candidate);
      }
    }
    return result;
  }

  @NotNull
  File getRoot() {
    return this.root;
  }

  @NotNull
  private static String getCallingMethodName() {
    final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
    return ste[3].getMethodName();
  }

  @NotNull
  public NativeIncludes getNativeIncludes() {
    return new NativeIncludes(settings::get, artifacts);
  }

  @NotNull
  public File getRemoteRoot() {
    return this.includesRoot;
  }
}