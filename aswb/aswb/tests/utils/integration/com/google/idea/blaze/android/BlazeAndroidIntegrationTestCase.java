/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.targetmapbuilder.NbTargetBuilder.targetMap;

import com.android.sdklib.repository.AndroidSdkHandler;
import com.google.idea.blaze.android.targetmapbuilder.NbTargetBuilder;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.BlazeSyncIntegrationTestCase;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.JdepsFileWriter;
import com.google.idea.blaze.base.sync.SyncMode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.After;
import org.junit.Rule;

/** Base class for integration tests that require an ASwB project setup. */
public class BlazeAndroidIntegrationTestCase extends BlazeSyncIntegrationTestCase {
  @Rule
  public final AndroidIntegrationTestSetupRule androidSetupRule =
      new AndroidIntegrationTestSetupRule();

  @Override
  protected final boolean isLightTestCase() {
    return false;
  }

  @Override
  protected BuildSystemName buildSystem() {
    return BuildSystemName.Bazel;
  }

  @After
  public void cleanUpAndroidSdkHandler() {
    AndroidSdkHandler.resetInstance(workspaceRoot.fileForPath(MockSdkUtil.SDK_DIR).toPath());
  }

  public void setTargetMap(NbTargetBuilder... builders) {
    TargetMap targetMap = targetMap(builders);
    setTargetMap(targetMap);
    JdepsFileWriter.writeDefaultJdepsFiles(getExecRoot(), fileSystem, targetMap);
  }

  protected void runFullBlazeSyncWithNoIssues() {
    runFullBlazeSync();
    errorCollector.assertNoIssues();
  }

  protected void runFullBlazeSyncWithExpectedIssues(String... issueMessages) {
    runFullBlazeSync();
    errorCollector.assertIssues(issueMessages);
  }

  protected void runFullBlazeSync() {
    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("full sync")
            .setSyncMode(SyncMode.FULL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build());
  }

  protected Module getModule(String moduleName) {
    Module module = ModuleManager.getInstance(getProject()).findModuleByName(moduleName);
    assertThat(module).isNotNull();
    return module;
  }

  protected Set<Module> getModules(String... moduleNames) {
    return Stream.of(moduleNames).map(this::getModule).collect(Collectors.toSet());
  }

  protected AndroidFacet getFacet(String moduleName) {
    AndroidFacet facet = AndroidFacet.getInstance(getModule(moduleName));
    assertThat(facet).isNotNull();
    return facet;
  }
}
