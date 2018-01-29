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
package org.jetbrains.android.actions;

import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.actions.CreateResourceDirectoryDialogBase.ValidatorFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertNull;

public final class CreateResourceDirectoryDialogTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private CreateResourceDirectoryDialog myDialog;

  @Before
  public void initCreateResourceDirectoryDialog() {
    ValidatorFactory factory = Mockito.mock(ValidatorFactory.class);

    ApplicationManager.getApplication().invokeAndWait(
      () -> myDialog = new CreateResourceDirectoryDialog(myRule.getProject(), myRule.getModule(), null, null, null, factory));
  }

  @After
  public void disposeOfCreateResourceDirectoryDialog() {
    ApplicationManager.getApplication().invokeAndWait(() -> Disposer.dispose(myDialog.getDisposable()));
  }

  @Test
  public void doValidateReturnsNullWhenDirectoryNameEqualsLayout() {
    myDialog.getDirectoryNameTextField().setText("layout");
    assertNull(myDialog.doValidate());
  }
}
