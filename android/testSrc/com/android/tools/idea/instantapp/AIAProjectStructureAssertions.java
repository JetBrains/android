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
package com.android.tools.idea.instantapp;

import com.android.builder.model.AndroidAtom;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.android.builder.model.AndroidProject.*;
import static com.android.tools.idea.testing.HighlightInfos.assertFileHasNoErrors;
import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.*;

/**
 * Helper methods for checking modules in an AIA-enabled project are created correctly by a project sync.
 *
 * Note: Splits were previously called atoms, and not all the Apis have bee updated to the new terminology, hence there is some mixed
 * naming.
 */
public class AIAProjectStructureAssertions {
  /**
   * Asserts that the given module is an app module with dependencies on the given library projects and no invalid split dependencies
   */
  public static void assertModuleIsValidAIAApp(@NotNull Module module, @NotNull Collection<String> libraryDependencies) throws InterruptedException {
    assertModuleIsValidForAIA(module, libraryDependencies, ImmutableList.of(), null, PROJECT_TYPE_APP);
  }

  /**
   * Asserts that the given module is a library module with dependencies on the given library projects and no invalid split dependencies
   */
  public static void assertModuleIsValidAIALibrary(@NotNull Module module, @NotNull Collection<String> libraryDependencies) throws InterruptedException {
    assertModuleIsValidForAIA(module, libraryDependencies, ImmutableList.of(), null, PROJECT_TYPE_LIBRARY);
  }

  /**
   * Asserts that the given module is an instant app module with the given base split and dependencies on the given split projects
   */
  public static void assertModuleIsValidAIAInstantApp(@NotNull Module module,
                                                     @NotNull String baseSplitName,
                                                     @NotNull Collection<String> splitDependencies)
    throws InterruptedException {
    assertModuleIsValidForAIA(module, ImmutableList.of(), splitDependencies, baseSplitName, PROJECT_TYPE_INSTANTAPP);
  }

  /**
   * Asserts that the given module is a split module with the given base split and dependencies on the given library projects and split
   * projects
   */
  public static void assertModuleIsValidAIASplit(@NotNull Module module,
                                                @NotNull String baseSplitName,
                                                @NotNull Collection<String> libraryDependencies,
                                                @NotNull Collection<String> splitDependencies) throws InterruptedException {
    assertModuleIsValidForAIA(module, libraryDependencies, splitDependencies, baseSplitName, PROJECT_TYPE_ATOM);
  }

  /**
   * Asserts that the given module is a base split module with dependencies on the given library projects
   */
  public static void assertModuleIsValidAIABaseSplit(@NotNull Module module,
                                                    @NotNull Collection<String> libraryDependencies) throws InterruptedException {
    assertModuleIsValidForAIA(module, libraryDependencies, ImmutableList.of(), null, PROJECT_TYPE_ATOM);
  }

  private static void assertModuleIsValidForAIA(@NotNull Module module,
                                                @NotNull Collection<String> libraryDependencies,
                                                @NotNull Collection<String> splitDependencies,
                                                @Nullable String expectedBaseSplit,
                                                int moduleType) throws InterruptedException {
    AndroidModuleModel model = AndroidModuleModel.get(module);
    assertNotNull(model);
    int projectType = model.getProjectType();
    assertEquals(moduleType, projectType);

    Dependencies dependencies = model.getMainArtifact().getDependencies();
    AndroidAtom baseSplit = dependencies.getBaseAtom();
    Collection<AndroidAtom> splits = dependencies.getAtoms();
    List<String> libraries =
      dependencies.getLibraries().stream().map(AndroidLibrary::getProject).filter(Objects::nonNull).collect(Collectors.toList());

    if (expectedBaseSplit != null) {
      assertNotNull(baseSplit);
      assertEquals(expectedBaseSplit, baseSplit.getAtomName());
    }
    else {
      assertNull(baseSplit);
    }

    assertEquals(splitDependencies.size(), splits.size());
    assertTrue(splits.stream().map(AndroidAtom::getProject).allMatch(splitDependencies::contains));

    assertEquals(libraryDependencies.size(), libraries.size());
    assertTrue(libraries.stream().allMatch(libraryDependencies::contains));

    if (moduleType != PROJECT_TYPE_INSTANTAPP) {
      assertFileHasNoErrors(module.getProject(), new File(module.getName(), "/src/main/AndroidManifest.xml"));
    }
  }
}
