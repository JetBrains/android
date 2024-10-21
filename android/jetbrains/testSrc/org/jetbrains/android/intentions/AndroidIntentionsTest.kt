package org.jetbrains.android.intentions

import com.android.AndroidProjectTypes
import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.inspections.AndroidNonConstantResIdsInSwitchInspection

class AndroidIntentionsTest : AndroidTestCase() {
  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
    myFixture.addFileToProject(
      "res/values/drawables.xml",
      "<resources><drawable name='icon'>@android:drawable/btn_star</drawable></resources>",
    )
  }

  fun testSwitchOnResourceId() {
    myFacet.configuration.projectType = AndroidProjectTypes.PROJECT_TYPE_LIBRARY
    val inspection = AndroidNonConstantResIdsInSwitchInspection()
    doTest(inspection, true, inspection.quickFixName)
  }

  fun testSwitchOnResourceId1() {
    myFacet.configuration.projectType = AndroidProjectTypes.PROJECT_TYPE_APP
    val inspection = AndroidNonConstantResIdsInSwitchInspection()
    doTest(inspection, false, inspection.quickFixName)
  }

  fun testSwitchOnResourceId2() {
    myFacet.configuration.projectType = AndroidProjectTypes.PROJECT_TYPE_LIBRARY
    val inspection = AndroidNonConstantResIdsInSwitchInspection()
    doTest(inspection, false, inspection.quickFixName)
  }

  private fun doTest(inspection: LocalInspectionTool, available: Boolean, quickFixName: String) {
    myFixture.enableInspections(inspection)

    val file =
      myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".java", "src/p1/p2/Class.java")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.checkHighlighting(true, false, false)

    val quickFix = myFixture.getAvailableIntention(quickFixName)
    if (available) {
      assertNotNull(quickFix)
      myFixture.launchAction(quickFix!!)
      myFixture.checkResultByFile(BASE_PATH + getTestName(false) + "_after.java")
    } else {
      assertNull(quickFix)
    }
  }

  companion object {
    private const val BASE_PATH = "intentions/"
  }
}
