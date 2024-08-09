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
package com.android.tools.idea.gradle.project;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.execution.RunConfigurationProducerService;
import com.intellij.execution.junit.JUnitConfigurationProducer;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.mock.MockModule;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.ServiceContainerUtil;
import io.github.classgraph.ClassGraph;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for {@link AndroidGradleProjectStartupActivity}.
 */
public class AndroidGradleProjectStartupActivityTest {

  @Rule public AndroidProjectRule myProjectRule = AndroidProjectRule.inMemory();
  private Info myInfo;
  private AndroidGradleProjectStartupActivity myStartupActivity;
  private GradleSyncInvoker mySyncInvoker;
  private GradleSyncInvoker.Request myRequest;

  @Before
  public void setUp() throws Exception {
    Project project = myProjectRule.getProject();
    Disposable testRootDisposable = myProjectRule.getTestRootDisposable();
    mySyncInvoker = new GradleSyncInvoker.FakeInvoker() {
      @Override
      public void requestProjectSync(@NotNull Project project,
                                     @NotNull GradleSyncInvoker.Request request,
                                     @Nullable GradleSyncListener listener) {
        super.requestProjectSync(project, request, listener);
        assertThat(myRequest).isNull();
        myRequest = request;
      }
    };
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), GradleSyncInvoker.class, mySyncInvoker, testRootDisposable);
    myInfo = mock(Info.class);
    ServiceContainerUtil.replaceService(project, Info.class, myInfo, testRootDisposable);

    myStartupActivity = new AndroidGradleProjectStartupActivity();
  }

  @After
  public void tearDown() {
    myInfo = null;
  }

  @Test
  public void testRunActivityWithImportedProject() {
    // this test only works in AndroidStudio due to a number of isAndroidStudio checks inside AndroidGradleProjectStartupActivity
    if (!IdeInfo.getInstance().isAndroidStudio()) return;

    when(myInfo.isBuildWithGradle()).thenReturn(true);

    Project project = myProjectRule.getProject();

    try {
      BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
                             (scope, continuation) -> myStartupActivity.execute(project, continuation));
    }
    catch(InterruptedException ignored) { }

    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_PROJECT_REOPEN);
    assertThat(myRequest).isEqualTo(request);
  }

  @Test
  public void testRunActivityWithExistingGradleProject() {
    when(myInfo.isBuildWithGradle()).thenReturn(true);
    when(myInfo.getAndroidModules()).thenReturn(Collections.singletonList(new MockModule(myProjectRule.getTestRootDisposable())));

    Project project = myProjectRule.getProject();
    try {
      BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
                             (scope, continuation) -> myStartupActivity.execute(project, continuation));
    }
    catch(InterruptedException ignored) { }

    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_PROJECT_REOPEN);
    assertThat(myRequest).isEqualTo(request);
  }

  @Test
  public void testRunActivityWithNonGradleProject() {
    when(myInfo.isBuildWithGradle()).thenReturn(false);

    Project project = myProjectRule.getProject();
    try {
      BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
                             (scope, continuation) -> myStartupActivity.execute(project, continuation));
    }
    catch(InterruptedException ignored) { }

    assertThat(myRequest).isNull();
  }

  @Test
  public void testJunitProducersAreIgnored() {
    Project project = myProjectRule.getProject();
    Set<String> ignoredProducersService = RunConfigurationProducerService.getInstance(project).getState().ignoredProducers;
    assertThat(ignoredProducersService.size()).isEqualTo(4);

    Set<ClassLoader> classLoaders = PluginManager.getLoadedPlugins().stream().map(PluginDescriptor::getClassLoader).collect(Collectors.toSet());
    Class<JUnitConfigurationProducer> junitProducerClass = JUnitConfigurationProducer.class;
    ClassGraph graph = new ClassGraph();
    for (ClassLoader loader : classLoaders)
    {
      graph.addClassLoader(loader);
    }
    Set<String> jUnitProducersNames = graph.enableClassInfo()
      .scan()
      .getAllClasses()
      .stream()
      .map( n -> {
        try {
          return n.loadClass();
        } catch (Throwable e) {
          return null;
        }

      })
      .filter(Objects::nonNull)
      .filter(junitProducerClass::isAssignableFrom)
      .filter(it -> !Modifier.isAbstract(it.getModifiers()))
      .map(Class::getName)
      .collect(Collectors.toSet());
    assertThat(ignoredProducersService).containsAllIn(jUnitProducersNames);
  }
}
