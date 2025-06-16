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
package main.utils.tooltips

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.TooltipMetrics
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle
import kotlin.time.Duration

object TooltipStyleFactory {
  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  fun createTooltipStyle(duration: Duration) = JewelTheme.tooltipStyle.metrics.let { TooltipStyle(JewelTheme.tooltipStyle.colors,
                                                                                                  TooltipMetrics(it.contentPadding,
                                                                                                                 duration, it.cornerSize,
                                                                                                                 it.borderWidth,
                                                                                                                 it.shadowSize,
                                                                                                                 it.placement))
  }
}