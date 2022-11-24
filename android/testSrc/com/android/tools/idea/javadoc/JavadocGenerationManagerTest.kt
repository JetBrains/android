package com.android.tools.idea.javadoc

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.analysis.AnalysisScope
import com.intellij.javadoc.JavadocGenerationManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class JavadocGenerationManagerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withSdk()

  @get:Rule
  val edtRule = EdtRule()

  @After
  fun tearDown() {
    ProjectJdkTable.getInstance().allJdks.forEach {
      SdkConfigurationUtil.removeSdk(it)
    }
  }

  @Test
  @RunsInEdt
  fun invokeGenerateJavaDocAction() {
    val outputDirectory = createTempDir().also {
      it.deleteOnExit()
    }
    projectRule.fixture.addFileToProject("src/main/java/com/test/Test.java", """
      package com.test;

      /**
       * Class Javadoc.
       */
      public class Test {
        /** Field Javadoc */
        public int a = 0;
      }
    """.trimIndent())

    val scope = AnalysisScope(projectRule.project)
    val javadocGenerationManager = JavadocGenerationManager.getInstance(projectRule.project)
    javadocGenerationManager.configuration.apply {
      OPEN_IN_BROWSER = false
      OUTPUT_DIRECTORY = outputDirectory.absolutePath
    }
    javadocGenerationManager.generateJavadoc(scope)

    // The Javadoc is generated in the background by the javadoc command line. Wait until it gets generated.
    // Verify that there is an output.
    val latch = CountDownLatch(1)
    val thread = thread(start = true) {
      repeat(10) {
        if (outputDirectory.list()?.isNotEmpty() == true) {
          latch.countDown()
          return@thread
        }
        Thread.sleep(TimeUnit.SECONDS.toMillis(1))
      }
    }
    if (!latch.await(10, TimeUnit.SECONDS)) {
      fail("Output directory does not contain any output")
    }
    thread.interrupt()
  }
}