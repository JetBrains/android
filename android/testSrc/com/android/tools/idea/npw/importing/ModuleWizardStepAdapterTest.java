/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.importing;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.StepListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.swing.*;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

public final class ModuleWizardStepAdapterTest {
  @Captor ArgumentCaptor<StepListener> myStepListener;
  @Mock WizardContext myContext;
  @Mock ModuleWizardStep myToWrap;
  ModuleWizardStepAdapter myStepAdapter;

  @Before
  public void setUp(){
    MockitoAnnotations.initMocks(this);

    when(myToWrap.getName()).thenReturn("Sample Step");
    myStepAdapter = new ModuleWizardStepAdapter(myContext, myToWrap);
  }

  @Test
  public void getName(){
    verify(myToWrap).getName();
    assertThat(myStepAdapter.getTitle()).isEqualTo(myToWrap.getName());
  }

  @Test
  public void getComponent(){
    when(myToWrap.getComponent()).thenReturn(mock(JComponent.class));
    myStepAdapter.getComponent();
    verify(myToWrap).getComponent();
  }

  @Test
  public void canGoForwardFalseOnException() throws ConfigurationException {
    verify(myToWrap).registerStepListener(myStepListener.capture());
    when(myToWrap.validate()).thenThrow(ConfigurationException.class);
    myStepListener.getValue().stateChanged();
    assertThat(myStepAdapter.canGoForward().get()).isFalse();
  }

  @Test
  public void canGoForwardFalseIfInvalid() throws ConfigurationException {
    verify(myToWrap).registerStepListener(myStepListener.capture());
    when(myToWrap.validate()).thenReturn(false);
    myStepListener.getValue().stateChanged();
    assertThat(myStepAdapter.canGoForward().get()).isFalse();
  }

  @Test
  public void canGoForwardTrueIfValid() throws ConfigurationException {
    verify(myToWrap).registerStepListener(myStepListener.capture());
    when(myToWrap.validate()).thenReturn(true);
    myStepListener.getValue().stateChanged();
    assertThat(myStepAdapter.canGoForward().get()).isTrue();
  }

  @Test
  public void onEntering() throws ConfigurationException {
    when(myToWrap.validate()).thenReturn(true);
    myStepAdapter.onEntering();
    verify(myToWrap).validate();
    assertThat(myStepAdapter.canGoForward().get()).isTrue();
  }

  @Test
  public void onProceeding() {
    myStepAdapter.onProceeding();
    verify(myToWrap).updateDataModel();
    verify(myToWrap).onStepLeaving();
  }

  @Test
  public void dispose() {
    Disposer.dispose(myStepAdapter);
    verify(myToWrap).disposeUIResources();
  }

  @Test
  public void onWizardFinished() throws CommitStepException{
    ModuleWizardStepAdapter.AdapterModel model = new ModuleWizardStepAdapter.AdapterModel(myToWrap);
    model.handleFinished();
    verify(myToWrap).onWizardFinished();
  }

  @Test
  public void onWizardFinishedLogsExceptions() throws CommitStepException{
    Logger testLogger = mock(Logger.class);
    ModuleWizardStepAdapterKt.setLogForTesting(testLogger);

    ModuleWizardStepAdapter.AdapterModel model = new ModuleWizardStepAdapter.AdapterModel(myToWrap);
    @NlsSafe String testMessage = "Test Message";
    doThrow(new CommitStepException(testMessage)).when(myToWrap).onWizardFinished();
    model.handleFinished();
    verify(testLogger).error(testMessage);

    ModuleWizardStepAdapterKt.setLogForTesting(null);
  }
}
