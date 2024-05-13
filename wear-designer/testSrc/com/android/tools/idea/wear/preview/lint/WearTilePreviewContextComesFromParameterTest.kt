/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wear.preview.lint

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wear.preview.WearPreviewBundle.message
import com.android.tools.idea.wear.preview.WearTileProjectRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WearTilePreviewContextComesFromParameterTest {

  @get:Rule val projectRule = WearTileProjectRule(AndroidProjectRule.withAndroidModel())

  private val fixture
    get() = projectRule.fixture

  private val inspection = WearTilePreviewContextComesFromParameter()

  @Before
  fun setUp() {
    fixture.enableInspections(inspection)
  }

  @Test
  fun checkErrorsKotlin() {
    val file =
      fixture.addFileToProject(
        "src/main/test.kt",
        // language=kotlin
        """
        import android.app.Activity
        import android.content.Context
        import androidx.wear.tiles.tooling.preview.Preview
        import androidx.wear.tiles.tooling.preview.TilePreviewData

        private class MyContext : Activity() {
          fun myOwnTileFunction() = TilePreviewData()

          @Preview
          fun previewUsingEnclosingClassAsContext() = tile(<error descr="${message("inspection.context.comes.from.parameter")}">this</error>)
        }

        private val someOtherContext = MyContext()

        fun tile(context: Context) = TilePreviewData()

        @Preview
        fun previewUsingWrongContextDirectly() = tile(<error descr="${message("inspection.context.comes.from.parameter")}">someOtherContext</error>)

        @Preview
        fun previewUsingWrongContextDirectlyWithContextParameter(context: Context) = tile(<error descr="${message("inspection.context.comes.from.parameter")}">someOtherContext</error>)

        @Preview
        // valid because we don't actually use the context to create the tile
        fun previewUsingWrongContextInAValidWay() = someOtherContext.myOwnTileFunction()

        @Preview
        fun previewUsingWrongContextInAnInvalidWay(): TilePreviewData {
          // invalid because we should not be relying on data that comes from an invalid context
          val invalid = <error descr="${message("inspection.context.comes.from.parameter")}">someOtherContext</error>.isUiContext
          return TilePreviewData()
        }

        @Preview
        fun previewUsingContextProperly(context: Context) = tile(context)
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting(false, false, false)
  }

  @Test
  fun checkErrorsJava() {
    val file =
      fixture.addFileToProject(
        "src/main/Test.java",
        // language=java
        """
        import android.app.Activity;
        import android.content.Context;
        import androidx.wear.tiles.tooling.preview.Preview;
        import androidx.wear.tiles.tooling.preview.TilePreviewData;

        class Test {
          private class MyContext extends Activity {
            TilePreviewData myOwnTileFunction() {
              return new TilePreviewData();
            }

            @Preview
            TilePreviewData previewUsingEnclosingClassAsContext() {
              return tile(this);
            }
          }

          private MyContext someOtherContext = new MyContext();

          TilePreviewData tile(Context context) {
            return new TilePreviewData();
          }

          @Preview
          TilePreviewData previewUsingWrongContextDirectly() {
            return tile(<error descr="${message("inspection.context.comes.from.parameter")}">someOtherContext</error>);
          }

          @Preview
          TilePreviewData previewUsingWrongContextDirectlyWithContextParameter(Context context) {
            return tile(<error descr="${message("inspection.context.comes.from.parameter")}">someOtherContext</error>);
          }

          @Preview
          // valid because we don't actually use the context to create the tile
          TilePreviewData previewUsingWrongContextInAValidWay() {
            return someOtherContext.myOwnTileFunction();
          }

          @Preview
          TilePreviewData previewUsingWrongContextInAnInvalidWay() {
            // invalid because we should not be relying on data that comes from an invalid context
            boolean invalid = <error descr="${message("inspection.context.comes.from.parameter")}">someOtherContext</error>.isUiContext();
            return new TilePreviewData();
          }

          @Preview
          TilePreviewData previewUsingContextProperly(Context context) {
            return tile(context);
          }
        }
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting(false, false, false)
  }
}
