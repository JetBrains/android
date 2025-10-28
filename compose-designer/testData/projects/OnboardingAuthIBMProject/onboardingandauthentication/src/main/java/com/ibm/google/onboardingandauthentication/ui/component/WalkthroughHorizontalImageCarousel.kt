/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.ibm.google.onboardingandauthentication.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ibm.google.onboardingandauthentication.R
import com.ibm.google.onboardingandauthentication.data.WalkthroughStepModel
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme

/**
 * Composable function to display a horizontal carousel of images.
 *
 * @param images List of WalkthroughStepModel resources to be displayed in the carousel.
 * @param modifier Modifier to be applied to the carousel container.
 * @param preferredItemWidth The preferred width of the carousel items.
 * @param maxSmallItemWidth The maximum width of smaller carousel items.
 * @param minSmallItemWidth The minimum width of smaller carousel items.
 * @param itemSpacing The spacing between carousel items.
 * @param contentScale The content scale to be applied to the images.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkthroughHorizontalImageCarousel(
  images: List<WalkthroughStepModel>,
  modifier: Modifier = Modifier,
  preferredItemWidth: Dp = 252.dp,
  maxSmallItemWidth: Dp = 56.dp,
  minSmallItemWidth: Dp = 56.dp,
  itemSpacing: Dp = 8.dp,
  contentScale: ContentScale = ContentScale.Crop,
) {
  val carouselState = rememberCarouselState(itemCount = { images.size })

  HorizontalMultiBrowseCarousel(
    state = carouselState,
    modifier = modifier,
    preferredItemWidth = preferredItemWidth,
    maxSmallItemWidth = maxSmallItemWidth,
    minSmallItemWidth = minSmallItemWidth,
    itemSpacing = itemSpacing,
    flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(carouselState),
  ) { index ->
    images[index].imageId?.let { imageResId ->
      Image(
        modifier = modifier.fillMaxSize().maskClip(RoundedCornerShape(24.dp)),
        painter = painterResource(id = imageResId),
        contentDescription = images[index].description,
        contentScale = contentScale,
      )
    }
  }
}

/**
 * Provides sample data for the [WalkthroughHorizontalImageCarousel] preview.
 *
 * This class implements [PreviewParameterProvider] to supply a sequence of
 * `List<WalkthroughStepModel>` instances. Each list represents a distinct set of images to be
 * displayed in the carousel preview. This allows testing the [WalkthroughHorizontalImageCarousel]
 * with different data sets using a single `@Preview` composable.
 */
class WalkthroughHorizontalImageCarouselPreviewProvider :
  PreviewParameterProvider<List<WalkthroughStepModel>> {
  override val values: Sequence<List<WalkthroughStepModel>> =
    sequenceOf(
      listOf(
        WalkthroughStepModel(
          imageId = R.drawable.ic_walkthrough_music,
          description = "Listen to relaxing music",
        ),
        WalkthroughStepModel(
          imageId = R.drawable.ic_walkthrough_blooming,
          description = "Take a moment to breathe and unwind",
        ),
        WalkthroughStepModel(
          imageId = R.drawable.ic_walkthrough_sun,
          description = "Go outside and get some sunshine",
        ),
      )
    )
}

@Preview(showBackground = true)
@Composable
private fun WalkthroughHorizontalImageCarouselPreview(
  @PreviewParameter(WalkthroughHorizontalImageCarouselPreviewProvider::class)
  images: List<WalkthroughStepModel>
) {
  OnBoardingAndAuthenticationTheme {
    WalkthroughHorizontalImageCarousel(
      modifier = Modifier.fillMaxWidth().height(351.dp),
      images = images,
    )
  }
}
