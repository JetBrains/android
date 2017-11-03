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
package com.android.tools.idea.npw.project;

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.project.BuildSystemService;
import com.android.tools.idea.templates.Parameter;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;

/**
 * An {@link AndroidProjectPaths} instance with a name. This is essentially a replacement for
 * {@link SourceProvider} which we can safely use even before an Android project is created (which
 * is needed for project creation flows, for example)
 */
public final class AndroidSourceSet {
  @NotNull private final String myName;
  @NotNull private final AndroidProjectPaths myPaths;

  /**
   * Convenience method to get {@link AndroidSourceSet}s from the current project.
   */
  public static List<AndroidSourceSet> getSourceSets(@NotNull AndroidFacet facet, @Nullable VirtualFile targetDirectory) {
    BuildSystemService service = BuildSystemService.getInstance(facet.getModule().getProject());
    assert service != null;
    return service.getSourceSets(facet, targetDirectory);
  }

  public AndroidSourceSet(@NotNull String name, @NotNull AndroidProjectPaths paths) {
    myName = name;
    myPaths = paths;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public AndroidProjectPaths getPaths() {
    return myPaths;
  }

  /**
   * Convenient method to convert into a {@link SourceProvider} if needed. Note that this target
   * source provider has many fields stubbed out and should only be used carefully.
   *
   * TODO: Investigate getting rid of dependencies on {@link SourceProvider} in
   * {@link Parameter#validate} as this may allow us to delete this code
   */
  @NotNull
  public SourceProvider toSourceProvider() {
    return new SourceProvider() {
      @NotNull
      @Override
      public String getName() {
        return myName;
      }

      @NotNull
      @Override
      public File getManifestFile() {
        return new File(myPaths.getManifestDirectory(), ANDROID_MANIFEST_XML);
      }

      @NotNull
      @Override
      public Collection<File> getJavaDirectories() {
        File srcDirectory = myPaths.getSrcDirectory(null);
        return srcDirectory == null ? Collections.emptyList() : Collections.singleton(srcDirectory);
      }

      @NotNull
      @Override
      public Collection<File> getResourcesDirectories() {
        return Collections.emptyList();
      }

      @NotNull
      @Override
      public Collection<File> getAidlDirectories() {
        File aidlDirectory = myPaths.getAidlDirectory(null);
        return aidlDirectory == null ? Collections.emptyList() : Collections.singleton(aidlDirectory);
      }

      @NotNull
      @Override
      public Collection<File> getRenderscriptDirectories() {
        return Collections.emptyList();
      }

      @NotNull
      @Override
      public Collection<File> getCDirectories() {
        return Collections.emptyList();
      }

      @NotNull
      @Override
      public Collection<File> getCppDirectories() {
        return Collections.emptyList();
      }

      @NotNull
      @Override
      public Collection<File> getResDirectories() {
        File resDirectory = myPaths.getResDirectory();
        return resDirectory == null ? Collections.emptyList() : Collections.singleton(resDirectory);
      }

      @NotNull
      @Override
      public Collection<File> getAssetsDirectories() {
        return Collections.emptyList();
      }

      @NotNull
      @Override
      public Collection<File> getJniLibsDirectories() {
        return Collections.emptyList();
      }

      @NotNull
      @Override
      public Collection<File> getShadersDirectories() {
        return Collections.emptyList();
      }
    };
  }
}
