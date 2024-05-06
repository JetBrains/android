/*
 * Copyright (C) 2023 The Android Open Source Project
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
package org.jetbrains.android.refactoring

import com.android.testutils.junit4.OldAgpTest
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_74
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_80

@OldAgpTest(agpVersions = ["7.4.1"], gradleVersions = ["7.5"])
class MigrateBuildConfigFromGradlePropertiesTest74 : MigrateBuildConfigFromGradlePropertiesTest(AGP_74, false)

@OldAgpTest(agpVersions = ["8.0.2"], gradleVersions = ["8.0"])
class MigrateBuildConfigFromGradlePropertiesTest80 : MigrateBuildConfigFromGradlePropertiesTest(AGP_80, null)