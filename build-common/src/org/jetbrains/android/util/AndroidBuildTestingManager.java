package org.jetbrains.android.util;

import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidBuildTestingManager {

  private static AndroidBuildTestingManager ourTestingManager;

  private final MyCommandExecutor myCommandExecutor;

  private AndroidBuildTestingManager(@NotNull MyCommandExecutor executor) {
    myCommandExecutor = executor;
  }

  @Nullable
  public static AndroidBuildTestingManager getTestingManager() {
    return ourTestingManager;
  }

  @NotNull
  public MyCommandExecutor getCommandExecutor() {
    return myCommandExecutor;
  }

  public interface MyCommandExecutor {
    @NotNull
    Process createProcess(@NotNull String[] args, @NotNull Map<? extends String, ? extends String> environment);

    void log(@NotNull String s);
  }
}
