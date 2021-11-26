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
package org.jetbrains.android.facet;

import static java.util.Collections.emptyList;

import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider;
import com.google.common.collect.ImmutableList;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import java.util.function.Function;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum AndroidSourceType {
  /** Manifests from all variants. */
  MANIFEST("manifest", it -> ImmutableList.copyOf(it.getManifestFiles()), AllIcons.Modules.SourceRoot),

  /** Java and Kotlin sources. */
  JAVA(
    "java",
    it -> ImmutableList.copyOf(it.getJavaDirectories()), AllIcons.Modules.SourceRoot),

  KOTLIN(
    "kotlin",
    it -> ImmutableList.copyOf(it.getKotlinDirectories()), AllIcons.Modules.SourceRoot),

  /** Generated java source folders, e.g. R, BuildConfig, and etc. */
  GENERATED_JAVA(JAVA.getName(), null, AllIcons.Modules.GeneratedSourceRoot, true),

  /** C++ sources */
  CPP("cpp", it -> ImmutableList.of(), AllIcons.Modules.SourceRoot),

  AIDL("aidl", it -> ImmutableList.copyOf(it.getAidlDirectories()), AllIcons.Modules.SourceRoot),
  RENDERSCRIPT("renderscript", it -> ImmutableList.copyOf(it.getRenderscriptDirectories()), AllIcons.Modules.SourceRoot),
  SHADERS("shaders", it -> ImmutableList.copyOf(it.getShadersDirectories()), AllIcons.Modules.SourceRoot),
  ASSETS("assets", it -> ImmutableList.copyOf(it.getAssetsDirectories()), AllIcons.Modules.ResourcesRoot),
  JNILIBS("jniLibs", it -> ImmutableList.copyOf(it.getJniLibsDirectories()), AllIcons.Modules.ResourcesRoot),

  /** Android resources. */
  RES("res", it -> ImmutableList.copyOf(it.getResDirectories()), AllIcons.Modules.ResourcesRoot),

  /** Generated Android resources, coming from the build system model. */
  GENERATED_RES(RES.getName(), it -> ImmutableList.copyOf(it.getResDirectories()), AllIcons.Modules.ResourcesRoot, true),

  /** Java-style resources. */
  RESOURCES("resources", it -> ImmutableList.copyOf(it.getResourcesDirectories()), AllIcons.Modules.ResourcesRoot),

  /** Machine learning models. */
  ML("ml", it -> ImmutableList.copyOf(it.getMlModelsDirectories()), AllIcons.Modules.ResourcesRoot),
  ;

  private final String myName;
  private final Function<NamedIdeaSourceProvider, List<VirtualFile>> mySourceExtractor;
  private final Icon myIcon;
  private final boolean myGenerated;

  AndroidSourceType(@NotNull String name,
                    @Nullable Function<NamedIdeaSourceProvider, List<VirtualFile>> sourceExtractor,
                    @NotNull Icon icon) {
    this(name, sourceExtractor, icon, false);
  }

  AndroidSourceType(@NotNull String name,
                    @Nullable Function<NamedIdeaSourceProvider, List<VirtualFile>> sourceExtractor,
                    @NotNull Icon icon,
                    boolean generated) {
    myName = name;
    mySourceExtractor = sourceExtractor;
    myIcon = icon;
    myGenerated = generated;
  }

  public String getName() {
    return myName;
  }

  @NotNull
  public List<VirtualFile> getSources(NamedIdeaSourceProvider provider) {
    if (mySourceExtractor == null) {
      return emptyList();
    }
    List<VirtualFile> files = mySourceExtractor.apply(provider);
    return files == null ? emptyList() : files;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  public boolean isGenerated() {
    return myGenerated;
  }
}
