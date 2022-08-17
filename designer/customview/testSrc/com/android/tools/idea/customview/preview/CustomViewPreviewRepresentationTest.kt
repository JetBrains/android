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

import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.util.Disposer
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test

class CustomViewPreviewRepresentationTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun test() {
    val file = projectRule.fixture.addFileToProject("src/com/example/CustomView.java", """
      package com.example;

      import android.view.View;

      public class CustomView extends View {
        public CustomButton() {
          super();
        }
      }
    """.trimIndent())

    runBlocking {
      listOf(uiThread, workerThread).forEach { scope ->
        val representation = withContext(scope) {
          CustomViewPreviewRepresentation(
            file,
            buildStateProvider = { CustomViewVisualStateTracker.BuildState.SUCCESSFUL }).also {
            Disposer.register(projectRule.fixture.testRootDisposable, it)
          }
        }
        TestCase.assertNotNull("failed to instantiate with scope $scope", representation)
      }
    }
  }
}