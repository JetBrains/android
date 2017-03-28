package org.jetbrains.android.database;

import com.intellij.database.DatabaseMessages;
import com.intellij.database.dataSource.DataSourceTemplate;
import com.intellij.database.dialects.DatabaseDialectEx;
import com.intellij.database.dialects.SqliteDialect;
import com.intellij.database.model.DatabaseSystem;
import com.intellij.database.psi.BasicDbPsiManager;
import com.intellij.database.psi.DbDataSource;
import com.intellij.database.psi.DbElement;
import com.intellij.database.psi.DbPsiFacade;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.GlobalUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDbManager extends BasicDbPsiManager<AndroidDataSource> {
  public static final String NOTIFICATION_GROUP_ID = "Android Data Source Manager";
  static final DataSourceTemplate DEFAULT_TEMPLATE = new AndroidDataSourceTemplate();

  public AndroidDbManager(@NotNull DbPsiFacade dbFacade) {
    super(dbFacade, AndroidDataSourceStorage.getInstance(dbFacade.getProject()).getDataSources());
  }

  @Nullable
  @Override
  public DatabaseDialectEx getDatabaseDialect(@NotNull DbDataSource element) {
    return SqliteDialect.INSTANCE;
  }

  @Override
  public void setDataSourceName(@NotNull DbDataSource element, String name) {
    if (!(element.getDelegate() instanceof AndroidDataSource)) throw new UnsupportedOperationException();
    final AndroidDataSource dataSource = (AndroidDataSource)element.getDelegate();
    dataSource.setName(name);
    myDbFacade.clearCaches(element);
  }

  @NotNull
  @Override
  public ModificationTracker getModificationTracker(@NotNull DbElement element) {
    return (AndroidDataSource)element.getDataSource().getDelegate();
  }

  @Override
  public void removeDataSource(DbDataSource element) {
    if (!(element.getDelegate() instanceof AndroidDataSource)) throw new UnsupportedOperationException();
    final AndroidDataSource dataSource = (AndroidDataSource)element.getDelegate();
    processAddOrRemove(dataSource, false);
  }

  @NotNull
  @Override
  public Configurable createDataSourceEditor(DbDataSource template) {
    if (!(template.getDelegate() instanceof AndroidDataSource)) throw new UnsupportedOperationException();
    AndroidDataSource dataSource = (AndroidDataSource)template.getDelegate();
    return new AndroidDataSourcePropertiesDialog(this, template.getProject(), dataSource);
  }

  @NotNull
  @Override
  public List<DataSourceTemplate> getDataSourceTemplates() {
    if (ProjectFacetManager.getInstance(myDbFacade.getProject()).hasFacets(AndroidFacet.ID)) {
      return Collections.singletonList(DEFAULT_TEMPLATE);
    }
    else {
      return Collections.emptyList();
    }
  }

  @Nullable
  @Override
  public DataSourceTemplate getDataSourceTemplate(DbDataSource element) {
    return DEFAULT_TEMPLATE;
  }

  public void processAddOrRemove(final AndroidDataSource dataSource, final boolean add) {
    final Project project = myDbFacade.getProject();

    final UndoableAction action = new GlobalUndoableAction() {
      public void undo() throws UnexpectedUndoException {
        if (add) {
          removeDataSourceInner(project, dataSource);
        }
        else {
          addDataSourceInner(project, dataSource);
        }
      }

      public void redo() throws UnexpectedUndoException {
        if (add) {
          addDataSourceInner(project, dataSource);
        }
        else {
          removeDataSourceInner(project, dataSource);
        }
      }
    };
    final String commandName = add ? DatabaseMessages.message("command.name.add.data.source")
                                   : DatabaseMessages.message("command.name.remove.data.source");
    new WriteCommandAction(project, commandName) {
      protected void run(@NotNull final Result result) throws Throwable {
        action.redo();
        UndoManager.getInstance(project).undoableActionPerformed(action);
      }

      @Override
      protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
        return UndoConfirmationPolicy.REQUEST_CONFIRMATION;
      }
    }.execute();
  }

  private void removeDataSourceInner(final Project project, final AndroidDataSource dataSource) {
    AndroidDataSourceStorage storage = AndroidDataSourceStorage.getInstance(project);
    storage.removeDataSource(dataSource);
    detachDataSource(dataSource);
    clearCaches(null);
  }

  private void addDataSourceInner(final Project project, final AndroidDataSource dataSource) {
    AndroidDataSourceStorage storage = AndroidDataSourceStorage.getInstance(project);
    storage.addDataSource(dataSource);
    attachDataSource(dataSource);
    clearCaches(null);
  }

  private void clearCaches(@Nullable final DatabaseSystem info) {
    myDbFacade.clearCaches(info != null ? myDbFacade.findDataSource(info.getUniqueId()) : null);
  }

  @Override
  public boolean canCreateDataSourceByFiles(Collection<VirtualFile> files) {
    return false;
  }

  @NotNull
  @Override
  public Collection<DbDataSource> createDataSourceByFiles(Collection<VirtualFile> files) {
    return Collections.emptyList();
  }

  @Override
  public void fireDataSourceUpdated(DbDataSource element) {
  }

  @Override
  public void addDataSource(@NotNull AndroidDataSource dataSource) {
    processAddOrRemove(dataSource, true);
  }

  private static class AndroidDataSourceTemplate implements DataSourceTemplate {
    @NotNull
    @Override
    public String getName() {
      return "Android SQLite";
    }

    @NotNull
    @Override
    public String getFullName() {
      return getName();
    }

    @NotNull
    @Override
    public List<DataSourceTemplate> getSubConfigurations() {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public DatabaseSystem createDataSource(@NotNull Project project, @Nullable DatabaseSystem copyFrom, @Nullable String newName) {
      AndroidDataSource result;
      if (copyFrom instanceof AndroidDataSource) {
        result = ((AndroidDataSource)copyFrom).copy();
      }
      else {
        result = new AndroidDataSource();
      }
      result.setName(StringUtil.notNullize(newName, getName()));
      result.resolveDriver();
      return result;
    }

    @Override
    public Icon getIcon(@IconFlags int flags) {
      return AndroidIcons.Android;
    }
  }
}
