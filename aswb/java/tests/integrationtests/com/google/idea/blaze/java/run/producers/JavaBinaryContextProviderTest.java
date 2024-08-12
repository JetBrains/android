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
package com.google.idea.blaze.java.run.producers;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeRunConfiguration;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.psi.PsiFile;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link JavaBinaryContextProvider}. */
@RunWith(JUnit4.class)
public class JavaBinaryContextProviderTest extends BlazeRunConfigurationProducerTestCase {

  @Test
  public void testUniqueJavaBinaryChosen() throws Throwable {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_binary")
                    .setLabel("//com/google/binary:UnrelatedName")
                    .addSource(sourceRoot("com/google/binary/MainClass.java"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiFile javaClass =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/MainClass.java"),
            "package com.google.binary;",
            "import java.lang.String;",
            "public class MainClass {",
            "  public static void main(java.lang.String[] args) {}",
            "}");

    RunConfiguration config = createConfigurationFromLocation(javaClass);

    assertThat(config).isInstanceOf(BlazeRunConfiguration.class);
    BlazeRunConfiguration blazeConfig = (BlazeRunConfiguration) config;
    assertThat(blazeConfig.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//com/google/binary:UnrelatedName"));
  }

  @Test
  public void testNoJavaBinaryChosenIfNotInRDeps() throws Throwable {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_binary")
                    .setLabel("//com/google/binary:MainClass")
                    .addSource(sourceRoot("com/google/binary/OtherClass.java"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiFile javaClass =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/MainClass.java"),
            "package com.google.binary;",
            "import java.lang.String;",
            "public class MainClass {",
            "  public static void main(java.lang.String[] args) {}",
            "}");

    assertThat(createConfigurationFromLocation(javaClass))
        .isNotInstanceOf(BlazeRunConfiguration.class);
  }

  @Test
  public void testNoResultForClassWithoutMainMethod() throws Throwable {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_binary")
                    .setLabel("//com/google/binary:MainClass")
                    .addSource(sourceRoot("com/google/binary/MainClass.java"))
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.MainClass"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiFile javaClass =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/MainClass.java"),
            "package com.google.binary;",
            "public class MainClass {}");

    assertThat(createConfigurationFromLocation(javaClass)).isNull();
  }

  @Test
  public void testJavaBinaryWithMatchingNameChosen() throws Throwable {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_binary")
                    .setLabel("//com/google/binary:UnrelatedName")
                    .addSource(sourceRoot("com/google/binary/MainClass.java"))
                    .build())
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_binary")
                    .setLabel("//com/google/binary:MainClass")
                    .addSource(sourceRoot("com/google/binary/MainClass.java"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiFile javaClass =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/MainClass.java"),
            "package com.google.binary;",
            "import java.lang.String;",
            "public class MainClass {",
            "  public static void main(java.lang.String[] args) {}",
            "}");

    RunConfiguration config = createConfigurationFromLocation(javaClass);
    assertThat(config).isInstanceOf(BlazeRunConfiguration.class);
    BlazeRunConfiguration blazeConfig = (BlazeRunConfiguration) config;
    assertThat(blazeConfig.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//com/google/binary:MainClass"));
  }

  @Test
  public void testJavaBinaryWithMatchingMainClassChosen() throws Throwable {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_binary")
                    .setLabel("//com/google/binary:UnrelatedName")
                    .addSource(sourceRoot("com/google/binary/MainClass.java"))
                    .build())
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_binary")
                    .setLabel("//com/google/binary:OtherName")
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.MainClass"))
                    .addSource(sourceRoot("com/google/binary/MainClass.java"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiFile javaClass =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/MainClass.java"),
            "package com.google.binary;",
            "import java.lang.String;",
            "public class MainClass {",
            "  public static void main(java.lang.String[] args) {}",
            "}");

    RunConfiguration config = createConfigurationFromLocation(javaClass);

    assertThat(config).isInstanceOf(BlazeRunConfiguration.class);
    BlazeRunConfiguration blazeConfig = (BlazeRunConfiguration) config;
    assertThat(blazeConfig.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//com/google/binary:OtherName"));
  }
}
