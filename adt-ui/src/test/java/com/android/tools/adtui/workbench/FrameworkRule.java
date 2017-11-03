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
package com.android.tools.adtui.workbench;

import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.InternalDecorator;
import com.intellij.testFramework.EdtTestUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.picocontainer.PicoContainer;

import javax.swing.*;
import java.awt.*;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class FrameworkRule implements TestRule {
  @NotNull
  @Override
  public Statement apply(@NotNull Statement base, @NotNull Description description) {
    return new FrameworkStatement(base);
  }

  private static class FrameworkStatement extends Statement {
    private final Statement myBase;
    private final PropertiesComponent myPropertiesComponent;
    private final Disposable myDisposable;
    private final UISettings myUISettings;

    @Mock private Application myApplication;
    @Mock private PicoContainer myPicoContainer;
    @Mock private Project myProject;
    @Mock private PicoContainer myProjectPicoContainer;
    @Mock private ProjectManager myProjectManager;
    @Mock private ActionManagerEx myActionManager;
    @Mock private DataManager myDataManager;
    @Mock private DataContext myDataContext;
    @Mock private TransactionGuard myTransactionGuard;
    @Mock private WorkBenchManager myWorkBenchManager;
    @Mock private StartupManager myStartupManager;
    @Mock private DumbService myDumbService;
    @Mock private DetachedToolWindowManager myFloatingToolWindowManager;
    @Mock private FileEditorManager myFileEditorManager;
    @Mock private ToolWindowManager myToolWindowManager;
    @Mock private ActionPopupMenu myActionPopupMenu;
    @Mock private JPopupMenu myPopupMenu;

    public FrameworkStatement(@NotNull Statement base) {
      myBase = base;
      myPropertiesComponent = new PropertiesComponentMock();
      myDisposable = Disposer.newDisposable();
      myUISettings = new UISettings();
    }

    @Override
    public void evaluate() throws Throwable {
      before();
      try {
        EdtTestUtil.runInEdtAndWait(myBase::evaluate);
      }
      finally {
        after();
      }
    }

    private void before() {
      initMocks(this);
      ApplicationManager.setApplication(myApplication, myDisposable);
      when(myApplication.getComponent(ProjectManager.class)).thenReturn(myProjectManager);
      when(myApplication.getComponent(ActionManager.class)).thenReturn(myActionManager);
      when(myApplication.getComponent(DataManager.class)).thenReturn(myDataManager);
      when(myApplication.getPicoContainer()).thenReturn(myPicoContainer);
      when(myApplication.getAnyModalityState()).thenReturn(ModalityState.NON_MODAL);
      when(myApplication.isDisposed()).thenReturn(false);

      when(myPicoContainer.getComponentInstance(UISettings.class.getName())).thenReturn(myUISettings);
      when(myPicoContainer.getComponentInstance(TransactionGuard.class.getName())).thenReturn(myTransactionGuard);
      when(myPicoContainer.getComponentInstance(WorkBenchManager.class.getName())).thenReturn(myWorkBenchManager);
      when(myPicoContainer.getComponentInstance(PropertiesComponent.class.getName())).thenReturn(myPropertiesComponent);

      when(myProject.getPicoContainer()).thenReturn(myProjectPicoContainer);
      when(myProject.getComponent(DetachedToolWindowManager.class)).thenReturn(myFloatingToolWindowManager);
      when(myProject.getComponent(FileEditorManager.class)).thenReturn(myFileEditorManager);
      when(myProject.getComponent(ToolWindowManager.class)).thenReturn(myToolWindowManager);
      when(myProject.isDisposed()).thenReturn(false);

      when(myProjectPicoContainer.getComponentInstance(DumbService.class.getName())).thenReturn(myDumbService);
      when(myProjectPicoContainer.getComponentInstance(StartupManager.class.getName())).thenReturn(myStartupManager);

      when(myActionManager.getAction(InternalDecorator.TOGGLE_DOCK_MODE_ACTION_ID)).thenReturn(new SomeAction("Docked"));
      when(myActionManager.getAction(InternalDecorator.TOGGLE_FLOATING_MODE_ACTION_ID)).thenReturn(new SomeAction("Floating"));
      when(myActionManager.getAction(InternalDecorator.TOGGLE_SIDE_MODE_ACTION_ID)).thenReturn(new SomeAction("Split"));
      when(myActionManager.getAction(IdeActions.ACTION_CLEAR_TEXT)).thenReturn(new SomeAction("ClearText"));
      when(myActionManager.createActionPopupMenu(anyString(), any(ActionGroup.class))).thenReturn(myActionPopupMenu);
      when(myActionPopupMenu.getComponent()).thenReturn(myPopupMenu);

      //noinspection deprecation
      when(myDataManager.getDataContext()).thenReturn(myDataContext);
      when(myDataManager.getDataContext(any(Component.class))).thenReturn(myDataContext);
      when(myProjectManager.getOpenProjects()).thenReturn(new Project[]{myProject});
      when(myProjectManager.getDefaultProject()).thenReturn(myProject);
      Answer<Void> runRunnable = invocation -> {
        Object[] arguments = invocation.getArguments();
        Runnable runnable = (Runnable)arguments[0];
        runnable.run();
        return null;
      };
      doAnswer(runRunnable).when(myTransactionGuard).submitTransactionAndWait(any(Runnable.class));
      doAnswer(runRunnable).when(myStartupManager).runWhenProjectIsInitialized(any(Runnable.class));
      doAnswer(runRunnable).when(myApplication).invokeLater(any(Runnable.class));
    }

    private void after() {
      Disposer.dispose(myDisposable);
      TestAppManager.tearDown();
    }
  }

  private static class SomeAction extends AnAction {
    private SomeAction(@NotNull String title) {
      super(title);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
    }
  }

  private static class TestAppManager extends ApplicationManager {

    private static void tearDown() {
      ourApplication = null;
    }
  }
}
