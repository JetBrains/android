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
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.uibuilder.model.NlComponentMixin
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.idea.validator.ValidatorResult
import com.google.common.collect.ImmutableList
import com.intellij.openapi.module.Module
import org.mockito.Mockito

/**
 * Helper class for testing [NlLayoutScanner].
 * It generates/mocks appropriate data needed for testing.
 */
class ScannerTestHelper {

  private var viewId = 0

  /** Returns the [View.getId] of the last generated [NlComponent] by [buildNlComponent] */
  val lastUsedViewId get() = viewId

  private var validatorIssueId = 0L

  /**
   * Returns [ValidatorData.Issue.mSrcId] that is used by the last
   * issue created by the helper thru [generateResult].
   */
  val lastUsedIssueId get() = validatorIssueId

  /**
   * Create a default component useful for testing.
   * It contains mocked view information.
   */
  fun buildNlComponent(model: NlModel? = null): NlComponent {
    val nlComponent = Mockito.mock(NlComponent::class.java)
    val mixin = NlComponentMixin(nlComponent)
    Mockito.`when`(nlComponent.mixin).thenReturn(mixin)
    model?.let {
      Mockito.`when`(nlComponent.model).thenReturn(model!!)
    }

    val viewInfos = buildViewInfo()
    nlComponent.viewInfo = viewInfos

    return nlComponent
  }

  private fun buildViewInfo(): ViewInfo {
    viewId++
    val viewInfo = Mockito.mock(ViewInfo::class.java)
    val view = Mockito.mock(View::class.java)

    Mockito.`when`(viewInfo.viewObject).thenReturn(view)
    Mockito.`when`(view.id).thenReturn(viewId)
    return viewInfo
  }

  /**
   * Generate the [RenderResult] with appropriate [ViewInfo] as well as
   * matching [ValidatorResult] based on [NlModel].
   *
   * It creates a [ValidatorData.Issue] per [NlModel.flattenComponents]
   */
  fun mockRenderResult(model: NlModel, injectedResult: ValidatorResult? = null):
    RenderResult {
    val result = Mockito.mock(RenderResult::class.java)
    val validatorResult = ValidatorResult.Builder()
    val viewInfos = ImmutableList.Builder<ViewInfo>()

    model.components.forEach {
      generateResult(it, validatorResult)
      validatorResult.mIssues.add(
        createTestIssueBuilder().setSrcId(lastUsedIssueId).build()
      )

      it?.viewInfo?.let { viewInfo -> viewInfos.add(viewInfo) }
    }
    if (injectedResult != null) {
      Mockito.`when`(result.validatorResult).thenReturn(injectedResult)
    } else {
      Mockito.`when`(result.validatorResult).thenReturn(validatorResult.build())
    }
    Mockito.`when`(result.rootViews).thenReturn(viewInfos.build())

    val renderResult = Mockito.mock(Result::class.java)
    Mockito.`when`(result.renderResult).thenReturn(renderResult)
    Mockito.`when`(renderResult.isSuccess).thenReturn(true)

    return result
  }

  /**
   * Creates a mocked [NlMode] with [size] number of [NlComponent]s.
   * The created model has 1 root, and 1-[size] children.
   */
  fun buildModel(size: Int): NlModel {
    val model = Mockito.mock(NlModel::class.java)
    val builder = ImmutableList.Builder<NlComponent>()
    if (size == 0) {
      Mockito.`when`(model.components).thenReturn(builder.build())
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
    Mockito.`when`(root.children).thenReturn(children)
    Mockito.`when`(model.components).thenReturn(builder.build())

    val module = Mockito.mock(Module::class.java)
    Mockito.`when`(module.isDisposed).thenReturn(false)
    Mockito.`when`(model.module).thenReturn(module)

    val configuration = Mockito.mock(Configuration::class.java)
    Mockito.`when`(model.configuration).thenReturn(configuration)

    return model
  }

  /**
   * Create a scanner(aka validator) result derived from the passed components.
   * Created result will have source map created.
   */
  fun generateResult(
    component: NlComponent,
    builder: ValidatorResult.Builder? = null) : ValidatorResult.Builder {
    val builder = builder ?: ValidatorResult.Builder()
    validatorIssueId += 1L
    builder.mSrcMap[validatorIssueId] = component.viewInfo?.viewObject as View
    return builder
  }

  companion object {

    /** Create a default issue builder with all the requirements. */
    fun createTestIssueBuilder(): ValidatorData.Issue.IssueBuilder {
      return ValidatorData.Issue.IssueBuilder()
        .setCategory("")
        .setType(ValidatorData.Type.ACCESSIBILITY)
        .setMsg("Test")
        .setLevel(ValidatorData.Level.ERROR)
        .setSrcId(-1)
        .setFix(ValidatorData.Fix(""))
        .setSourceClass("")
    }
  }
}
