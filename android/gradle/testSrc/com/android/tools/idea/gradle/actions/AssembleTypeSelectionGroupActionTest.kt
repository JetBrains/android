package com.android.tools.idea.gradle.actions

import junit.framework.TestCase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [AssembleTypeSelectionGroupActionTest].
 */
class AssembleTypeSelectionGroupActionTest : TestCase() {

  fun testDoPerform() {
    val groupAction = MakeTypeSelectionGroupAction()
    val children = groupAction.getChildren(null)
    assertEquals(2, children.size, "Number of make options in the action group")

    val makeModules = children.find { it is AssembleGradleModuleActionFromGroupAction }
    val makeProject = children.find { it is AssembleGradleProjectWithTestsAction }

    assertNotNull(makeModules, "Action to build selected modules")
    assertNotNull(makeProject, "Action to build project")
    assertTrue(groupAction.isPrimary(makeModules), "Make selected modules is primary action")
  }
}