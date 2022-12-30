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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Helper methods for classes implementing {@link org.gradle.internal.impldep.aQute.bnd.service.repository.ResourceRepository} interface.
 */
final class ResourceRepositoryImplUtil {
  /**
   * The standard implementation of the {@link com.android.ide.common.resources.SingleNamespaceResourceRepository#getPackageName()} method.
   */
  @Nullable
  public static String getPackageName(@NotNull ResourceNamespace namespace, @NotNull AndroidFacet facet) {
    String packageName = namespace.getPackageName();
    return packageName == null ? ProjectSystemUtil.getModuleSystem(facet).getPackageName() : packageName;
  }
}
