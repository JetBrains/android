// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.database;

import com.intellij.ide.ApplicationInitializedListener;

final class AndroidDataSourceApplicationComponent implements ApplicationInitializedListener {
  @Override
  public void componentsInitialized() {
    AndroidRemoteDataBaseManager.getInstance().processRemovedProjects();
  }
}
