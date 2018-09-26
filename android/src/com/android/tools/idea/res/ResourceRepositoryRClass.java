/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.augment.ResourceRepositoryInnerRClass;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Represents a dynamic "class R" for resources in a {@link ResourceRepository}. */
public abstract class ResourceRepositoryRClass extends AndroidRClassBase {
  private static final Logger LOG = Logger.getInstance(ResourceRepositoryRClass.class);

  /**
   * Determines the package name, where the resources should come from and which namespace is used to find them in the repository.
   */
  public interface ResourcesSource {
    @Nullable String getPackageName();
    @NotNull LocalResourceRepository getResourceRepository();
    @NotNull ResourceNamespace getResourceNamespace();
  }

  @NotNull protected final Module myModule;
  @NotNull private final ResourcesSource mySource;

  public ResourceRepositoryRClass(@NotNull PsiManager psiManager, @NotNull Module module, @NotNull ResourcesSource source) {
    // TODO(b/110188226): Update the file package name when the module's package name changes.
    super(psiManager, source.getPackageName());
    mySource = source;
    myModule = module;
    this.putUserData(ModuleUtilCore.KEY_MODULE, module);
    // Some scenarios move up to the file level and then attempt to get the module from the file.
    myFile.putUserData(ModuleUtilCore.KEY_MODULE, module);
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  @Override
  protected PsiClass[] doGetInnerClasses() {
    if (DumbService.isDumb(getProject())) {
      LOG.debug("R_CLASS_AUGMENT: empty because of dumb mode");
      return PsiClass.EMPTY_ARRAY;
    }

    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    if (facet == null) {
      LOG.debug("R_CLASS_AUGMENT: empty because no facet");
      return PsiClass.EMPTY_ARRAY;
    }

    Set<ResourceType> types = mySource.getResourceRepository().getResourceTypes(mySource.getResourceNamespace());
    List<PsiClass> result = new ArrayList<>();

    for (ResourceType type : types) {
      if (type.getHasInnerClass()) {
        result.add(new ResourceRepositoryInnerRClass(facet, type, mySource, this));
      }
    }
    LOG.debug("R_CLASS_AUGMENT: " + result.size() + " classes added");
    return result.toArray(PsiClass.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  protected Object[] getInnerClassesDependencies() {
    return new Object[]{mySource.getResourceRepository()};
  }

  /**
   * {@inheritDoc}
   *
   * For {@link ResourceRepositoryRClass} this is the package name specified in the module's manifest.
   */
  @Nullable
  @Override
  public String getQualifiedName() {
    String packageName = mySource.getPackageName();
    return packageName == null ? SdkConstants.R_CLASS : packageName + "." + SdkConstants.R_CLASS;
  }
}
