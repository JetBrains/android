package org.jetbrains.android.run;

import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.ui.RunContentDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSessionInfo {
  private final RunContentDescriptor myDescriptor;
  private final AndroidExecutionState myState;
  private final String myExecutorId;

  public AndroidSessionInfo(@NotNull RunContentDescriptor descriptor,
                            @NotNull AndroidExecutionState state,
                            @NotNull String executorId) {
    myDescriptor = descriptor;
    myState = state;
    myExecutorId = executorId;
  }

  @NotNull
  public RunContentDescriptor getDescriptor() {
    return myDescriptor;
  }

  @NotNull
  public AndroidExecutionState getState() {
    return myState;
  }

  @NotNull
  public String getExecutorId() {
    return myExecutorId;
  }

  public boolean isEmbeddable() {
    // TODO: Maybe also check that descriptor process handler is still running?
    return myExecutorId == DefaultDebugExecutor.EXECUTOR_ID && // We only embed to debug sessions.
           myState.getConsoleView() != null &&
           myState.getDevices().size() == 1;
  }
}
