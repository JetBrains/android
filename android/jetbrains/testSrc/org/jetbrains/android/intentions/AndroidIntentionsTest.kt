package org.jetbrains.android.intentions

import com.android.AndroidProjectTypes
import com.android.SdkConstants
import com.android.test.testutils.TestUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.android.inspections.AndroidNonConstantResIdsInSwitchInspection
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val BASE_PATH = "intentions"

@RunWith(JUnit4::class)
class AndroidIntentionsTest {
  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  private val fixture by lazy {
    projectRule.fixture.apply {
      testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData").toString()
    }
  }
  private val facet by lazy { requireNotNull(fixture.module.androidFacet) }

  @Before
  fun setUp() {
    fixture.copyFileToProject(
      SdkConstants.FN_ANDROID_MANIFEST_XML,
      SdkConstants.FN_ANDROID_MANIFEST_XML,
    )
    fixture.addFileToProject(
      "res/values/drawables.xml",
      // language=XML
      """
      <resources>
        <drawable name='icon'>@android:drawable/btn_star</drawable>
        <drawable name='icon2'>@android:drawable/btn_star</drawable>
      </resources>
      """
        .trimIndent(),
    )
  }

  @Test
  fun switchOnResourceId() {
    facet.configuration.projectType = AndroidProjectTypes.PROJECT_TYPE_LIBRARY
    val inspection = AndroidNonConstantResIdsInSwitchInspection()
    doTest(inspection, true, "SwitchOnResourceId.java", "SwitchOnResourceId_after.java")
  }

  @Test
  fun switchOnResourceId1() {
    facet.configuration.projectType = AndroidProjectTypes.PROJECT_TYPE_APP
    val inspection = AndroidNonConstantResIdsInSwitchInspection()
    doTest(inspection, false, "SwitchOnResourceId1.java")
  }

  @Test
  fun switchOnResourceId2() {
    facet.configuration.projectType = AndroidProjectTypes.PROJECT_TYPE_LIBRARY
    val inspection = AndroidNonConstantResIdsInSwitchInspection()
    doTest(inspection, false, "SwitchOnResourceId2.java")
  }

  @Test
  fun switchOnResourceId3() {
    // Validate case statement with multiple value
    facet.configuration.projectType = AndroidProjectTypes.PROJECT_TYPE_LIBRARY
    val inspection = AndroidNonConstantResIdsInSwitchInspection()
    doTest(inspection, true, "SwitchOnResourceId3.java", "SwitchOnResourceId3_after.java")
  }

  private fun doTest(
    inspection: LocalInspectionTool,
    available: Boolean,
    inputFileName: String,
    afterFileName: String? = null,
  ) {
    fixture.enableInspections(inspection)

    val file = fixture.copyFileToProject("$BASE_PATH/$inputFileName", "src/p1/p2/Class.java")
    fixture.configureFromExistingVirtualFile(file)
    fixture.checkHighlighting(true, false, false)

    val quickFix = fixture.getAvailableIntention("Replace 'switch' with 'if'")
    if (available) {
      requireNotNull(quickFix) { "Quick fix should have been found." }
      if (afterFileName != null) {
        fixture.launchAction(quickFix)
        fixture.checkResultByFile("$BASE_PATH/$afterFileName")
      }
    } else {
      assertThat(quickFix).isNull()
    }
  }
}
