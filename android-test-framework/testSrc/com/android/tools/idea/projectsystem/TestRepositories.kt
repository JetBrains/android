/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:JvmName("TestRepositories")

package com.android.tools.idea.projectsystem

import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.ide.common.repository.GoogleMavenArtifactId.ANDROIDX_CONSTRAINT_LAYOUT
import com.android.ide.common.repository.GoogleMavenArtifactId.CONSTRAINT_LAYOUT
import com.android.ide.common.repository.GoogleMavenArtifactId.FLEXBOX_LAYOUT
import com.android.ide.common.repository.GoogleMavenArtifactId.PLAY_SERVICES
import com.android.ide.common.repository.GoogleMavenArtifactId.PLAY_SERVICES_ADS
import com.android.ide.common.repository.GoogleMavenArtifactId.PLAY_SERVICES_MAPS
import com.android.ide.common.repository.GoogleMavenArtifactId.PLAY_SERVICES_WEARABLE

/**
 * A collection of repos for dependencies used during testing.
 */
@JvmField
val PLATFORM_SUPPORT_LIBS = GoogleMavenArtifactId.values()
  .filter { it.isPlatformSupportLibrary }
  .map { it.getCoordinate("+") }

@JvmField
val NON_PLATFORM_SUPPORT_LAYOUT_LIBS = listOf(CONSTRAINT_LAYOUT, ANDROIDX_CONSTRAINT_LAYOUT, FLEXBOX_LAYOUT)
  .map { it.getCoordinate("+") }

@JvmField
val GOOGLE_PLAY_SERVICES = listOf(PLAY_SERVICES, PLAY_SERVICES_ADS, PLAY_SERVICES_WEARABLE, PLAY_SERVICES_MAPS)
  .map { it.getCoordinate("+") }
