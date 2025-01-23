/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.ide.common.resources.ResourceRepository;
import com.google.common.base.Verify;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import java.util.Collection;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for light classes that only contain inner classes, like {@code R} or {@code Manifest}.
 */
public abstract class AndroidClassWithOnlyInnerClassesBase extends AndroidLightClassBase {
  private static final Logger LOG = Logger.getInstance(AndroidClassWithOnlyInnerClassesBase.class);

  @NotNull protected final CachedValue<PsiClass[]> myClassCache;
  @NotNull protected final String myShortName;
  @Nullable private final String myPackageName;

  protected AndroidClassWithOnlyInnerClassesBase(@NotNull String shortName,
                                                 @Nullable String packageName,
                                                 @NotNull PsiManager psiManager,
                                                 @NotNull Collection<String> modifiers) {
    this(shortName,
         packageName,
         psiManager,
         modifiers,
         new ContainingFileProvider.Builder(
           packageNameForContainingFile(packageName, psiManager.getProject()),
           shortName));
  }

  private AndroidClassWithOnlyInnerClassesBase(@NotNull String shortName,
                                               @Nullable String packageName,
                                               @NotNull PsiManager psiManager,
                                               @NotNull Collection<String> modifiers,
                                               @NotNull ContainingFileProvider.Builder containingFileProvider) {
    super(psiManager, modifiers, containingFileProvider);
    Project project = getProject();

    myShortName = shortName;
    myPackageName = packageName;

    myClassCache =
      CachedValuesManager.getManager(project).createCachedValue(() -> {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Recomputing inner classes of " + this.getClass());
        }
        PsiClass[] innerClasses = doGetInnerClasses();

        ModificationTracker dependencies = getInnerClassesDependencies();
        // When ResourceRepositoryManager's caches are dropped, new instances of repositories are created and the old ones
        // stop incrementing their modification count. We need to make sure the CachedValue doesn't hold on to any particular repository
        // instance and instead reads the modification count of the "current" instance.
        Verify.verify(!(dependencies instanceof ResourceRepository), "Resource repository leaked in a CachedValue.");

        return CachedValueProvider.Result.create(innerClasses, dependencies);
      });
  }

  private static String packageNameForContainingFile(String packageName, Project project) {
    if (packageName == null || !PsiNameHelper.getInstance(project).isQualifiedName(packageName)) {
      return "_";
    }
    return packageName;
  }

  @Nullable
  public String getPackageName() {
    return myPackageName;
  }

  @NotNull
  protected abstract PsiClass[] doGetInnerClasses();

  /**
   * Dependencies (as defined by {@link CachedValueProvider.Result#getDependencyItems()}) for the cached set of inner classes computed by
   * {@link #doGetInnerClasses()}.
   */
  @NotNull
  protected abstract ModificationTracker getInnerClassesDependencies();

  @NotNull
  @Override
  public PsiClass[] getInnerClasses() {
    return myClassCache.getValue();
  }

  @Override
  @NotNull
  public final String getName() {
    return myShortName;
  }

  @Override
  public TextRange getTextRange() {
    return TextRange.EMPTY_RANGE;
  }
}
