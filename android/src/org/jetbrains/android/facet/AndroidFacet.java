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

import static com.android.tools.idea.AndroidPsiUtils.getModuleSafely;
import static org.jetbrains.android.util.AndroidUtils.loadDomElement;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.apk.ApkFacet;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

/**
 * @author yole
 */
public class AndroidFacet extends Facet<AndroidFacetConfiguration> {
  public static final FacetTypeId<AndroidFacet> ID = new FacetTypeId<>("android");
  public static final String NAME = "Android";

  @Nullable
  public static AndroidFacet getInstance(@NotNull Module module, @NotNull IdeModifiableModelsProvider modelsProvider) {
    return modelsProvider.getModifiableFacetModel(module).getFacetByType(ID);
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull VirtualFile file, @NotNull Project project) {
    Module module = ModuleUtilCore.findModuleForFile(file, project);

    if (module == null) {
      return null;
    }

    return getInstance(module);
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
   * project," please use the method {@link com.android.tools.idea.gradle.project.GradleProjectInfo#isBuildWithGradle()}.
   *
   * @return {@code true} if the project has a {@code AndroidProject}; {@code false} otherwise.
   */
  public boolean requiresAndroidModel() {
    return !getProperties().ALLOW_USER_CONFIGURATION && ApkFacet.getInstance(getModule()) == null;
  }

  @NotNull
  public final SourceProvider getMainSourceProvider() {
    return SourceProviderManager.getInstance(this).getMainSourceProvider();
  }

  @NotNull
  public final IdeaSourceProvider getMainIdeaSourceProvider() {
    return SourceProviderManager.getInstance(this).getMainIdeaSourceProvider();
  }

  @Override
  public void disposeFacet() {
    getConfiguration().disposeFacet();
  }

  /**
   * @see #getManifest()
   */
  @Nullable
  public final VirtualFile getManifestFile() {
    // When opening a project, many parts of the IDE will try to read information from the manifest. If we close the project before
    // all of this finishes, we may end up creating disposable children of an already disposed facet. This is a rather hard problem in
    // general, but pretending there was no manifest terminates many code paths early.
    if (isDisposed()) {
      return null;
    }

    return getMainIdeaSourceProvider().getManifestFile();
  }

  /**
   * Creates and returns a DOM representation of the manifest. This may come with significant overhead,
   * as initializing the DOM model requires parsing the manifest. In performance-critical situations,
   * callers may want to consider getting the manifest as a {@link VirtualFile} with {@link #getManifestFile()}
   * and searching the corresponding PSI file manually.
   */
  @Nullable
  public final Manifest getManifest() {
    VirtualFile manifestFile = getManifestFile();
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
