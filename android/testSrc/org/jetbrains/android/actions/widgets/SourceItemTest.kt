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
package org.jetbrains.android.actions.widgets

import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.module.Module
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.picocontainer.PicoContainer
import kotlin.test.assertEquals

class SourceItemTest {

  private lateinit var sourceProviderMock: NamedIdeaSourceProvider
  private lateinit var moduleMock: Module

  @Before
  fun setup() {
    sourceProviderMock = Mockito.mock(NamedIdeaSourceProvider::class.java)
    moduleMock = Mockito.mock(Module::class.java)
    val picoContainer = Mockito.mock(PicoContainer::class.java)
    val propertyManager = Mockito.mock(ExternalSystemModulePropertyManager::class.java)
    Mockito.`when`(picoContainer.getComponentInstance(Mockito.any())).thenReturn(propertyManager)
    Mockito.`when`(moduleMock.picoContainer).thenReturn(picoContainer)
  }

  @Test
  fun displayableResourceDir() {
    Mockito.`when`(moduleMock.moduleFilePath).thenReturn("/projects/projectName/moduleName/module.iml")
    assertEquals("src/main/res",
                 SourceSetItem.create(
                   sourceProviderMock,
                   moduleMock,
                   "file:///projects/projectName/moduleName/src/main/res").displayableResDir)
  }

  @Test
  fun displayableResourceDir2() {
    Mockito.`when`(moduleMock.moduleFilePath).thenReturn("/projects/projectName/.idea/moduleName/module.iml")
    assertEquals("moduleName/src/main/res",
                 SourceSetItem.create(
                   sourceProviderMock,
                   moduleMock,
                   "file:///projects/projectName/moduleName/src/main/res").displayableResDir)
  }

  @Test
  fun displayableResourceDir3() {
    Mockito.`when`(moduleMock.moduleFilePath).thenReturn("/madeUpDir/module.iml")
    assertEquals("...ctName/moduleName/src/main/res",
                 SourceSetItem.create(
                   sourceProviderMock,
                   moduleMock,
                   "file:///projects/projectName/moduleName/src/main/res").displayableResDir)
  }
}