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
package com.android.tools.idea.layoutinspector.resource

import com.android.annotations.concurrency.Slow
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.ResourceResolver.MAX_RESOURCE_INDIRECTION
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.layoutinspector.common.StringTable
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.resource.data.AppContext
import com.android.tools.idea.res.RESOURCE_ICON_SIZE
import com.android.tools.idea.res.parseColor
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.ClassUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ColorIcon
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Dimension
import javax.swing.Icon

/**
 * Utility for looking up resources in a project.
 *
 * This class contains facilities for finding property values and to navigate to
 * the property definition or property assignment.
 */
class ResourceLookup(private val project: Project) {
  private val composeResolver = LambdaResolver(project)

  @VisibleForTesting
  var resolver: ResourceLookupResolver? = null
    private set

  val hasResolver: Boolean
    get() = resolver != null

  /**
   * The dpi of the device we are currently inspecting or <code>null</code> if unknown.
   *
   * This is unknown for older saved snapshots.
   */
  var dpi: Int? = null
    @VisibleForTesting set

  /**
   * The fontScale currently used on the device or <code>null</code> if unknown.
   *
   * This is unknown for the legacy client and older saved snapshots.
   */
  var fontScale: Float? = null
    @VisibleForTesting set

  /**
   * The screen dimension in pixels <code>null</code> if unknown.
   *
   * This is unknown for the legacy client and older saved snapshots.
   */
  var screenDimension: Dimension? = null
    @VisibleForTesting set

  /**
   * Updates the configuration after a possible configuration change detected on the device.
   */
  @Slow
  fun updateConfiguration(
    folderConfig: FolderConfiguration,
    fontScaleFromConfig: Float,
    appContext: AppContext,
    stringTable: StringTable,
    process: ProcessDescriptor
  ) {
    dpi = folderConfig.densityQualifier?.value?.dpiValue?.takeIf { it > 0 }
    fontScale = fontScaleFromConfig.takeIf { it > 0f }
    resolver = createResolver(folderConfig, appContext, stringTable, process)
    screenDimension = Dimension(appContext.screenWidth, appContext.screenHeight).takeIf { it.height > 0 && it.width > 0 }
  }

  /**
   * Update the configuration after a legacy reload, or snapshot load.
   */
  fun updateConfiguration(deviceDpi: Int?, deviceFontScale: Float? = null, screenSize: Dimension? = null) {
    dpi = deviceDpi?.takeIf { it > 0 }
    fontScale = deviceFontScale?.takeIf { it > 0f }
    resolver = null
    screenDimension = screenSize
  }

  @Slow
  private fun createResolver(
    folderConfig: FolderConfiguration,
    appContext: AppContext,
    stringTable: StringTable,
    process: ProcessDescriptor
  ): ResourceLookupResolver? {
    val facet = ReadAction.compute<AndroidFacet?, RuntimeException> { findFacetFromPackage(project, process.name) } ?: return null
    val theme = appContext.theme.createReference(stringTable)
    val themeStyle = mapReference(facet, theme)?.resourceUrl?.toString() ?: return null
    val mgr = ConfigurationManager.getOrCreateInstance(facet)
    val cache = mgr.resolverCache
    val resourceResolver = ReadAction.compute<ResourceResolver, RuntimeException> {
      cache.getResourceResolver(mgr.target, themeStyle, folderConfig)
    }
    return ResourceLookupResolver(project, facet, folderConfig, resourceResolver)
  }

  /**
   * Find the file locations for a [property].
   *
   * The list of file locations will start from [InspectorPropertyItem.source]. If that is a reference
   * the definition of that reference will be next etc.
   * The [max] guards against recursive indirection in the resources.
   * Each file location is specified by:
   *  - a string containing the file name and a line number
   *  - a [Navigatable] that can be used to goto the source location
   */
  fun findFileLocations(property: InspectorPropertyItem, view: ViewNode, max: Int = MAX_RESOURCE_INDIRECTION): List<SourceLocation> =
    resolver?.findFileLocations(property, view, property.source, max) ?: emptyList()

  /**
   * Find the location of the specified [view].
   */
  fun findFileLocation(view: ViewNode): SourceLocation? =
    resolver?.findFileLocation(view)

  /**
   * Find the attribute value from resource reference.
   */
  fun findAttributeValue(property: InspectorPropertyItem, view: ViewNode, location: ResourceReference): String? =
    resolver?.findAttributeValue(property, view, location)

  /**
   * Find the lambda source location.
   */
  fun findLambdaLocation(
    packageName: String,
    fileName: String,
    lambdaName: String,
    functionName: String,
    startLine: Int,
    endLine: Int
  ): SourceLocation =
    composeResolver.findLambdaLocation(packageName, fileName, lambdaName, functionName, startLine, endLine)

  /**
   * Find the source navigatable of a composable function.
   */
  @Slow
  fun findComposableNavigatable(composable: ComposeViewNode): Navigatable? =
    composeResolver.findComposableNavigatable(composable)

  /**
   * Find the icon from this drawable property.
   */
  fun resolveAsIcon(property: InspectorPropertyItem, view: ViewNode): Icon? {
    resolver?.resolveAsIcon(property, view)?.let { return it }
    val value = property.value
    val color = value?.let { parseColor(value) } ?: return null
    return JBUIScale.scaleIcon(ColorIcon(RESOURCE_ICON_SIZE, color, false))
  }

  /**
   * Convert a class name to a source location.
   */
  fun resolveClassNameAsSourceLocation(className: String): SourceLocation? {
    val psiClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project))
    val navigatable = psiClass?.let { findNavigatable(psiClass) } ?: return null
    val source = ClassUtil.extractClassName(className)
    return SourceLocation(source, navigatable)
  }

  /**
   * Is this attribute a dimension according to the resource manager.
   */
  @Slow
  fun isDimension(view: ViewNode, attributeName: String): Boolean =
    ReadAction.compute<Boolean, Nothing> { resolver?.isDimension(view, attributeName) ?: false }
}
