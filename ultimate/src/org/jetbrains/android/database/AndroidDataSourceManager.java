// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.database;

import com.intellij.database.Dbms;
import com.intellij.database.actions.DatabaseViewActions;
import com.intellij.database.dialects.DatabaseDialectEx;
import com.intellij.database.model.DasDataSource;
import com.intellij.database.psi.BasicDataSourceManager;
import com.intellij.database.util.DbImplUtil;
import com.intellij.database.util.DbSqlUtilCore;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidDataSourceManager extends BasicDataSourceManager<AndroidDataSource> {
  public static final String NOTIFICATION_GROUP_ID = "Android Data Source Manager";

  private final AndroidDataSourceStorage myStorage;

  public AndroidDataSourceManager(@NotNull Project project) {
    super(project, AndroidDataSourceStorage.getInstance(project).getDataSources());
    myStorage = AndroidDataSourceStorage.getInstance(project);
  }

  @Nullable
  @Override
  public DatabaseDialectEx getDatabaseDialect(@NotNull AndroidDataSource element) {
    return DbImplUtil.getDatabaseDialect(Dbms.SQLITE);
  }

  @Nullable
  @Override
  public Language getQueryLanguage(@NotNull AndroidDataSource element) {
    return DbSqlUtilCore.findSqlDialect(Dbms.SQLITE);
  }

  @Override
  public void renameDataSource(@NotNull AndroidDataSource element, @NotNull String name) {
    element.setName(name);
    updateDataSource(element);
  }

  @Override
  public boolean isMyDataSource(@NotNull Class<? extends DasDataSource> clazz) {
    return AndroidDataSource.class.isAssignableFrom(clazz);
  }

  @Override
  public boolean isLoading(@NotNull AndroidDataSource dataSource) {
    return false;
  }

  @Override
  public void addDataSource(@NotNull AndroidDataSource dataSource) {
    myStorage.addDataSource(dataSource);
    attachDataSource(dataSource);
  }

  @Override
  public void removeDataSource(@NotNull AndroidDataSource element) {
    myStorage.removeDataSource(element);
    detachDataSource(element);
  }

  @NotNull
  @Override
  public Configurable createDataSourceEditor(@NotNull AndroidDataSource element) {
    return new AndroidDataSourceConfigurable(this, myProject, element);
  }

  @Override
  public AnAction getCreateDataSourceAction(@NotNull Consumer<? super AndroidDataSource> consumer) {
    if (!ProjectFacetManager.getInstance(myProject).hasFacets(AndroidFacet.ID)) return null;
    return new DumbAwareAction("Android SQLite", null, AndroidIcons.Android) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(DatabaseViewActions.isDataSourceActionsEnabled(e));
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        AndroidDataSource result = new AndroidDataSource();
        result.setName(getTemplatePresentation().getText());
        result.resolveDriver();
        consumer.consume(result);
      }
    };
  }

  @Override
  public @NotNull AndroidDataSource createEmpty() {
    return new AndroidDataSource();
  }

  @NotNull
  @Override
  public AndroidDataSource copyDataSource(@NotNull String newName, @NotNull AndroidDataSource copyFrom) {
    AndroidDataSource result = copyFrom.copy(false);
    result.setName(newName);
    result.resolveDriver();
    return result;
  }

}
