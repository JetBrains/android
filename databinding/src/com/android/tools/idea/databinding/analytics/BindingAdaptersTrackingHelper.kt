/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding.analytics

import com.android.tools.idea.databinding.DATA_BINDING_ANNOTATIONS
import com.android.tools.idea.databinding.BINDING_METHODS_ANNOTATION
import com.android.tools.idea.databinding.INVERSE_BINDING_METHODS_ANNOTATION
import com.google.wireless.android.sdk.stats.DataBindingEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.JavaProjectRootsUtil
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex
import com.intellij.psi.search.ProjectScope
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex

/**
 * Count all of the different types of binding adapter annotations listed on the official documentation:
 * https://developer.android.com/reference/android/databinding/Bindable
 */
internal fun trackBindingAdapters(project: Project): DataBindingEvent.DataBindingPollMetadata.BindingAdapterMetrics {
  val scope = JavaProjectRootsUtil.getScopeWithoutGeneratedSources(ProjectScope.getProjectScope(project), project)
  val annotationCount = DATA_BINDING_ANNOTATIONS
    .flatMap { JavaAnnotationIndex.getInstance().get(it, project, scope) + KotlinAnnotationsIndex.getInstance().get(it, project, scope) }
    .count()


  //  [KotlinAnnotationsIndex] does not handle nested annotations, for example it would not catch the BindingMethod inside @BindingMethods in:
  //  @BindingMethods(BindingMethod(type = View::class, attribute = "android:backgroundTint", method = "setBackgroundTintList"))
  //  Therefore it is necessary to visit each @BindingMethods annotation and count its children.
  val kotlinNestedAnnotationCount = listOf(BINDING_METHODS_ANNOTATION, INVERSE_BINDING_METHODS_ANNOTATION)
    .flatMap { KotlinAnnotationsIndex.getInstance().get(it, project, scope) }
    .flatMap { it.valueArguments }
    .count()

  return DataBindingEvent.DataBindingPollMetadata.BindingAdapterMetrics.newBuilder().setAdapterCount(
    annotationCount + kotlinNestedAnnotationCount).build()
}
