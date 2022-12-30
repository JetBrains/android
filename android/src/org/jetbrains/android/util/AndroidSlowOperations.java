// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android.util;

import static com.intellij.util.SlowOperations.allowSlowOperations;

import com.android.tools.idea.IdeInfo;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

public final class AndroidSlowOperations {
  public static <T, E extends Throwable> T allowSlowOperationsInIdea(@NotNull ThrowableComputable<T, E> computable) throws E {
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // DO NOT delete this "if", and DO NOT replace call site with SlowOperations.allowSlowOperations
    // This is true slow operation that has to be removed from the EDT thread. In Android plugin this has to be done in Android Studio
    // first, and then has to be cherry-picked to other JB IDEs via regular android plugin merge procedure.
    //
    // When the problem is fixed, allowSlowOperationsInIdea should be removed at the call site.
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    if (IdeInfo.getInstance().isAndroidStudio()) {
      return computable.compute();
    } else {
      return allowSlowOperations(computable);
    }
  }

  public static <E extends Throwable> void allowSlowOperationsInIdea(@NotNull ThrowableRunnable<E> runnable) throws E {
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // DO NOT delete this "if", and DO NOT replace call site with SlowOperations.allowSlowOperations
    // This is true slow operation that has to be removed from the EDT thread. In Android plugin this has to be done in Android Studio
    // first, and then has to be cherry-picked to other JB IDEs via regular android plugin merge procedure.
    //
    // When the problem is fixed, allowSlowOperationsInIdea should be removed at the call site.
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    if (IdeInfo.getInstance().isAndroidStudio()) {
      runnable.run();
    } else {
      allowSlowOperations(runnable);
    }
  }
}
