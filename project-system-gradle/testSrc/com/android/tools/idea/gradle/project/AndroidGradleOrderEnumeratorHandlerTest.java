/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import static com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject.openPreparedTestProject;
import static com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.prepareTestProject;
import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static com.android.tools.idea.testing.AndroidProjectRuleKt.onEdt;
import static com.intellij.testFramework.UsefulTestCase.assertContainsElements;
import static com.intellij.testFramework.UsefulTestCase.assertDoesntContain;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.gradle.model.IdeAndroidArtifact;
import com.android.tools.idea.gradle.model.IdeArtifactName;
import com.android.tools.idea.gradle.model.IdeJavaArtifact;
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject;
import com.android.tools.idea.testing.AndroidModuleModelBuilder;
import com.android.tools.idea.testing.AndroidProjectBuilder;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.EdtAndroidProjectRule;
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule;
import com.android.tools.idea.testing.JavaModuleModelBuilder;
import com.google.common.collect.Collections2;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEnumerationHandler;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.testFramework.RunsInEdt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

@RunsInEdt
public class AndroidGradleOrderEnumeratorHandlerTest {
  @Rule
  public EdtAndroidProjectRule projectRule = onEdt(AndroidProjectRule.withAndroidModels());

  @Test
  public void testAndroidModulesRecursiveAndJavaModulesNot() {
    projectRule.setupProjectFrom(JavaModuleModelBuilder.getRootModuleBuilder(),
                                 new AndroidModuleModelBuilder(":app", "debug", new AndroidProjectBuilder()),
                                 new JavaModuleModelBuilder(":jav", true));

    Module appModule = gradleModule(projectRule.getProject(), ":app");
    Module libModule = gradleModule(projectRule.getProject(), ":jav");

    OrderEnumerationHandler appHandler = new AndroidGradleOrderEnumeratorHandlerFactory().createHandler(appModule);
    assertTrue(appHandler.shouldProcessDependenciesRecursively());
    assertFalse(new AndroidGradleOrderEnumeratorHandlerFactory().isApplicable(libModule));
  }
}

