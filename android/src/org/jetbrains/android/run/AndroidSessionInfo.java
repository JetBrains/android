package org.jetbrains.android.run;

import com.intellij.debugger.ui.DebuggerSessionTab;
import com.intellij.execution.ui.RunContentDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSessionInfo {
  private final RunContentDescriptor myDescriptor;
  private final AndroidExecutionState myState;
  private final String myExecutorId;
  private final DebuggerSessionTab myDebuggerSessionTab;

  public AndroidSessionInfo(@NotNull RunContentDescriptor descriptor,
                            @NotNull AndroidExecutionState state,
                            @NotNull String executorId,
                            @Nullable DebuggerSessionTab debuggerSessionTab) {
    myDescriptor = descriptor;
    myState = state;
    myExecutorId = executorId;
    myDebuggerSessionTab = debuggerSessionTab;
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

  @Nullable
  public DebuggerSessionTab getDebuggerSessionTab() {
    return myDebuggerSessionTab;
  }
}
