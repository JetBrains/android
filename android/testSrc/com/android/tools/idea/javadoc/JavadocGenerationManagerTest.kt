package com.android.tools.idea.javadoc

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.analysis.AnalysisScope
import com.intellij.javadoc.JavadocGenerationManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.WaitFor
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class JavadocGenerationManagerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withSdk()

  @get:Rule
  val edtRule = EdtRule()

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
    val waitFor = object : WaitFor(TimeUnit.SECONDS.toMillis(10).toInt()) {
      override fun condition(): Boolean =
        outputDirectory.list()?.isNotEmpty() ?: false
    }
    waitFor.join()
    assertTrue("Output directory does not contain any output", waitFor.isConditionRealized)
  }
}