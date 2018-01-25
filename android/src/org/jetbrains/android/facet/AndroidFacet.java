/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.facet;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.util.GradleProjects;
import com.android.tools.idea.model.AndroidModel;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.util.List;

import static com.android.builder.model.AndroidProject.*;
import static com.android.tools.idea.AndroidPsiUtils.getModuleSafely;
import static org.jetbrains.android.util.AndroidUtils.loadDomElement;

/**
 * @author yole
 */
public class AndroidFacet extends Facet<AndroidFacetConfiguration> {
  public static final FacetTypeId<AndroidFacet> ID = new FacetTypeId<>("android");
  public static final String NAME = "Android";

  private SourceProvider myMainSourceSet;
  private IdeaSourceProvider myMainIdeaSourceSet;

  @Nullable
  public static AndroidFacet getInstance(@NotNull Module module, @NotNull IdeModifiableModelsProvider modelsProvider) {
    return modelsProvider.getModifiableFacetModel(module).getFacetByType(ID);
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull ConvertContext context) {
    return findAndroidFacet(context.getModule());
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull PsiElement element) {
    return findAndroidFacet(getModuleSafely(element));
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull DomElement element) {
    return findAndroidFacet(element.getModule());
  }

  @Nullable
  private static AndroidFacet findAndroidFacet(@Nullable Module module) {
    return module != null ? getInstance(module) : null;
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull Module module) {
    return !module.isDisposed() ? FacetManager.getInstance(module).getFacetByType(ID) : null;
  }

  public AndroidFacet(@NotNull Module module, @NotNull String name, @NotNull AndroidFacetConfiguration configuration) {
    super(getFacetType(), module, name, configuration, null);
    configuration.setFacet(this);
  }

  /**
   * Indicates whether the project requires a {@link AndroidProject} (obtained from a build system. To check if a project is a "Gradle
   * project," please use the method {@link GradleProjects#isBuildWithGradle(Project)}.
   *
   * @return {@code true} if the project has a {@code AndroidProject}; {@code false} otherwise.
   */
  public boolean requiresAndroidModel() {
    return !getProperties().ALLOW_USER_CONFIGURATION && ApkFacet.getInstance(getModule()) == null;
  }
  
  public boolean canBeDependency() {
    int projectType = getProjectType();
    return projectType == PROJECT_TYPE_LIBRARY || projectType == PROJECT_TYPE_FEATURE;
  }

  @Deprecated
  public boolean isLibraryProject() {
    return getConfiguration().isLibraryProject();
  }

  public int getProjectType() {
    return getProperties().PROJECT_TYPE;
  }

  public void setProjectType(int type) {
    getProperties().PROJECT_TYPE = type;
  }

  /**
   * Returns the main source provider for the project. For projects that are not backed by an {@link AndroidProject}, this method returns a
   * {@link SourceProvider} wrapper which provides information about the old project.
   */
  @NotNull
  public SourceProvider getMainSourceProvider() {
    AndroidModel model = getConfiguration().getModel();
    if (model != null) {
      //noinspection deprecation
      return model.getDefaultSourceProvider();
    }
    else {
      if (myMainSourceSet == null) {
        myMainSourceSet = new LegacySourceProvider(this);
      }
      return myMainSourceSet;
    }
  }

  @NotNull
  public IdeaSourceProvider getMainIdeaSourceProvider() {
    if (!requiresAndroidModel()) {
      if (myMainIdeaSourceSet == null) {
        myMainIdeaSourceSet = IdeaSourceProvider.create(this);
      }
    }
    else {
      SourceProvider mainSourceSet = getMainSourceProvider();
      if (myMainIdeaSourceSet == null || mainSourceSet != myMainSourceSet) {
        myMainIdeaSourceSet = IdeaSourceProvider.create(mainSourceSet);
      }
    }

    return myMainIdeaSourceSet;
  }

  @NotNull
  public ResourceFolderManager getResourceFolderManager() {
    return ResourceFolderManager.getInstance(this);
  }

  /**
   * @return all resource directories, in the overlay order.
   * @deprecated use getResourceFolderManager().getFolders() instead
   */
  @NotNull
  @Deprecated
  public List<VirtualFile> getAllResourceDirectories() {
    return getResourceFolderManager().getFolders();
  }

  /**
   * This returns the primary resource directory; the default location to place newly created resources etc.  This method is marked
   * deprecated since we should be gradually adding in UI to allow users to choose specific resource folders among the available flavors
   * (see {@link AndroidModuleModel#getFlavorSourceProviders()} etc).
   *
   * @return the primary resource dir, if any.
   */
  @Deprecated
  @Nullable
  public VirtualFile getPrimaryResourceDir() {
    return ResourceFolderManager.getInstance(this).getPrimaryFolder();
  }

  @Override
  public void disposeFacet() {
    getConfiguration().disposeFacet();
  }

  @Nullable
  public Manifest getManifest() {
    VirtualFile manifestFile = getMainIdeaSourceProvider().getManifestFile();
    return manifestFile != null ? loadDomElement(getModule(), manifestFile, Manifest.class) : null;
  }

  @NotNull
  public static AndroidFacetType getFacetType() {
    return (AndroidFacetType)FacetTypeRegistry.getInstance().findFacetType(ID);
  }

  @NotNull
  public JpsAndroidModuleProperties getProperties() {
    JpsAndroidModuleProperties state = getConfiguration().getState();
    assert state != null;
    return state;
  }
}
