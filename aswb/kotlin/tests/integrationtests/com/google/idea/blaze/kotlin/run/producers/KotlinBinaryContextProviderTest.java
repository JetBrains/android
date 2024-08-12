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
package com.google.idea.blaze.kotlin.run.producers;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeRunConfiguration;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.psi.PsiFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link KotlinBinaryContextProvider}. */
@RunWith(JUnit4.class)
public class KotlinBinaryContextProviderTest extends BlazeRunConfigurationProducerTestCase {
  @Test
  public void testMainMethodIsRunnable() throws Throwable {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("kt_jvm_binary")
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.MainKt"))
                    .setLabel("//com/google/binary:main_kt")
                    .addSource(sourceRoot("com/google/binary/Main.kt"))
                    .build())
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("kt_jvm_binary")
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.Main"))
                    .setLabel("//com/google/binary:just_main")
                    .addSource(sourceRoot("com/google/binary/JustMain.kt"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiFile kotlinFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/Main.kt"),
            "package com.google.binary",
            "fun main(args: Array<String>) {}");

    RunConfiguration config = createConfigurationFromLocation(kotlinFile);
    assertThat(config).isInstanceOf(BlazeRunConfiguration.class);
  }

  @Test
  public void testMainClassInterpretedCorrectly() throws Throwable {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("kt_jvm_binary")
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.Main"))
                    .setLabel("//com/google/binary:just_main")
                    .addSource(sourceRoot("com/google/binary/JustMain.kt"))
                    .build())
            .build());

    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiFile justMainFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/JustMain.kt"),
            "@file:JvmName(\"Main\")",
            "package com.google.binary",
            "fun main(args: Array<String>) {}");

    RunConfiguration justMainRunConfig = createConfigurationFromLocation(justMainFile);
    assertThat(justMainRunConfig).isInstanceOf(BlazeRunConfiguration.class);
  }
}
