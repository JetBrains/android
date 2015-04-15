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
package com.android.tools.idea.gradle;

import com.android.tools.idea.gradle.util.ProxyUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaSourceDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;
import org.jetbrains.plugins.gradle.model.ExtIdeaContentRoot;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;

import java.io.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.gradle.facet.JavaGradleFacet.COMPILE_JAVA_TASK_NAME;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static java.util.Collections.emptyList;

public class IdeaJavaProject implements Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 2L;

  @NotNull private String myModuleName;
  @NotNull private Collection<? extends IdeaContentRoot> myContentRoots;
  @NotNull private List<? extends IdeaDependency> myDependencies;
  @NotNull private List<? extends IdeaDependency> myDependencyProxies;

  @Nullable private Map<String, Set<File>> myArtifactsByConfiguration;
  @Nullable private ExtIdeaCompilerOutput myCompilerOutput;
  @Nullable private File myBuildFolderPath;

  private boolean myBuildable;

  @NotNull
  public static IdeaJavaProject newJavaProject(@NotNull final IdeaModule ideaModule, @Nullable ModuleExtendedModel extendedModel) {
    Collection<? extends IdeaContentRoot> contentRoots = getContentRoots(ideaModule, extendedModel);
    Map<String, Set<File>> artifactsByConfiguration = Maps.newHashMap();
    if (extendedModel != null) {
      artifactsByConfiguration = extendedModel.getArtifactsByConfiguration();
    }
    ExtIdeaCompilerOutput compilerOutput = extendedModel != null ? extendedModel.getCompilerOutput() : null;
    File buildFolderPath = ideaModule.getGradleProject().getBuildDirectory();
    boolean buildable = isBuildable(ideaModule);
    return new IdeaJavaProject(ideaModule.getName(), contentRoots, getDependencies(ideaModule), artifactsByConfiguration, compilerOutput,
                               buildFolderPath, buildable);
  }

  @NotNull
  private static Collection<? extends IdeaContentRoot> getContentRoots(@NotNull IdeaModule ideaModule,
                                                                       @Nullable ModuleExtendedModel extendedModel) {
    Collection<? extends IdeaContentRoot> contentRoots = extendedModel != null ? extendedModel.getContentRoots() : null;
    if (contentRoots != null) {
      return contentRoots;
    }
    contentRoots = ideaModule.getContentRoots();
    if (contentRoots != null) {
      return contentRoots;
    }
    return emptyList();
  }

  @NotNull
  private static List<? extends IdeaDependency> getDependencies(@NotNull IdeaModule ideaModule) {
    List<? extends IdeaDependency> dependencies = ideaModule.getDependencies().getAll();
    if (dependencies != null) {
      return dependencies;
    }
    return emptyList();
  }

  private static boolean isBuildable(@NotNull IdeaModule ideaModule) {
    for (GradleTask task : ideaModule.getGradleProject().getTasks()) {
      if (COMPILE_JAVA_TASK_NAME.equals(task.getName())) {
        return true;
      }
    }
    return false;
  }

  public IdeaJavaProject(@NotNull String name,
                         @NotNull Collection<? extends IdeaContentRoot> contentRoots,
                         @NotNull List<? extends IdeaDependency> dependencies,
                         @Nullable Map<String, Set<File>> artifactsByConfiguration,
                         @Nullable ExtIdeaCompilerOutput compilerOutput,
                         @Nullable File buildFolderPath,
                         boolean buildable) {
    myModuleName = name;
    myContentRoots = contentRoots;
    myDependencies = dependencies;
    List<IdeaDependency> proxies = Lists.newArrayListWithExpectedSize(dependencies.size());
    for (IdeaDependency dependency : dependencies) {
      // IdeaDependency cannot be serialized/deserialized as it is. This is a workaround.
      // See https://code.google.com/p/android/issues/detail?id=165576
      IdeaDependency proxy = ProxyUtil.reproxy(IdeaDependency.class, dependency);
      proxies.add(proxy);
    }
    myDependencyProxies = proxies;
    myArtifactsByConfiguration = artifactsByConfiguration;
    myCompilerOutput = compilerOutput;
    myBuildFolderPath = buildFolderPath;
    myBuildable = buildable;
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @NotNull
  public Collection<? extends IdeaContentRoot> getContentRoots() {
    return myContentRoots;
  }

  @NotNull
  public List<? extends IdeaDependency> getDependencies() {
    return myDependencies;
  }

  @Nullable
  public File getJarFilePath() {
    Map<String, Set<File>> artifactsByConfiguration = getArtifactsByConfiguration();
    if (artifactsByConfiguration != null) {
      Set<File> defaultArtifacts = artifactsByConfiguration.get("default");
      if (!defaultArtifacts.isEmpty()) {
        return getFirstItem(defaultArtifacts);
      }
    }
    return null;
  }

  @Nullable
  public Map<String, Set<File>> getArtifactsByConfiguration() {
    return myArtifactsByConfiguration;
  }

  @Nullable
  public ExtIdeaCompilerOutput getCompilerOutput() {
    return myCompilerOutput;
  }

  @Nullable
  public File getBuildFolderPath() {
    return myBuildFolderPath;
  }

  public boolean isBuildable() {
    return myBuildable;
  }

  public boolean containsSourceFile(@NotNull File file) {
    for (IdeaContentRoot contentRoot : getContentRoots()) {
      if (contentRoot != null) {
        if (containsFile(contentRoot, file)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean containsFile(@NotNull IdeaContentRoot contentRoot, @NotNull File file) {
    if (containsFile(contentRoot.getSourceDirectories(), file) ||
        containsFile(contentRoot.getTestDirectories(), file) ||
        containsFile(contentRoot.getGeneratedSourceDirectories(), file) ||
        containsFile(contentRoot.getGeneratedTestDirectories(), file)) {
      return true;
    }
    if (contentRoot instanceof ExtIdeaContentRoot) {
      ExtIdeaContentRoot extContentRoot = (ExtIdeaContentRoot)contentRoot;
      return containsFile(extContentRoot.getResourceDirectories(), file) || containsFile(extContentRoot.getTestResourceDirectories(), file);
    }
    return false;
  }

  private static boolean containsFile(@Nullable DomainObjectSet<? extends IdeaSourceDirectory> sourceFolders, @NotNull File file) {
    if (sourceFolders != null) {
      for (IdeaSourceDirectory folder : sourceFolders) {
        File path = folder.getDirectory();
        if (isAncestor(path, file, false)) {
          return true;
        }
      }
    }
    return false;
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeObject(myModuleName);
    out.writeObject(myContentRoots);
    out.writeObject(myArtifactsByConfiguration);
    out.writeObject(myCompilerOutput);
    out.writeObject(myBuildFolderPath);
    out.writeObject(myBuildable);
    out.writeObject(myDependencyProxies);
  }

  @SuppressWarnings("unchecked")
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    myModuleName = (String)in.readObject();
    myContentRoots = (Collection<? extends IdeaContentRoot>)in.readObject();
    myArtifactsByConfiguration = (Map<String, Set<File>>)in.readObject();
    myCompilerOutput = (ExtIdeaCompilerOutput)in.readObject();
    myBuildFolderPath = (File)in.readObject();
    myBuildable = (Boolean)in.readObject();
    myDependencies = (List<? extends IdeaDependency>)in.readObject();
    myDependencyProxies = myDependencies;
  }
}
