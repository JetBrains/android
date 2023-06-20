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
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.augment.AndroidLightField;
import org.jetbrains.android.augment.ResourceRepositoryInnerRClass;
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
    @NotNull Transitivity getTransitivity();
    @NotNull
    StudioResourceRepositoryManager getResourceRepositoryManager();
    @NotNull LocalResourceRepository getResourceRepository();
    @NotNull ResourceNamespace getResourceNamespace();
    @NotNull AndroidLightField.FieldModifier getFieldModifier();
  }

  public enum Transitivity {
    TRANSITIVE,
    NON_TRANSITIVE
  }

  @NotNull private final ResourcesSource mySource;

  public ResourceRepositoryRClass(@NotNull PsiManager psiManager, @NotNull ResourcesSource source) {
    // TODO(b/110188226): Update the file package name when the module's package name changes.
    super(psiManager, source.getPackageName());
    mySource = source;
  }

  @NotNull
  @Override
  protected PsiClass[] doGetInnerClasses() {
    if (DumbService.isDumb(getProject())) {
      LOG.debug("R_CLASS_AUGMENT: empty because of dumb mode");
      return PsiClass.EMPTY_ARRAY;
    }
    ResourceType[] types;
    if (mySource.getTransitivity() == Transitivity.TRANSITIVE) {
      types = mySource.getResourceRepository().getResourceTypes(mySource.getResourceNamespace()).toArray(new ResourceType[0]);
    } else {
      // For Non-Transitive Classes we want to show every resource type for ModuleRClasses, so that we can recommend resource fields from
      // other R classes.
      types = ResourceType.values();
    }
    List<PsiClass> result = new ArrayList<>();

    for (ResourceType type : types) {
      if (type.getHasInnerClass()) {
        result.add(new ResourceRepositoryInnerRClass(type, mySource, this));
      }
    }
    LOG.debug("R_CLASS_AUGMENT: " + result.size() + " classes added");
    return result.toArray(PsiClass.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  protected ModificationTracker getInnerClassesDependencies() {
    return () -> mySource.getResourceRepository().getModificationCount();
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
