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

package org.jetbrains.android.refactoring.runsGradle

import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import org.jetbrains.android.refactoring.MigrateResValuesFromGradlePropertiesTest

// TODO (b/366029616): enable this test when the current AGP version is at 9.0.0 or above
// When AGP_CURRENT no longer supports android.defaults.buildfeatures.buildconfig at all, this test can be removed.
//class MigrateResValuesFromGradlePropertiesTestCurrent : MigrateResValuesFromGradlePropertiesTest(AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT, null)