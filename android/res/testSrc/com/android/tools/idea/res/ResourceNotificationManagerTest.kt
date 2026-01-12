/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.res

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.testutils.waitForCondition
import com.android.tools.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener
import com.android.tools.idea.testing.AndroidProjectRule.Companion.onDisk
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.testing.waitForResourceRepositoryUpdates
import com.android.tools.idea.util.ReformatUtil
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/** Tests for [ResourceNotificationManager]. */
@RunsInEdt
class ResourceNotificationManagerTest {
  @get:Rule val projectRule = EdtAndroidProjectRule(onDisk().initAndroid(true))

  private val fixture by lazy { projectRule.fixture }
  private val module by lazy { fixture.module }
  private val project by lazy { projectRule.project }
  private val facet by lazy { requireNotNull(AndroidFacet.getInstance(module)) }

  @Test
  fun testEditNotifications() {
    // Setup sample project: a strings file, and a couple of layout file
    val layout1 =
      projectRule.fixture.addFileToProject(
        "res/layout/my_layout1.xml",
        // language=xml
        """<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <!-- My comment -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:text="@string/hello" />
</FrameLayout>""",
      ) as XmlFile
    val resourceDir = layout1.parent!!.parent!!.virtualFile
    assertNotNull(resourceDir)

    val layout2 =
      fixture.addFileToProject(
        "res/layout/my_layout2.xml",
        // language=xml
        """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
""",
      ) as XmlFile

    val values1 =
      fixture.addFileToProject(
        "res/values/my_values1.xml",
        // language=xml
        """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="hello">Hello</string>

    <!-- Base application theme. -->
    <style name="AppTheme" parent="Theme.AppCompat.Light.DarkActionBar">
        <!-- Customize your theme here. -->
        <item name="android:colorBackground">#ff0000</item>
    </style></resources>""",
      ) as XmlFile

    fixture.addFileToProject(
      "res/values/colors.xml",
      // language=xml
      """<?xml version="1.0" encoding="utf-8"?>
<resources>
    
</resources>""",
    )

    val configuration1 =
      ConfigurationManager.getOrCreateInstance(module).getConfiguration(layout1.virtualFile)
    val manager = ResourceNotificationManager.getInstance(project)

    // Make sure that when we're modifying multiple files, with complicated
    // edits (that trigger full file rescans), we handle that scenario correctly.
    // There's actually some special optimizations done via PsiResourceItem#recomputeValue
    // to only mark the resource repository changed if the value has actually been looked
    // up. This allows us to not recompute layout if you're editing some string that
    // hasn't actually been looked up and rendered in a layout. In order to make sure
    // that that optimization doesn't kick in here, we need to look up the value of
    // the resource item first:
    runEnsuringListenersCalled(
      ResourceNotificationManager.Reason.RESOURCE_EDIT,
      layout1.virtualFile,
      configuration1,
    ) {
      assertThat(
          configuration1
            .getResourceResolver()
            .getStyle(
              com.android.ide.common.rendering.api.ResourceReference(
                ResourceNamespace.RES_AUTO,
                ResourceType.STYLE,
                "AppTheme",
              )
            )
            ?.getItem(ResourceNamespace.ANDROID, "colorBackground")
            ?.getValue()
        )
        .isEqualTo("#ff0000")
      createValueResource(
        project,
        resourceDir,
        "color2",
        ResourceType.COLOR,
        "colors.xml",
        mutableListOf("values"),
        "#fa2395",
      )
    }

    runEnsuringListenersCalled(
      ResourceNotificationManager.Reason.RESOURCE_EDIT,
      layout1.virtualFile,
      configuration1,
    ) {
      val tag = values1.document!!.rootTag!!.subTags[1].subTags[0]
      assertThat(tag.name).isEqualTo("item")
      WriteCommandAction.runWriteCommandAction(project) {
        tag.value.setEscapedText("@color/color2")
      }
    }

    // First check: Modify the layout by changing @string/hello to @string/hello_world
    // and verify that our listeners are called.
    val version1 = manager.getCurrentVersion(facet, layout1, configuration1)
    runEnsuringListenersCalled(
      ResourceNotificationManager.Reason.EDIT,
      layout1.virtualFile,
      configuration1,
    ) {
      addText(layout1, "@string/hello^", "_world")
    }
    val version2 = manager.getCurrentVersion(facet, layout1, configuration1)
    assertThat(version2).isNotEqualTo(version1)

    // Next check: Modify a <string> value definition in a values file
    // and check that those changes are flagged too
    val version3 = manager.getCurrentVersion(facet, layout1, configuration1)
    runEnsuringListenersCalled(
      ResourceNotificationManager.Reason.RESOURCE_EDIT,
      layout1.virtualFile,
      configuration1,
    ) {
      addText(values1, "name=\"hello^\"", "_world")
    }
    val version4 = manager.getCurrentVersion(facet, layout1, configuration1)
    assertThat(version4).isNotEqualTo(version3)

    // Next check: Modify content in a comment and verify that no changes are fired
    runEnsuringListenersNotCalled(layout1.virtualFile, configuration1) {
      addText(layout1, "My ^comment", "new ")
    }

    // Check that editing text in a layout file has no effect
    runEnsuringListenersNotCalled(layout1.virtualFile, configuration1) {
      addText(layout1, " ^ <TextView", "abc")
    }

    // Make sure that's true for replacements too
    runEnsuringListenersNotCalled(layout1.virtualFile, configuration1) {
      replaceText(layout1, "^abc", "abc".length, "def")
    }

    // ...and for deletions
    runEnsuringListenersNotCalled(layout1.virtualFile, configuration1) {
      removeText(layout1, "^def", "def".length)
    }

    // Check that editing text in a *values file* -does- have an effect
    // Read the value first to ensure that we trigger it as a read (see comment above for previous
    // resource resolver lookup).
    runEnsuringListenersCalled(
      ResourceNotificationManager.Reason.RESOURCE_EDIT,
      layout1.virtualFile,
      configuration1,
    ) {
      assertThat(
          configuration1
            .getResourceResolver()
            .getResolvedResource(
              com.android.ide.common.rendering.api.ResourceReference(
                ResourceNamespace.RES_AUTO,
                ResourceType.STRING,
                "hello_world",
              )
            )!!
            .getValue()
        )
        .isEqualTo("Hello")
      // getResolvedResource
      addText(values1, "Hello^</string>", " World")
    }

    // Check that recreating AppResourceRepository object doesn't affect the
    // ResourceNotificationManager.
    runEnsuringListenersCalled(
      ResourceNotificationManager.Reason.RESOURCE_EDIT,
      layout1.virtualFile,
      configuration1,
    ) {
      StudioResourceRepositoryManager.getInstance(facet).resetAllCaches()
      createValueResource(
        project,
        resourceDir,
        "color4",
        ResourceType.COLOR,
        "colors.xml",
        mutableListOf("values"),
        "#ff2300",
      )
    }

    // Next check: Mark the lines between <TextView .... /> as comments
    // and verify that our listeners are called.
    val version5 = manager.getCurrentVersion(facet, layout1, configuration1)
    runEnsuringListenersCalled(
      ResourceNotificationManager.Reason.EDIT,
      layout1.virtualFile,
      configuration1,
    ) {
      replaceText(layout1, "^<TextView", 9, "<!--<TextView-->")
      replaceText(
        layout1,
        "        ^android:layout_width",
        35,
        "<!--android:layout_width=\"match_parent\"-->",
      )
      replaceText(
        layout1,
        "        ^android:layout_height",
        36,
        "<!--android:layout_height=\"match_parent\"-->",
      )
      replaceText(layout1, "^android:text=", 37, "<!--android:text=\"@string/hello_world\" />-->")
    }
    val version6 = manager.getCurrentVersion(facet, layout1, configuration1)
    assertNotEquals(version6.toString(), version5, version6)

    // Next check: Un-mark the comments of the lines between <!--<TextView ... />--> (which we just
    // commented in previous check)
    // and verify that our listeners are called.
    val version7 = manager.getCurrentVersion(facet, layout1, configuration1)
    runEnsuringListenersCalled(
      ResourceNotificationManager.Reason.EDIT,
      layout1.virtualFile,
      configuration1,
    ) {
      replaceText(layout1, "^<!--<TextView-->", 15, "<TextView")
      replaceText(
        layout1,
        "^<!--android:layout_width=\"match_parent\"-->",
        42,
        "android:layout_width=\"match_parent\"",
      )
      replaceText(
        layout1,
        "^<!--android:layout_height=\"match_parent\"-->",
        43,
        "android:layout_height=\"match_parent\"",
      )
      replaceText(
        layout1,
        "^<!--android:text=\"@string/hello_world\" />-->",
        44,
        "android:text=\"@string/hello_world\" />",
      )
    }
    val version8 = manager.getCurrentVersion(facet, layout1, configuration1)
    assertNotEquals(version8.toString(), version7, version8)

    // Finally check that once we remove the listeners there are no more notifications.
    val listener = MyResourceChangeListener()
    manager.addListener(listener, facet, layout1.virtualFile, configuration1)
    manager.removeListener(listener, facet, layout1.virtualFile, configuration1)

    addText(layout1, "@string/hello_world^", "2")
    assertThat(listener.isCalled).isFalse()

    // TODO: Check that editing a partial URL doesn't re-render.
    // Check module dependency triggers!
    // TODO: Test that remove and replace editing also works as expected.
  }

  @Test
  fun testNotifiedOnRename() {
    // Setup sample project: a strings file, and a couple of layout file
    @Language("XML")
    val xml =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
        "    android:layout_width=\"match_parent\"\n" +
        "    android:layout_height=\"match_parent\">\n" +
        "    <!-- My comment -->\n" +
        "    <TextView\n" +
        "        android:layout_width=\"match_parent\"\n" +
        "        android:layout_height=\"match_parent\"\n" +
        "        android:text=\"@string/hello\" />\n" +
        "</FrameLayout>"
    val layout1 = fixture.addFileToProject("res/layout/my_layout1.xml", xml) as XmlFile
    val resourceDir = layout1.parent!!.parent!!.virtualFile
    assertNotNull(resourceDir)

    val configuration1: Configuration =
      ConfigurationManager.getOrCreateInstance(module).getConfiguration(layout1.virtualFile)

    runEnsuringListenersCalled(
      ResourceNotificationManager.Reason.RESOURCE_EDIT,
      layout1.virtualFile,
      configuration1,
    ) {
      RenameDialog(project, layout1, null, null).performRename("newLayout")
    }
  }

  @Test
  fun testNotNotifiedOnRenameNonResourceFile() {
    // Setup sample project: a strings file, and a couple of layout file.
    @Language("JAVA") val java = "class Hello {}"
    val javaFile = fixture.addFileToProject("src/hello.java", java)
    val resourceDir = javaFile.parent!!.parent!!.virtualFile
    assertNotNull(resourceDir)

    val configuration1: Configuration =
      ConfigurationManager.getOrCreateInstance(module).getConfiguration(javaFile.virtualFile)

    runEnsuringListenersNotCalled(javaFile.virtualFile, configuration1) {
      RenameDialog(project, javaFile, null, null).performRename("newFile.java")
    }
  }

  @Test // Regression test for b/362961808
  fun testResourceImageChangedNotNotifiedWhenOtherFileIsReformatted() {
    // Setup sample project: a layout file and an animated vector file
    val layout =
      projectRule.fixture.addFileToProject(
        "res/layout/my_layout1.xml",
        // language=xml
        """<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <!-- My comment -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:text="@string/hello" />
</FrameLayout>""",
      ) as XmlFile

    val animatedVector =
      projectRule.fixture.addFileToProject(
        "res/drawable/my_animated_vector.xml",
        // language=xml
        """
        <animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:aapt="http://schemas.android.com/aapt" >
            <aapt:attr name="android:drawable">
                <vector
                    android:height="64dp"
                    android:width="64dp"
                    android:viewportHeight="600"
                    android:viewportWidth="600" >
                    <group
                        android:name="rotationGroup"
                        android:pivotX="300.0"
                        android:pivotY="300.0"
                        android:rotation="45.0" >
                        <path
                            android:name="v"
                            android:fillColor="#000000"
                            android:pathData="M300,70 l 0,-70 70,70 0,0 -70,70z" />
                    </group>
                </vector>
            </aapt:attr>
        </animated-vector>
        """
          .trimIndent(),
      ) as XmlFile
    WriteCommandAction.runWriteCommandAction(project) {
      ReformatUtil.reformatAndRearrange(project, animatedVector)
    }

    val resourcesHaveChanged = AtomicBoolean(false)
    val layoutConfiguration: Configuration =
      ConfigurationManager.getOrCreateInstance(module).getConfiguration(layout.virtualFile)
    UIUtil.dispatchAllInvocationEvents() // Dispatch any pending notifications
    val manager = ResourceNotificationManager.getInstance(projectRule.project)
    manager.addListener(
      { resourcesHaveChanged.set(true) },
      facet,
      layout.virtualFile,
      layoutConfiguration,
    )
    WriteCommandAction.runWriteCommandAction(project) {
      ReformatUtil.reformatAndRearrange(project, animatedVector)
    }
    waitForResourceRepositoryUpdates(module, 4, TimeUnit.SECONDS)
    UIUtil.dispatchAllInvocationEvents() // Dispatch notifications
    assertWithMessage("Reformat of the vector should not have triggered a change")
      .that(resourcesHaveChanged.get())
      .isFalse()
  }

  private fun runEnsuringListenersNotCalled(
    file: VirtualFile,
    configuration: Configuration,
    runnable: Runnable,
  ) {
    runEnsuringListeners(null, file, configuration, false, runnable)
  }

  private fun runEnsuringListenersCalled(
    reason: ResourceNotificationManager.Reason,
    file: VirtualFile,
    configuration: Configuration,
    runnable: Runnable,
  ) {
    runEnsuringListeners(reason, file, configuration, true, runnable)
  }

  private fun runEnsuringListeners(
    reason: ResourceNotificationManager.Reason?,
    file: VirtualFile,
    configuration: Configuration,
    listenersShouldBeCalled: Boolean,
    runnable: Runnable,
  ) {
    val manager = ResourceNotificationManager.getInstance(project)

    // Listener 1: Listens for changes in layout 1.
    val listener1 = MyResourceChangeListener()
    manager.addListener(listener1, facet, file, configuration)

    // Listener 2: Only listens for general changes in the module.
    val listener2 = MyResourceChangeListener()
    manager.addListener(listener2, facet, null, null)

    runnable.run()

    waitForResourceRepositoryUpdates(module, 4, TimeUnit.SECONDS)
    UIUtil.dispatchAllInvocationEvents()

    val waitForListenerDuration = 5.seconds
    if (listenersShouldBeCalled) {
      waitForCondition(waitForListenerDuration) { listener1.isCalled && listener2.isCalled }
      assertThat(listener1.reasonChanged).containsExactly(reason)
      assertThat(listener2.reasonChanged).containsExactly(reason)
    } else {
      Thread.sleep(waitForListenerDuration.toJavaDuration())
      assertThat(listener1.isCalled).isFalse()
      assertThat(listener2.isCalled).isFalse()
    }

    manager.removeListener(listener1, facet, file, configuration)
    manager.removeListener(listener2, facet, null, null)
  }

  private fun addText(file: PsiFile, location: String, insertedText: String) {
    editText(file, location, 0, insertedText)
  }

  private fun removeText(file: PsiFile, location: String, length: Int) {
    editText(file, location, length, "")
  }

  private fun replaceText(file: PsiFile, location: String, length: Int, replaceText: String) {
    editText(file, location, length, replaceText)
  }

  private fun editText(file: PsiFile, location: String, length: Int, text: String) {
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = assertNotNull(documentManager.getDocument(file))

    // Insert a comment at the beginning
    WriteCommandAction.runWriteCommandAction(null) {
      val documentText = document.text
      assertThat(location).contains("^")
      val delta = location.indexOf('^')

      val target = location.substring(0, delta) + location.substring(delta + 1)
      assertThat(documentText).contains(target)
      val offset = documentText.indexOf(target)

      if (!text.isEmpty()) {
        if (length == 0) {
          document.insertString(offset + delta, text)
        } else {
          document.replaceString(offset + delta, offset + delta + length, text)
        }
      } else {
        document.deleteString(offset + delta, offset + delta + length)
      }
      documentManager.commitDocument(document)
    }
  }

  private class MyResourceChangeListener : ResourceChangeListener {
    var reasonChanged: Set<ResourceNotificationManager.Reason>? = null
      private set

    val isCalled: Boolean
      get() = reasonChanged != null

    override fun resourcesChanged(reason: ImmutableSet<ResourceNotificationManager.Reason>) {
      assertThat(reasonChanged).isNull()
      reasonChanged = reason
    }
  }
}
