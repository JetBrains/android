/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.customview.preview

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.editor.multirepresentation.MultiRepresentationPreview
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class CustomViewPreviewRepresentationTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun test() {
    val file =
      projectRule.fixture.addFileToProject(
        "src/com/example/CustomView.java",
        """
        package com.example;

        import android.view.View;

        public class CustomView extends View {
          public CustomButton() {
            super();
          }
        }
        """
          .trimIndent(),
      )

    runBlocking {
      val representation =
        CustomViewPreviewRepresentation(
          file,
          buildStateProvider = { CustomViewVisualStateTracker.BuildState.SUCCESSFUL },
        )
      assertNotNull("failed to instantiate CustomViewPreviewRepresentation", representation)
      assertTrue(representation.hasPendingRefresh)
      assertFalse(representation.isActive)

      representation.onActivate()
      assertTrue(representation.isActive)
      assertFalse("onActivate should trigger a refresh", representation.hasPendingRefresh)

      assertNotNull(representation.workbench.modelContext)
      Disposer.dispose(representation)
      // Disposing the representation should dispose the workbench and clear its model context
      assertNull(representation.workbench.modelContext)
    }
  }

  @Test
  fun testDeprecationBanner() {
    val file =
      projectRule.fixture.addFileToProject(
        "src/com/example/CustomView.java",
        """
        package com.example;

        import android.view.View;

        public class CustomView extends View {
          public CustomButton() {
            super();
          }
        }
        """
          .trimIndent(),
      )

    runBlocking {
      val representation =
        CustomViewPreviewRepresentation(
          file,
          buildStateProvider = { CustomViewVisualStateTracker.BuildState.SUCCESSFUL },
        )

      val provider = CustomViewDeprecationNotificationProvider()
      val fileEditor = Mockito.mock(MultiRepresentationPreview::class.java)
      Mockito.`when`(fileEditor.currentRepresentation).thenReturn(representation)

      // Test with flag enabled
      val panelFunc = provider.collectNotificationData(projectRule.project, file.virtualFile)
      assertNotNull(panelFunc)
      val panel = panelFunc!!.apply(fileEditor)
      assertNotNull(panel)
      assertEquals(
        "Custom View preview will be deprecated in the next release, please update your use accordingly.",
        panel!!.text,
      )

      Disposer.dispose(representation)
    }
  }
}
