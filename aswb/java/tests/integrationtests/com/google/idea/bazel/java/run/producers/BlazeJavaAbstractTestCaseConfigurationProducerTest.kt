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
package com.google.idea.bazel.java.run.producers

import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.base.command.BlazeFlags
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager
import com.google.idea.blaze.base.model.primitives.WorkspacePath
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducerTestCase
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiMethod
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Integration tests for [BlazeJavaAbstractTestCaseConfigurationProducer]. */
@RunWith(JUnit4::class)
@Ignore("b/466755859")
class BlazeJavaAbstractTestCaseConfigurationProducerTest : BlazeRunConfigurationProducerTestCase() {

  @Before
  fun setup() {
    // required for IntelliJ to recognize annotations, JUnit version, etc.
    workspace.createPsiFile(
      WorkspacePath("org/junit/runner/RunWith.java"),
      "package org.junit.runner;",
      "public @interface RunWith {",
      "    Class<? extends Runner> value();",
      "}",
    )
    workspace.createPsiFile(WorkspacePath("org/junit/Test.java"), "package org.junit;", "public @interface Test {}")
    workspace.createPsiFile(WorkspacePath("org/junit/runners/JUnit4.java"), "package org.junit.runners;", "public class JUnit4 {}")
  }

  @Test
  fun testIgnoreTestClassWithNoTestSubclasses() {
    val javaFile =
      createAndIndexFile(
        WorkspacePath("java/com/google/test/TestClass.java"),
        "package com.google.test;",
        "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
        "public class TestClass {",
        "  @org.junit.Test",
        "  public void testMethod1() {}",
        "  @org.junit.Test",
        "  public void testMethod2() {}",
        "}",
      )

    val javaClass = (javaFile as PsiClassOwner).classes[0]
    assertThat(javaClass).isNotNull()

    val context = createContextFromPsi(javaClass)
    val fromContext = BlazeJavaAbstractTestCaseConfigurationProducer().createConfigurationFromContext(context)
    assertThat(fromContext).isNull()
  }

  @Test
  fun testIgnoreAbstractTestClassWithNoTestSubclasses() {
    val javaFile =
      createAndIndexFile(
        WorkspacePath("java/com/google/test/TestClass.java"),
        "package com.google.test;",
        "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
        "public abstract class TestClass {",
        "  @org.junit.Test",
        "  public void testMethod1() {}",
        "  @org.junit.Test",
        "  public void testMethod2() {}",
        "}",
      )

    val javaClass = (javaFile as PsiClassOwner).classes[0]
    assertThat(javaClass).isNotNull()

    val context = createContextFromPsi(javaClass)
    val fromContext = BlazeJavaAbstractTestCaseConfigurationProducer().createConfigurationFromContext(context)
    assertThat(fromContext).isNull()
  }

  @Test
  fun testHandlesNonAbstractClassWithTestSubclass() {
    workspace.createPsiDirectory(WorkspacePath("java/com/google/test"))
    val superClassFile =
      createAndIndexFile(
        WorkspacePath("java/com/google/test/NonAbstractSuperClassTestCase.java"),
        "package com.google.test;",
        "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
        "public class NonAbstractSuperClassTestCase {",
        "  @org.junit.Test",
        "  public void testMethod() {}",
        "}",
      )

    createAndIndexFile(
      WorkspacePath("java/com/google/test/TestClass.java"),
      "package com.google.test;",
      "import com.google.test.NonAbstractSuperClassTestCase;",
      "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
      "public class TestClass extends NonAbstractSuperClassTestCase {",
      "  @org.junit.Test",
      "  public void anotherTestMethod() {}",
      "}",
    )

    val javaClass = (superClassFile as PsiClassOwner).classes[0]
    assertThat(javaClass).isNotNull()

    val context = createContextFromPsi(superClassFile)
    val configurations = context.configurationsFromContext
    assertThat(configurations).hasSize(1)

    val fromContext = configurations!![0]
    assertThat(fromContext.isProducedBy(BlazeJavaAbstractTestCaseConfigurationProducer::class.java)).isTrue()
    assertThat(fromContext.sourceElement).isEqualTo(javaClass)

    val config = fromContext.configuration
    assertThat(config).isInstanceOf(BlazeCommandRunConfiguration::class.java)
    val blazeConfig = config as BlazeCommandRunConfiguration
    assertThat(blazeConfig.targetPatterns).isEmpty()
    assertThat(blazeConfig.name).isEqualTo("Choose subclass for NonAbstractSuperClassTestCase")
  }

  @Test
  fun testConfigurationCreatedFromAbstractClass() {
    workspace.createPsiDirectory(WorkspacePath("java/com/google/test"))
    val abstractClassFile =
      createAndIndexFile(
        WorkspacePath("java/com/google/test/AbstractTestCase.java"),
        "package com.google.test;",
        "public abstract class AbstractTestCase {}",
      )

    createAndIndexFile(
      WorkspacePath("java/com/google/test/TestClass.java"),
      "package com.google.test;",
      "import com.google.test.AbstractTestCase;",
      "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
      "public class TestClass extends AbstractTestCase {",
      "  @org.junit.Test",
      "  public void testMethod1() {}",
      "  @org.junit.Test",
      "  public void testMethod2() {}",
      "}",
    )

    val javaClass = (abstractClassFile as PsiClassOwner).classes[0]
    assertThat(javaClass).isNotNull()

    val context = createContextFromPsi(abstractClassFile)
    val configurations = context.configurationsFromContext
    assertThat(configurations).hasSize(1)

    val fromContext = configurations!![0]
    assertThat(fromContext.isProducedBy(BlazeJavaAbstractTestCaseConfigurationProducer::class.java)).isTrue()
    assertThat(fromContext.sourceElement).isEqualTo(javaClass)

    val config = fromContext.configuration
    assertThat(config).isInstanceOf(BlazeCommandRunConfiguration::class.java)
    val blazeConfig = config as BlazeCommandRunConfiguration
    assertThat(blazeConfig.targetPatterns).isEmpty()
    assertThat(blazeConfig.name).isEqualTo("Choose subclass for AbstractTestCase")

    val builder = MockBlazeProjectDataBuilder.builder(workspaceRoot)
    registerProjectService(BlazeProjectDataManager::class.java, MockBlazeProjectDataManager(builder.build()))

    var ran = false
    val producer = BlazeJavaAbstractTestCaseConfigurationProducer()
    producer.onFirstRun(fromContext, context) { ran = true }
    ApplicationManager.getApplication().invokeAndWait {}
    assertThat(ran).isTrue()

    assertThat(blazeConfig.targetPatterns).containsExactly("//java/com/google/test:TestClass")
    assertThat(getTestFilterContents(blazeConfig)).isEqualTo(BlazeFlags.TEST_FILTER + "=com.google.test.TestClass#")
  }

  @Test
  fun testConfigurationCreatedFromMethodInAbstractClass() {
    val builder = MockBlazeProjectDataBuilder.builder(workspaceRoot)
    registerProjectService(BlazeProjectDataManager::class.java, MockBlazeProjectDataManager(builder.build()))

    val abstractClassFile =
      createAndIndexFile(
        WorkspacePath("java/com/google/test/AbstractTestCase.java"),
        "package com.google.test;",
        "public abstract class AbstractTestCase {",
        "  @org.junit.Test",
        "  public void testMethod() {}",
        "}",
      )

    createAndIndexFile(
      WorkspacePath("java/com/google/test/TestClass.java"),
      "package com.google.test;",
      "import com.google.test.AbstractTestCase;",
      "import org.junit.runner.RunWith;",
      "import org.junit.runners.JUnit4;",
      "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
      "public class TestClass extends AbstractTestCase {}",
    )

    val javaClass = (abstractClassFile as PsiClassOwner).classes[0]
    val method = PsiUtils.findFirstChildOfClassRecursive(javaClass, PsiMethod::class.java)
    assertThat(method).isNotNull()

    val context = createContextFromPsi(method)
    val configurations = context.configurationsFromContext
    assertThat(configurations).hasSize(1)

    val fromContext = configurations!![0]
    assertThat(fromContext.isProducedBy(BlazeJavaAbstractTestCaseConfigurationProducer::class.java)).isTrue()
    assertThat(fromContext.sourceElement).isEqualTo(method)

    val config = fromContext.configuration
    assertThat(config).isInstanceOf(BlazeCommandRunConfiguration::class.java)
    val blazeConfig = config as BlazeCommandRunConfiguration
    assertThat(blazeConfig.targetPatterns).isEmpty()
    assertThat(blazeConfig.name).isEqualTo("Choose subclass for AbstractTestCase.testMethod")

    var ran = false
    val producer = BlazeJavaAbstractTestCaseConfigurationProducer()
    producer.onFirstRun(fromContext, context) { ran = true }
    ApplicationManager.getApplication().invokeAndWait {}
    assertThat(ran).isTrue()

    assertThat(blazeConfig.targetPatterns).containsExactly("//java/com/google/test:TestClass")
    assertThat(getTestFilterContents(blazeConfig)).isEqualTo(BlazeFlags.TEST_FILTER + "=com.google.test.TestClass#testMethod$")
  }

  @Test
  fun testConfigurationCancelledFromAbstractClass() {
    workspace.createPsiDirectory(WorkspacePath("java/com/google/test"))
    val abstractClassFile =
      createAndIndexFile(
        WorkspacePath("java/com/google/test/AbstractTestCase.java"),
        "package com.google.test;",
        "public abstract class AbstractTestCase {}",
      )

    createAndIndexFile(
      WorkspacePath("java/com/google/test/TestClass.java"),
      "package com.google.test;",
      "import com.google.test.AbstractTestCase;",
      "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
      "public class TestClass extends AbstractTestCase {",
      "  @org.junit.Test",
      "  public void testMethod1() {}",
      "}",
    )

    createAndIndexFile(
      WorkspacePath("java/com/google/test/TestClass2.java"),
      "package com.google.test;",
      "import com.google.test.AbstractTestCase;",
      "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
      "public class TestClass2 extends AbstractTestCase {}",
    )

    val javaClass = (abstractClassFile as PsiClassOwner).classes[0]
    val context = createContextFromPsi(abstractClassFile)
    val configurations = context.configurationsFromContext
    val fromContext = configurations!![0]
    val blazeConfig = fromContext.configuration as BlazeCommandRunConfiguration

    val builder = MockBlazeProjectDataBuilder.builder(workspaceRoot)
    registerProjectService(BlazeProjectDataManager::class.java, MockBlazeProjectDataManager(builder.build()))

    SubclassTestChooser.testSelectionHook = { null }

    var ran = false
    val producer = BlazeJavaAbstractTestCaseConfigurationProducer()
    producer.onFirstRun(fromContext, context) { ran = true }
    ApplicationManager.getApplication().invokeAndWait {}

    assertThat(ran).isFalse()
    assertThat(blazeConfig.targetPatterns).isEmpty()

    SubclassTestChooser.testSelectionHook = null
  }
}
