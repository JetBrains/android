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
package com.android.tools.idea.ui.resourcemanager.rendering

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.ide.common.resources.ResourceMergerItem
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceType
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.plugin.FrameworkDrawableRenderer
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth
import com.intellij.mock.MockVirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Dimension
import java.util.concurrent.CompletableFuture

class DrawableSlowPreviewProviderTest {
  @get:Rule
  var androidProjectRule = AndroidProjectRule.inMemory()

  val facet get() = androidProjectRule.fixture.module.androidFacet!!

  @Before
  fun setup() {
    androidProjectRule.fixture.testDataPath = getTestDataDirectory()
    androidProjectRule.fixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML)
  }

  @Test
  fun useDrawableRendererForFrameworkResourceValue() {
    val resourceResolver = Mockito.spy(createResourceResolver(facet))
    val drawableRenderer = Mockito.mock(FrameworkDrawableRenderer::class.java)
    FrameworkDrawableRenderer.setInstance(facet, drawableRenderer)
    val image = createTestImage()
    val name = "file"
    val resourceItem = ResourceMergerItem(name, ResourceNamespace.RES_AUTO, ResourceType.ATTR, null, null, "external")
    val mockFile = MockVirtualFile("${name}.png")

    // This would be a Drawable resource, represented by an Attribute resource (a Theme Attribute).
    val designAsset = DesignAsset(mockFile, emptyList(), ResourceType.DRAWABLE, name, resourceItem)

    // The 'Framework ResourceValue' that should be rendered by DrawableRenderer instead of DesignAssetRenderer.
    val frameworkResourceValue = ResourceValueImpl(ResourceNamespace.ANDROID, ResourceType.DRAWABLE, name, null)

    // Have the Attribute resource resolve to the framework resource value.
    whenever(resourceResolver.findItemInTheme(resourceItem.referenceToSelf)).thenReturn(frameworkResourceValue)

    // Have the drawable renderer return a valid image for our desired framework resource value.
    whenever(drawableRenderer.getDrawableRender(frameworkResourceValue, mockFile, Dimension(100, 100))).thenReturn(
      CompletableFuture.completedFuture(image)
    )
    val provider = DrawableSlowPreviewProvider(facet, resourceResolver, null)

    val result = provider.getSlowPreview(100, 100, designAsset)
    assertNotNull(result)
    Mockito.verify(drawableRenderer).getDrawableRender(eq(frameworkResourceValue), eq(mockFile), eq(Dimension(100, 100)))
    Truth.assertThat(result!!.getRGB(1, 1)).isEqualTo(0xff012345.toInt())
  }

  private fun createResourceResolver(androidFacet: AndroidFacet): ResourceResolver {
    val configurationManager = ConfigurationManager.getOrCreateInstance(androidFacet.module)
    val manifest = MergedManifestManager.getMergedManifestSupplier(androidFacet.module).get().get()
    val theme = manifest.manifestTheme ?: manifest.getDefaultTheme(null, null, null)
    return configurationManager.resolverCache.getResourceResolver(null, theme, FolderConfiguration.createDefault())
  }
}