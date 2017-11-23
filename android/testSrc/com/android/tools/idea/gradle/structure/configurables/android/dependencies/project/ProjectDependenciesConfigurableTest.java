/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.PsAllModulesFakeModule;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.options.ConfigurationException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class ProjectDependenciesConfigurableTest {

  private PsContext myContext;
  private PsAllModulesFakeModule myAllModules;
  private ProjectDependenciesConfigurable myConfigurable;

  @Before
  public void setUp() {
    myAllModules = mock(PsAllModulesFakeModule.class);
    myContext = mock(PsContext.class);
    myConfigurable = new ProjectDependenciesConfigurable(myAllModules, myContext, ImmutableList.of());
    reset(myAllModules);  // Forget getName() unverified calls.
  }

  @Test
  public void testApply() throws ConfigurationException {
    myConfigurable.apply();
    verify(myAllModules, only()).appplyChanges();
  }

  @Test
  public void testIsModified() throws ConfigurationException {
    when(myAllModules.isModified()).thenReturn(true);
    assertThat(myConfigurable.isModified(), is(true));

    when(myAllModules.isModified()).thenReturn(false);
    assertThat(myConfigurable.isModified(), is(false));
  }

}