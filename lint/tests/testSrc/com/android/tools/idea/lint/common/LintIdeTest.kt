/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * U…nless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.lint.common

import com.android.testutils.TestUtils.getWorkspaceRoot
import com.android.tools.idea.util.StudioPathManager
import com.android.tools.lint.checks.CommentDetector
import com.android.tools.lint.client.api.LintClient
import com.android.tools.tests.AdtTestProjectDescriptors
import com.google.common.base.Verify
import com.google.common.collect.Sets
import com.google.common.truth.Truth.assertThat
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiFile
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.ModuleFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.testFramework.fixtures.impl.JavaModuleFixtureBuilderImpl
import com.intellij.testFramework.fixtures.impl.ModuleFixtureImpl
import com.intellij.util.ThrowableRunnable
import java.io.File
import java.nio.file.Files

class LintIdeTest : UsefulTestCase() {
  init {
    LintClient.clientName = LintClient.CLIENT_UNIT_TESTS
  }

  private lateinit var myFixture: JavaCodeInsightTestFixture
  private lateinit var myModule: Module

  override fun setUp() {
    super.setUp()

    // Compute the workspace root before any IDE code starts messing with user.dir:
    getWorkspaceRoot()
    VfsRootAccess.allowRootAccess(testRootDisposable, FileUtil.toCanonicalPath(androidPluginHome))

    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val projectBuilder = factory.createFixtureBuilder(name)
    val fixture =
      JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.fixture)
    myFixture = fixture
    fixture.setUp()
    fixture.testDataPath = testDataPath

    // Set up module and content roots
    factory.registerFixtureBuilder(
      LintModuleFixtureBuilder::class.java,
      LintModuleFixtureBuilderImpl::class.java,
    )
    val moduleFixtureBuilder = projectBuilder.addModule(LintModuleFixtureBuilder::class.java)
    moduleFixtureBuilder.setModuleRoot(fixture.tempDirPath)
    moduleFixtureBuilder.addContentRoot(fixture.tempDirPath)
    File("${fixture.tempDirPath}/src/").mkdir()
    moduleFixtureBuilder.addSourceRoot("src")
    myModule = moduleFixtureBuilder.fixture!!.module
    AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(false)
    fixture.allowTreeAccessForAllFiles()
  }

  override fun tearDown() {
    try {
      myFixture.tearDown()
    } catch (e: Throwable) {
      addSuppressedException(e)
    } finally {
      super.tearDown()
    }
  }

  private val project: Project
    get() = myFixture.project

  fun testLintIdeClientReturnsModuleFromEditorResult() {
    val fileContent =
      """
      package p1.p2;
      public class WhySoSerious {}
    """
        .trimIndent()
    val vfPointer =
      myFixture.addFileToProject("src/p1/p2/WhySoSerious.java", fileContent).virtualFile

    val module = ModuleManager.getInstance(myFixture.project).modules[0]
    val lintClient =
      LintIdeClient(
        myFixture.project,
        LintEditorResult(module, vfPointer, fileContent, Sets.newHashSet()),
      )

    assertThat(lintClient.module).isSameAs(module)
  }

  fun testUseValueOf() {
    doTestWithFix(
      AndroidLintUseValueOfInspection(),
      "Replace with valueOf()",
      "/src/test/pkg/UseValueOf.java",
      "java",
    )
  }

  fun testUseValueOfSuppress() {
    doTestWithFix(
      AndroidLintUseValueOfInspection(),
      "Suppress UseValueOf with an annotation",
      "/src/test/pkg/UseValueOf.java",
      "java",
    )
  }

  fun testWrongQuote() {
    doTestWithFix(
      AndroidLintNotInterpolatedInspection(),
      "Replace single quotes with double quotes",
      "build.gradle",
      "gradle",
    )
  }

  fun testAddSuperCallJava() {
    addCallSuper()
    doTestWithFix(
      AndroidLintMissingSuperCallInspection(),
      "Add super call",
      "/src/p1/p2/SuperTestJava.java",
      "java",
    )
  }

  fun testAddSuperCall() {
    addCallSuper()
    doTestWithFix(
      AndroidLintMissingSuperCallInspection(),
      "Add super call",
      "/src/p1/p2/SuperTest.kt",
      "kt",
    )
  }

  fun testAddSuperCallInterface() {
    addCallSuper()
    doTestWithFix(
      AndroidLintMissingSuperCallInspection(),
      "Add super call",
      "/src/p1/p2/SuperTestInterface.kt",
      "kt",
    )
  }

  fun testAddSuperCallSuppress() {
    addCallSuper()
    doTestWithFix(
      AndroidLintMissingSuperCallInspection(),
      "Suppress MissingSuperCall with an annotation",
      "/src/p1/p2/SuperTest.kt",
      "kt",
    )
  }

  fun testAddSuperCallExpression() {
    addCallSuper()
    doTestWithFix(
      AndroidLintMissingSuperCallInspection(),
      "Add super call",
      "/src/p1/p2/SuperTest.kt",
      "kt",
    )
  }

  fun testJavaCheckResultTest1() {
    addCheckResult()
    doTestWithFix(
      AndroidLintCheckResultInspection(),
      "Call replace instead",
      "/src/p1/p2/JavaCheckResultTest1.java",
      "java",
    )
  }

  fun testKotlinCheckResultTest1() {
    addCheckResult()
    doTestWithFix(
      AndroidLintCheckResultInspection(),
      "Call replace instead",
      "/src/p1/p2/KotlinCheckResultTest1.kt",
      "kt",
    )
  }

  fun testPropertyFiles() {
    doTestWithFix(AndroidLintPropertyEscapeInspection(), "Escape", "local.properties", "properties")
  }

  fun testCallSuper() {
    addCallSuper()
    doTestWithFix(
      AndroidLintMissingSuperCallInspection(),
      "Add super call",
      "src/p1/p2/CallSuperTest.java",
      "java",
    )
  }

  fun testCallSuper2() {
    addCallSuper()
    doTestWithFix(
      AndroidLintMissingSuperCallInspection(),
      "Add super call",
      "src/p1/p2/FooImpl.java",
      "java",
    )
  }

  fun testCallSuperKotlin() {
    addCallSuper()
    doTestWithFix(
      AndroidLintMissingSuperCallInspection(),
      "Add super call",
      "src/p1/p2/FooImpl.kt",
      "kt",
    )
  }

  fun testStopShip() {
    CommentDetector.STOP_SHIP.setEnabledByDefault(true)
    doTestWithFix(
      AndroidLintStopShipInspection(),
      "Remove STOPSHIP",
      "/src/test/pkg/StopShip.java",
      "java",
    )
  }

  fun testDisabledTestsEnabledOnTheFly() {
    // Verifies that inspections are force-enabled when the IDE inspection profile mentions them.
    val wasEnabled = CommentDetector.STOP_SHIP.isEnabledByDefault()
    try {
      CommentDetector.STOP_SHIP.setEnabledByDefault(false)
      myFixture.copyFileToProject("$globalTestDir/Stopship.java", "src/p1/p2/Stopship.java")
      doGlobalInspectionTest(AndroidLintStopShipInspection())
      assertThat(CommentDetector.STOP_SHIP.isEnabledByDefault()).isTrue()
    } finally {
      CommentDetector.STOP_SHIP.setEnabledByDefault(wasEnabled)
    }
  }

  fun testGradleWindows() {
    doTestWithFix(
      AndroidLintGradlePathInspection(),
      "Replace with my/libs/http.jar",
      "build.gradle",
      "gradle",
    )
  }

  // Global (batch) inspections

  fun testSuppressingInJava() {
    myFixture.copyFileToProject("$globalTestDir/MyActivity.java", "src/p1/p2/MyActivity.java")
    doGlobalInspectionTest(AndroidLintUseValueOfInspection())
  }

  fun testLintInJavaFile() {
    myFixture.copyFileToProject("$globalTestDir/MyActivity.java", "src/p1/p2/MyActivity.java")
    doGlobalInspectionTest(AndroidLintUseValueOfInspection())
  }

  fun testLintNonAndroid() {
    // Make sure that we include the lint implementation checks themselves outside of Android
    // contexts
    val issues = LintIdeIssueRegistry()
    val issue = issues.getIssue("LintImplDollarEscapes")!!
    val support = object : LintIdeSupport() {}
    assertEquals(support.getPlatforms(), issue.platforms)
  }

  fun testLintJar() {
    // This lint test checks two things:
    // (1) loading custom lint jars from a lint.xml works in the IDE in a non-Android project 1
    // (2) the specific lint check has a quickfix which tests some advanced scenarios of
    //     the replace string quickfix. This is done via a custom loaded lint check because 1
    //     it tests a scenario (quickfixes modifying different files than the one where the
    //     issue is flagged) that none of the built-in checks uses, but is required by some
    //     third party checks
    // (See tools/base's LintFixVerifierTest for the implementation of this custom lint check
    //  jar and a comment on the bottom of the file for how to update it)
    try {
      AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(true)
      myFixture.copyFileToProject("$globalTestDir/build.gradle", "build.gradle")
      myFixture.copyFileToProject("$globalTestDir/lint.xml", "lint.xml")
      myFixture.copyFileToProject("$globalTestDir/lint-fix-verifier.jar", "lint-fix-verifier.jar")
      myFixture.copyFileToProject("$globalTestDir/lint-strings.jar", "lint-strings.jar")
      val file = myFixture.copyFileToProject("$globalTestDir/Test.java", "src/test/pkg/Test.java")
      myFixture.configureFromExistingVirtualFile(file)
      myFixture.doHighlighting()
      myFixture.checkHighlighting(true, false, false)

      val action =
        myFixture.getAvailableIntention("Update build.gradle") ?: error("Failed to find intention")
      assertThat(action.asModCommandAction()).isNotNull()
      assertTrue(action.isAvailable(myFixture.project, myFixture.editor, myFixture.file))
      myFixture.launchAction(action)
      myFixture.checkResultByFile("build.gradle", "$globalTestDir/build.gradle_after", true)
    } finally {
      AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(false)
    }
  }

  @Suppress("UnstableApiUsage")
  private fun doGlobalInspectionTest(inspection: AndroidLintInspectionBase) {
    myFixture.enableInspections(inspection)
    val wrapper = GlobalInspectionToolWrapper(inspection)

    val scope = AnalysisScope(myModule)
    scope.invalidate()

    val globalContext =
      createGlobalContextForTool(scope, project, listOf<InspectionToolWrapper<*, *>>(wrapper))
    InspectionTestUtil.runTool(wrapper, scope, globalContext)
    InspectionTestUtil.compareToolResults(
      globalContext,
      wrapper,
      false,
      testDataPath + globalTestDir,
    )
    globalContext.getPresentation(wrapper)
  }

  private val globalTestDir: String
    get() = BASE_PATH_GLOBAL + getTestName(true)

  private fun doTestWithFix(
    inspection: AndroidLintInspectionBase,
    message: String,
    copyTo: String,
    extension: String,
  ) {
    myFixture.enableInspections(inspection)
    val file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + "." + extension, copyTo)
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.doHighlighting()
    myFixture.checkHighlighting(true, false, false)

    val action =
      myFixture.getAvailableIntention(message) ?: error("Failed to find intention \"$message\"")
    assertTrue(action.isAvailable(myFixture.project, myFixture.editor, myFixture.file))
    assertThat(action.asModCommandAction()).isNotNull()
    myFixture.checkPreviewAndLaunchAction(action)
    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after." + extension)
  }

  private fun addCallSuper() {
    myFixture.addFileToProject(
      "/src/android/support/annotation/CallSuper.java",
      """
        package android.support.annotation;
        import static java.lang.annotation.ElementType.METHOD;
        import static java.lang.annotation.RetentionPolicy.CLASS;
        import java.lang.annotation.Documented;
        import java.lang.annotation.Retention;
        import java.lang.annotation.Target;
        @Documented
        @Retention(CLASS)
        @Target({METHOD})
        public @interface CallSuper {
        }"""
        .trimIndent(),
    )
  }

  private fun addCheckResult(): PsiFile {
    return myFixture.addFileToProject(
      "/src/android/support/annotation/Keep.java",
      """
          package android.support.annotation;
          import static java.lang.annotation.ElementType.METHOD;
          import static java.lang.annotation.RetentionPolicy.CLASS;
          import java.lang.annotation.Documented;
          import java.lang.annotation.Retention;
          import java.lang.annotation.Target;
          @Documented
          @Retention(CLASS)
          @Target({METHOD})
          public @interface CheckResult {
              String suggest() default "";
          }"""
        .trimIndent(),
    )
  }

  interface LintModuleFixtureBuilder<T : ModuleFixture?> : JavaModuleFixtureBuilder<T> {
    fun setModuleRoot(moduleRoot: String)
  }

  class LintModuleFixtureBuilderImpl(
    fixtureBuilder: TestFixtureBuilder<out IdeaProjectTestFixture>
  ) :
    JavaModuleFixtureBuilderImpl<ModuleFixtureImpl>(fixtureBuilder),
    LintModuleFixtureBuilder<ModuleFixtureImpl> {

    init {
      AdtTestProjectDescriptors.kotlin().configureFixture(this)
    }

    private var myModuleRoot: File? = null

    override fun setModuleRoot(moduleRoot: String) {
      val file = File(moduleRoot)
      myModuleRoot = file
      if (!file.exists()) {
        Verify.verify(file.mkdirs())
      }
    }

    override fun createModule(): Module {
      myModuleRoot!!
      val project = myFixtureBuilder.fixture.project
      Verify.verifyNotNull(project)
      val moduleFilePath = myModuleRoot.toString() + "/app" + ModuleFileType.DOT_DEFAULT_EXTENSION
      return ModuleManager.getInstance(project)
        .newModule(moduleFilePath, JavaModuleType.getModuleType().id)
    }

    override fun instantiateFixture(): ModuleFixtureImpl {
      return ModuleFixtureImpl(this)
    }
  }

  fun testIsEdited() {
    val fileContent =
      """
      package p1.p2;
      public class Test {}
    """
        .trimIndent()

    val now = System.currentTimeMillis()
    val yesterday = now - 24 * 60 * 60 * 1000L

    val vFile = myFixture.addFileToProject("src/p1/p2/Test.java", fileContent).virtualFile
    val file = VfsUtilCore.virtualToIoFile(vFile)

    val module = ModuleManager.getInstance(myFixture.project).modules[0]
    val client =
      LintIdeClient(
        myFixture.project,
        LintEditorResult(module, vFile, fileContent, Sets.newHashSet()),
      )

    assertThat(file).isNotNull()
    // File was just created: recent files are treated as edited
    assertThat(client.isEdited(file, false)).isTrue()
    file.setLastModified(yesterday)
    assertThat(client.isEdited(file, true)).isFalse()

    val document = FileDocumentManager.getInstance().getDocument(vFile)
    assertThat(document).isNotNull()
    WriteCommandAction.writeCommandAction(myFixture.project)
      .run(ThrowableRunnable { document?.insertString(document.textLength, "// appended") })
    assertThat(client.isEdited(file, true)).isTrue()
  }

  companion object {
    private const val BASE_PATH = "/lint/"
    private const val BASE_PATH_GLOBAL = BASE_PATH + "global/"

    val testDataPath: String
      get() = "$androidPluginHome/../lint/tests/testData"

    // For now lint is co-located with the Android plugin
    private val androidPluginHome: String
      get() {
        val adtPath = StudioPathManager.resolvePathFromSourcesRoot("tools/adt/idea/android")
        return if (Files.exists(adtPath)) adtPath.toString()
        else PathManagerEx.findFileUnderCommunityHome("android/android").path
      }
  }
}
