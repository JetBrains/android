package org.jetbrains.android.database;

import com.intellij.database.DatabaseMessages;
import com.intellij.database.dialects.DatabaseDialectEx;
import com.intellij.database.dialects.SqliteDialect;
import com.intellij.database.model.DatabaseSystem;
import com.intellij.database.psi.BasicDbPsiManager;
import com.intellij.database.psi.DbPsiManager;
import com.intellij.database.util.DbSqlUtil;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.GlobalUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.sql.dialects.SqlLanguageDialect;
import com.intellij.util.PairConsumer;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDbManager extends BasicDbPsiManager<AndroidDataSource> {
  public static final String NOTIFICATION_GROUP_ID = "Android Data Source Manager";

  public AndroidDbManager(@NotNull Project project) {
    super(project, AndroidDataSourceStorage.getInstance(project).getDataSources());
  }

  @Nullable
  @Override
  public DatabaseDialectEx getDatabaseDialect(@NotNull DatabaseSystem element) {
    return SqliteDialect.INSTANCE;
  }

  @Nullable
  @Override
  public SqlLanguageDialect getSqlDialect(@NotNull DatabaseSystem element) {
    return DbSqlUtil.findSqlDialect(SqliteDialect.INSTANCE);
  }

  @Override
  public void setDataSourceName(@NotNull DatabaseSystem element, String name) {
    if (!(element instanceof AndroidDataSource)) throw new UnsupportedOperationException();
    final AndroidDataSource dataSource = (AndroidDataSource)element;
    dataSource.setName(name);
    updateDataSource((AndroidDataSource)element);
  }

  @Override
  public void removeDataSource(@NotNull DatabaseSystem element) {
    if (!(element instanceof AndroidDataSource)) throw new UnsupportedOperationException();
    final AndroidDataSource dataSource = (AndroidDataSource)element;
    processAddOrRemove(dataSource, false);
  }

  @NotNull
  @Override
  public Configurable createDataSourceEditor(@NotNull DatabaseSystem template) {
    if (!(template instanceof AndroidDataSource)) throw new UnsupportedOperationException();
    AndroidDataSource dataSource = (AndroidDataSource)template;
    return new AndroidDataSourceConfigurable(this, myProject, dataSource);
  }

  @Override
  public AnAction getCreateDataSourceAction(@NotNull PairConsumer<DbPsiManager, DatabaseSystem> consumer) {
    if (!ProjectFacetManager.getInstance(myProject).hasFacets(AndroidFacet.ID)) return null;
    return new DumbAwareAction("Android SQLite", null, AndroidIcons.Android) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        AndroidDataSource result = new AndroidDataSource();
        result.setName(getTemplatePresentation().getText());
        result.resolveDriver();
        consumer.consume(AndroidDbManager.this, result);
      }
    };
  }

  @NotNull
  @Override
  public DatabaseSystem copyDataSource(@NotNull String newName, @NotNull DatabaseSystem copyFrom) {
    AndroidDataSource result = ((AndroidDataSource)copyFrom).copy();
    result.setName(newName);
    result.resolveDriver();
    return result;
  }

  public void processAddOrRemove(final AndroidDataSource dataSource, final boolean add) {
    final UndoableAction action = new GlobalUndoableAction() {
      public void undo() {
        doIt(!add);
      }

      public void redo() {
        doIt(add);
      }

      private void doIt(boolean add) {
        if (add) {
          addDataSourceInner(myProject, dataSource);
        }
        else {
          removeDataSourceInner(myProject, dataSource);
        }
      }
    };
    try {
      WriteCommandAction.writeCommandAction(myProject)
                        .withName(add ? DatabaseMessages.message("command.name.add.data.source")
                                      : DatabaseMessages.message("command.name.remove.data.source"))
                        .withUndoConfirmationPolicy(UndoConfirmationPolicy.REQUEST_CONFIRMATION)
                        .run(() -> {
                          action.redo();
                          UndoManager.getInstance(myProject).undoableActionPerformed(action);
                        });
    }
    catch (UnexpectedUndoException e) {
      throw new RuntimeException(e);
    }
  }

  private void removeDataSourceInner(final Project project, final AndroidDataSource dataSource) {
    AndroidDataSourceStorage storage = AndroidDataSourceStorage.getInstance(project);
    storage.removeDataSource(dataSource);
    detachDataSource(dataSource);
  }

  private void addDataSourceInner(final Project project, final AndroidDataSource dataSource) {
    AndroidDataSourceStorage storage = AndroidDataSourceStorage.getInstance(project);
    storage.addDataSource(dataSource);
    attachDataSource(dataSource);
  }

  @Override
  public boolean canCreateDataSourceByFiles(@NotNull Collection<VirtualFile> files) {
    return false;
  }

  @NotNull
  @Override
  public Collection<AndroidDataSource> createDataSourceByFiles(@NotNull Collection<VirtualFile> files) {
    return Collections.emptyList();
  }

  @Override
  public void addDataSource(@NotNull AndroidDataSource dataSource) {
    processAddOrRemove(dataSource, true);
  }
}
