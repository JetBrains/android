/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules.RuleTypes;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.smrunner.BlazeWebTestEventsHandler;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeWebTestEventsHandler} with Java. */
@RunWith(JUnit4.class)
public class BlazeWebJavaTestEventsHandlerTest extends BlazeIntegrationTestCase {

  private BlazeWebTestEventsHandler handler;

  @Before
  public final void doSetUp() {
    handler = new BlazeWebTestEventsHandler();
  }

  @Test
  public void testSuiteLocationResolves() {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/com/google/lib:JavaClass_chrome-linux")
                    .setKind("web_test")
                    .setBuildFile(src("java/com/google/lib/BUILD"))
                    .addDependency("//java/com/google/lib:JavaClass_wrapped_test"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/com/google/lib:JavaClass_wrapped_test")
                    .setKind("java_test")
                    .setBuildFile(src("java/com/google/lib/BUILD"))
                    .addSource(src("java/com/google/lib/JavaClass.java")))
            .build();

    registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder(workspaceRoot).setTargetMap(targetMap).build()));

    PsiFile javaFile =
        workspace.createPsiFile(
            new WorkspacePath("java/com/google/lib/JavaClass.java"),
            "package com.google.lib;",
            "public class JavaClass {}");
    PsiClass javaClass = ((PsiClassOwner) javaFile).getClasses()[0];
    assertThat(javaClass).isNotNull();

    String url =
        handler.suiteLocationUrl(
            Label.create("//java/com/google/lib:JavaClass_chrome-linux"),
            RuleTypes.WEB_TEST.getKind(),
            "com.google.lib.JavaClass");
    Location<?> location = getLocation(url);
    assertThat(location).isNotNull();
    assertThat(location.getPsiElement()).isEqualTo(javaClass);
  }

  @Test
  public void testMethodLocationResolves() {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/com/google/lib:JavaClass_chrome-linux")
                    .setKind("web_test")
                    .setBuildFile(src("java/com/google/lib/BUILD"))
                    .addDependency("//java/com/google/lib:JavaClass_wrapped_test"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/com/google/lib:JavaClass_wrapped_test")
                    .setKind("java_test")
                    .setBuildFile(src("java/com/google/lib/BUILD"))
                    .addSource(src("java/com/google/lib/JavaClass.java")))
            .build();

    registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder(workspaceRoot).setTargetMap(targetMap).build()));

    PsiFile javaFile =
        workspace.createPsiFile(
            new WorkspacePath("java/com/google/lib/JavaClass.java"),
            "package com.google.lib;",
            "public class JavaClass {",
            "  public void testMethod() {}",
            "}");
    PsiClass javaClass = ((PsiClassOwner) javaFile).getClasses()[0];
    PsiMethod method = javaClass.findMethodsByName("testMethod", false)[0];
    assertThat(method).isNotNull();

    String url =
        handler.testLocationUrl(
            Label.create("//java/com/google/lib:JavaClass_chrome-linux"),
            RuleTypes.WEB_TEST.getKind(),
            null,
            "testMethod",
            "com.google.lib.JavaClass");
    Location<?> location = getLocation(url);
    assertThat(location).isNotNull();
    assertThat(location.getPsiElement()).isEqualTo(method);
  }

  @Test
  public void testParameterizedMethodLocationResolves() {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/com/google/lib:JavaClass_chrome-linux")
                    .setKind("web_test")
                    .setBuildFile(src("java/com/google/lib/BUILD"))
                    .addDependency("//java/com/google/lib:JavaClass_wrapped_test"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/com/google/lib:JavaClass_wrapped_test")
                    .setKind("java_test")
                    .setBuildFile(src("java/com/google/lib/BUILD"))
                    .addSource(src("java/com/google/lib/JavaClass.java")))
            .build();

    registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder(workspaceRoot).setTargetMap(targetMap).build()));

    PsiFile javaFile =
        workspace.createPsiFile(
            new WorkspacePath("java/com/google/lib/JavaClass.java"),
            "package com.google.lib;",
            "public class JavaClass {",
            "  public void testMethod() {}",
            "}");
    PsiClass javaClass = ((PsiClassOwner) javaFile).getClasses()[0];
    PsiMethod method = javaClass.findMethodsByName("testMethod", false)[0];
    assertThat(method).isNotNull();

    String url =
        handler.testLocationUrl(
            Label.create("//java/com/google/lib:JavaClass_chrome-linux"),
            null,
            "testMethod",
            "[0] true (testMethod)",
            "com.google.lib.JavaClass");
    Location<?> location = getLocation(url);
    assertThat(location).isNotNull();
    assertThat(location.getPsiElement()).isEqualTo(method);
  }

  @Nullable
  private Location<?> getLocation(String url) {
    String protocol = VirtualFileManager.extractProtocol(url);
    String path = VirtualFileManager.extractPath(url);
    if (protocol == null) {
      return null;
    }
    SMTestLocator locator = handler.getTestLocator();
    assertThat(locator).isNotNull();
    return Iterables.getFirst(
        locator.getLocation(protocol, path, getProject(), GlobalSearchScope.allScope(getProject())),
        null);
  }

  private static ArtifactLocation src(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }
}
