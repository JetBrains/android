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
package com.android.tools.idea.vitals

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.insights.VariantData
import com.android.tools.idea.vitals.datamodel.VitalsConnection
import com.intellij.openapi.module.Module
import org.mockito.Mockito.`when`

private val MODULE1 = mock<Module>().apply { `when`(name).thenReturn("app1") }
private val MODULE2 = mock<Module>().apply { `when`(name).thenReturn("app2") }
val TEST_CONNECTION_1 =
  VitalsConnection("appId1", "Test App 1", VariantData(MODULE1, "variant1")) { name }
val TEST_CONNECTION_2 =
  VitalsConnection("appId2", "Test App 2", VariantData(MODULE2, "variant2")) { name }
val TEST_CONNECTION_3 = VitalsConnection("appId3", "Test App 3", null) { name }
