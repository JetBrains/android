package org.jetbrains.android.run;

import com.android.ddmlib.IDevice;
import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.*;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerManager;
import icons.AndroidIcons;
import org.jetbrains.android.logcat.AndroidLogcatView;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
* @author Eugene.Kudelevsky
*/
class LogcatExecutionConsole implements ExecutionConsoleEx, ObservableConsoleView, ConsoleView {
  @NonNls private static final String ANDROID_DEBUG_SELECTED_TAB_PROPERTY = "ANDROID_DEBUG_SELECTED_TAB_";

  private final Project myProject;
  private final AndroidLogcatView myToolWindowView;
  @NotNull private final ConsoleView myConsoleView;
  private final String myConfigurationId;

  LogcatExecutionConsole(Project project,
                         IDevice device,
                         @NotNull ConsoleView consoleView,
                         String configurationId) {
    myProject = project;
    myConsoleView = consoleView;
    myConfigurationId = configurationId;
    myToolWindowView = new AndroidLogcatView(project, device) {
      @Override
      protected boolean isActive() {
        final XDebugSession session = XDebuggerManager.getInstance(myProject).getDebugSession(LogcatExecutionConsole.this);
        if (session == null) {
          return false;
        }
        final Content content = session.getUI().findContent(AndroidDebugRunner.ANDROID_LOGCAT_CONTENT_ID);
        return content != null && content.isSelected();
      }
    };
    Disposer.register(this, myToolWindowView);
  }

  @Override
  public void buildUi(final RunnerLayoutUi layoutUi) {
    final Content consoleContent = layoutUi.createContent(DebuggerContentInfo.CONSOLE_CONTENT, getComponent(),
                                                          XDebuggerBundle.message("debugger.session.tab.console.content.name"),
                                                          AllIcons.Debugger.Console, getPreferredFocusableComponent());

    consoleContent.setCloseable(false);
    layoutUi.addContent(consoleContent, 1, PlaceInGrid.bottom, false);

    // todo: provide other icon
    final Content logcatContent = layoutUi.createContent(AndroidDebugRunner.ANDROID_LOGCAT_CONTENT_ID, myToolWindowView.getContentPanel(), "Logcat",
                                                         AndroidIcons.Android, getPreferredFocusableComponent());
    logcatContent.setCloseable(false);
    logcatContent.setSearchComponent(myToolWindowView.createSearchComponent());
    layoutUi.addContent(logcatContent, 2, PlaceInGrid.bottom, false);
    final String selectedTabProperty = ANDROID_DEBUG_SELECTED_TAB_PROPERTY + myConfigurationId;

    final String tabName = PropertiesComponent.getInstance().getValue(selectedTabProperty);
    Content selectedContent = logcatContent;

    if (tabName != null) {
      for (Content content : layoutUi.getContents()) {
        if (tabName.equals(content.getDisplayName())) {
          selectedContent = content;
        }
      }
    }
    layoutUi.getContentManager().setSelectedContent(selectedContent);

    layoutUi.addListener(new ContentManagerAdapter() {
      @Override
      public void selectionChanged(final ContentManagerEvent event) {
        final Content content = event.getContent();

        if (content.isSelected()) {
          PropertiesComponent.getInstance().setValue(selectedTabProperty, content.getDisplayName());
        }
        myToolWindowView.activate();
      }
    }, logcatContent);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        myToolWindowView.activate();
      }
    });
  }

  @Nullable
  @Override
  public String getExecutionConsoleId() {
    return "ANDROID_LOGCAT";
  }

  @Override
  public JComponent getComponent() {
    return myConsoleView.getComponent();
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myConsoleView.getPreferredFocusableComponent();
  }

  @Override
  public void dispose() {
  }

  @Override
  public void addChangeListener(@NotNull ChangeListener listener, @NotNull Disposable parent) {
    if (myConsoleView instanceof ObservableConsoleView) {
      ((ObservableConsoleView)myConsoleView).addChangeListener(listener, parent);
    }
  }

  @Override
  public void print(@NotNull String s, @NotNull ConsoleViewContentType contentType) {
    myConsoleView.print(s, contentType);
  }

  @Override
  public void clear() {
    myConsoleView.clear();
  }

  @Override
  public void scrollTo(int offset) {
    myConsoleView.scrollTo(offset);
  }

  @Override
  public void attachToProcess(ProcessHandler processHandler) {
    myConsoleView.attachToProcess(processHandler);
  }

  @Override
  public void setOutputPaused(boolean value) {
    myConsoleView.setOutputPaused(value);
  }

  @Override
  public boolean isOutputPaused() {
    return myConsoleView.isOutputPaused();
  }

  @Override
  public boolean hasDeferredOutput() {
    return myConsoleView.hasDeferredOutput();
  }

  @Override
  public void performWhenNoDeferredOutput(Runnable runnable) {
    myConsoleView.performWhenNoDeferredOutput(runnable);
  }

  @Override
  public void setHelpId(String helpId) {
    myConsoleView.setHelpId(helpId);
  }

  @Override
  public void addMessageFilter(Filter filter) {
    myConsoleView.addMessageFilter(filter);
  }

  @Override
  public void printHyperlink(String hyperlinkText, HyperlinkInfo info) {
    myConsoleView.printHyperlink(hyperlinkText, info);
  }

  @Override
  public int getContentSize() {
    return myConsoleView.getContentSize();
  }

  @Override
  public boolean canPause() {
    return myConsoleView.canPause();
  }

  @NotNull
  @Override
  public AnAction[] createConsoleActions() {
    return myConsoleView.createConsoleActions();
  }

  @Override
  public void allowHeavyFilters() {
    myConsoleView.allowHeavyFilters();
  }
}
