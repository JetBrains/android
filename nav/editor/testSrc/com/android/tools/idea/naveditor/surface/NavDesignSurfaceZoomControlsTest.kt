/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.naveditor.surface

import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils
import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.editor.zoomActionPlace
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.naveditor.model.NavComponentRegistrar
import com.android.tools.idea.rendering.NoSecurityManagerRenderService
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.BorderLayout
import java.nio.file.Paths
import javax.swing.JPanel

class NavDesignSurfaceZoomControlsTest {
  @get:Rule
  val androidProjectRule = AndroidProjectRule.withSdk()

  @Before
  fun setup() {
    androidProjectRule.fixture.testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/nav/editor/testData").toString()
    RenderTestUtil.beforeRenderTestCase()
    RenderService.setForTesting(androidProjectRule.project, NoSecurityManagerRenderService(androidProjectRule.project))
  }

  @After
  fun tearDown() {
    ApplicationManager.getApplication().invokeAndWait {
      RenderTestUtil.afterRenderTestCase()
    }
  }

  /**
   * Populate the [NavigationSchema] by using a fake androidx navigation library.
   */
  private fun populateSchema() {
    androidProjectRule.fixture.addFileToProject(
      "src/main/java/androidx/fragment/app/Fragment.kt",
      //language=kotlin
      """
        package androidx.fragment.app

        class Fragment
      """.trimIndent()
    )

    @Suppress("RemoveEmptyClassBody")
    androidProjectRule.fixture.addFileToProject(
      "src/main/java/androidx/navigation/Navigators.kt",
      //language=kotlin
      """
        package androidx.navigation

        import android.app.Activity

        import androidx.fragment.app.Fragment

        open class Navigator<T> {
          annotation class Name(val value: String)
        }

        class NavGraph {
        }

        @Navigator.Name("navigation")
        class NavGraphNavigator : Navigator<NavGraph>()

        @Navigator.Name("activity")
        class ActivityNavigator : Navigator<Activity>()

        @Navigator.Name("fragment")
        class FragmentNavigator : Navigator<Fragment>()
      """.trimIndent()
    )

    invokeAndWaitIfNeeded {
      NavigationSchema.createIfNecessary(androidProjectRule.module)
    }
  }

  private fun getGoldenImagePath(testName: String) =
    Paths.get("${androidProjectRule.fixture.testDataPath}/zoomGoldenImages/$testName.png")

  @Test
  fun testNavDesignSurfaceZoom() {
    populateSchema()
    val facet = androidProjectRule.module.androidFacet!!

    androidProjectRule.fixture.addFileToProject(
      "src/main/java/com/example/myapplication/FirstFragment.java",
      //language=Java
      """
        package com.example.myapplication;

        import androidx.fragment.app.Fragment;

        public class FirstFragment extends Fragment {
        }
      """.trimIndent()
    )
    androidProjectRule.fixture.addFileToProject(
      "src/main/java/com/example/myapplication/SecondFragment.java",
      //language=Java
      """
        package com.example.myapplication;

        import androidx.fragment.app.Fragment;

        public class SecondFragment extends Fragment {
        }
      """.trimIndent()
    )
    val layout = androidProjectRule.fixture.addFileToProject(
      "res/layout/test.xml",
      //language=xml
      """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:background="#0000FF"
              android:padding="30dp"
              android:orientation="vertical">
              <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize='40sp'
                android:text="Hello world"/>
        </LinearLayout>
      """.trimIndent())
    val navGraph = androidProjectRule.fixture.addFileToProject(
      "res/navigation/nav_graph.xml",
      //language=xml
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            xmlns:tools="http://schemas.android.com/tools"
            android:id="@+id/nav_graph"
            app:startDestination="@id/FirstFragment">

            <fragment
                android:id="@+id/FirstFragment"
                android:name="com.example.myapplication.FirstFragment"
                android:label="First"
                tools:layout="@layout/test">

                <action
                    android:id="@+id/action_FirstFragment_to_SecondFragment"
                    app:destination="@id/SecondFragment" />
            </fragment>
            <fragment
                android:id="@+id/SecondFragment"
                android:name="com.example.myapplication.SecondFragment"
                android:label="Second"
                tools:layout="@layout/test">
                <action
                    android:id="@+id/action_SecondFragment_to_FirstFragment"
                    app:destination="@id/FirstFragment" />
            </fragment>
        </navigation>
      """.trimIndent()
    )
    val configuration = RenderTestUtil.getConfiguration(androidProjectRule.fixture.module, layout.virtualFile)
    val surface = invokeAndWaitIfNeeded {
      NavDesignSurface(androidProjectRule.project, androidProjectRule.fixture.testRootDisposable)
    }

    surface.activate()

    val model = NlModel.builder(facet, navGraph.virtualFile, configuration)
      .withParentDisposable(androidProjectRule.testRootDisposable)
      .withComponentRegistrar(NavComponentRegistrar)
      .build()

    invokeAndWaitIfNeeded {
      surface.addModelWithoutRender(model)
      surface.currentNavigation = surface.sceneManager!!.model.find("FirstFragment")!!
    }

    val fakeUi = invokeAndWaitIfNeeded {
      val outerPanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.customLine(JBColor.RED)
        add(surface, BorderLayout.CENTER)
        setBounds(0, 0, 1000, 1000)
      }

      FakeUi(outerPanel, 1.0, true).apply {
        updateToolbars()
        layoutAndDispatchEvents()
      }
    }

    ImageDiffUtil.assertImageSimilar(getGoldenImagePath("zoomInitial"), fakeUi.render(), 0.1, 1)

    val zoomActionsToolbar = fakeUi.findComponent<ActionToolbarImpl> { it.place == zoomActionPlace }!!
    val zoomInAction = zoomActionsToolbar.actions
      .filterIsInstance<ZoomInAction>()
      .single()
    val zoomOutAction = zoomActionsToolbar.actions
      .filterIsInstance<ZoomOutAction>()
      .single()
    val zoomToFitAction = zoomActionsToolbar.actions
      .filterIsInstance<ZoomToFitAction>()
      .single()

    val event = TestActionEvent { dataId -> surface.getData(dataId) }
    zoomToFitAction.actionPerformed(event)
    val zoomToFitScale = surface.scale

    // Verify zoom in
    run {
      val originalScale = surface.scale
      repeat(3) { zoomInAction.actionPerformed(event) }
      Assert.assertTrue(surface.scale > originalScale)
      ImageDiffUtil.assertImageSimilar(getGoldenImagePath("zoomIn"), fakeUi.render(), 0.1, 1)
    }

    // Verify zoom to fit
    run {
      zoomToFitAction.actionPerformed(event)
      Assert.assertEquals(zoomToFitScale, surface.scale, 0.01)
      ImageDiffUtil.assertImageSimilar(getGoldenImagePath("zoomFit"), fakeUi.render(), 0.1, 1)
    }

    // Verify zoom out
    run {
      val originalScale = surface.scale
      repeat(3) { zoomOutAction.actionPerformed(event) }
      Assert.assertTrue(surface.scale < originalScale)
      ImageDiffUtil.assertImageSimilar(getGoldenImagePath("zoomOut"), fakeUi.render(), 0.1, 1)
    }
  }
}