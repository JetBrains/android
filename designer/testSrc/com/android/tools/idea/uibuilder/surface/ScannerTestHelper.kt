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
package com.android.tools.idea.uibuilder.surface

import android.view.View
import com.android.ide.common.rendering.api.Result
import com.android.ide.common.rendering.api.ViewInfo
import com.android.testutils.MockitoKt.whenever
import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.NlTreeReader
import com.android.tools.idea.uibuilder.model.NlComponentMixin
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.idea.validator.ValidatorResult
import com.android.tools.rendering.RenderResult
import com.android.tools.rendering.RenderResultStats
import com.google.common.collect.ImmutableList
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.mockito.Mockito

/**
 * Helper class for testing [NlLayoutScanner]. It generates/mocks appropriate data needed for
 * testing.
 */
class ScannerTestHelper {

  private var viewId = 0

  /** Returns the [View.getId] of the last generated [NlComponent] by [buildNlComponent] */
  val lastUsedViewId
    get() = viewId

  private var validatorIssueId = 0L

  /**
   * Returns [ValidatorData.Issue.mSrcId] that is used by the last issue created by the helper thru
   * [generateResult].
   */
  val lastUsedIssueId
    get() = validatorIssueId

  /** Create a default component useful for testing. It contains mocked view information. */
  fun buildNlComponent(model: NlModel? = null, tagName: String = "tagname"): NlComponent {
    val nlComponent = Mockito.mock(NlComponent::class.java)
    val mixin = NlComponentMixin(nlComponent)
    whenever(nlComponent.mixin).thenReturn(mixin)

    val nlModel =
      model
        ?: Mockito.mock(NlModel::class.java).apply {
          whenever(modelDisplayName).thenReturn("displayName")
          val mockFile = Mockito.mock(VirtualFile::class.java)
          whenever(virtualFile).thenReturn(mockFile)
        }
    whenever(nlComponent.model).thenReturn(nlModel)

    whenever(nlComponent.tagName).thenReturn(tagName)

    val viewInfos = buildViewInfo()
    nlComponent.viewInfo = viewInfos

    return nlComponent
  }

  private fun buildViewInfo(): ViewInfo {
    viewId++
    val viewInfo = Mockito.mock(ViewInfo::class.java)
    val view = Mockito.mock(View::class.java)

    whenever(viewInfo.viewObject).thenReturn(view)
    whenever(view.id).thenReturn(viewId)
    return viewInfo
  }

  /**
   * Generate the [RenderResult] with appropriate [ViewInfo] as well as matching [ValidatorResult]
   * based on [NlModel].
   *
   * It creates a [ValidatorData.Issue] per [NlModel.treeReader.flattenComponents]
   */
  fun mockRenderResult(model: NlModel, injectedResult: ValidatorResult? = null): RenderResult {
    val result = Mockito.mock(RenderResult::class.java)
    val validatorResult = ValidatorResult.Builder()
    val viewInfos = ImmutableList.Builder<ViewInfo>()

    model.treeReader.components.forEach {
      generateResult(it, validatorResult)
      validatorResult.mIssues.add(createTestIssueBuilder().setSrcId(lastUsedIssueId).build())

      it?.viewInfo?.let { viewInfo -> viewInfos.add(viewInfo) }
    }
    if (injectedResult != null) {
      whenever(result.validatorResult).thenReturn(injectedResult)
    } else {
      whenever(result.validatorResult).thenReturn(validatorResult.build())
    }
    whenever(result.rootViews).thenReturn(viewInfos.build())

    val renderResult = Mockito.mock(Result::class.java)
    whenever(result.renderResult).thenReturn(renderResult)
    whenever(result.stats).thenReturn(RenderResultStats())
    whenever(renderResult.isSuccess).thenReturn(true)

    return result
  }

  /**
   * Creates a mocked [NlMode] with [size] number of [NlComponent]s. The created model has 1 root,
   * and 1-[size] children.
   */
  fun buildModel(size: Int): NlModel {
    val model = Mockito.mock(NlModel::class.java)
    val builder = ImmutableList.Builder<NlComponent>()
    if (size == 0) {
      val treeReader = Mockito.mock(NlTreeReader::class.java)
      whenever(treeReader.components).thenReturn(builder.build())
      whenever(model.treeReader).thenReturn(treeReader)
      return model
    }

    val root = buildNlComponent(model)
    val children = ArrayList<NlComponent>()

    builder.add(root)
    for (i in 1 until size) {
      val component = buildNlComponent(model)
      builder.add(component)
      children.add(component)
    }
    whenever(root.children).thenReturn(children)
    val treeReader = Mockito.mock(NlTreeReader::class.java)
    whenever(treeReader.components).thenReturn(builder.build())
    whenever(model.treeReader).thenReturn(treeReader)

    val module = Mockito.mock(Module::class.java)
    whenever(module.isDisposed).thenReturn(false)
    whenever(model.module).thenReturn(module)

    val configuration = Mockito.mock(Configuration::class.java)
    whenever(model.configuration).thenReturn(configuration)

    return model
  }

  /**
   * Create a scanner(aka validator) result derived from the passed components. Created result will
   * have source map created.
   */
  fun generateResult(
    component: NlComponent,
    builder: ValidatorResult.Builder? = null,
  ): ValidatorResult.Builder {
    val builder = builder ?: ValidatorResult.Builder()
    validatorIssueId += 1L
    builder.mSrcMap[validatorIssueId] = component.viewInfo?.viewObject as View
    return builder
  }

  companion object {

    /** Create a default issue builder with all the requirements. */
    fun createTestIssueBuilder(fix: ValidatorData.Fix? = null): ValidatorData.Issue.IssueBuilder {
      return ValidatorData.Issue.IssueBuilder()
        .setCategory("")
        .setType(ValidatorData.Type.ACCESSIBILITY)
        .setMsg("Test")
        .setLevel(ValidatorData.Level.ERROR)
        .setSrcId(-1)
        .setFix(fix)
        .setSourceClass("")
    }
  }
}
