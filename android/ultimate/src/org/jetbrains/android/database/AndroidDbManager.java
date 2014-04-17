package org.jetbrains.android.database;

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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.persistence.DatabaseMessages;
import com.intellij.persistence.database.DataSourceInfo;
import com.intellij.persistence.database.DataSourceTemplate;
import com.intellij.persistence.database.dialects.DatabaseDialect;
import com.intellij.persistence.database.dialects.SqliteDialect;
import com.intellij.persistence.database.psi.*;
import gnu.trove.THashMap;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDbManager extends DbPsiManagerSpi {
  public static final String NOTIFICATION_GROUP_ID = "Android Data Source Manager";
  static final DataSourceTemplate DEFAULT_TEMPLATE = new AndroidDataSourceTemplate();

  private final static Key<Map<AndroidDataSource, DbDataSourceElement>> DS_MAP_KEY = Key.create("ANDROID_DATASOURCE_MAP_KEY");

  private final DbPsiFacade myDbFacade;

  public AndroidDbManager(@NotNull DbPsiFacade dbFacade) {
    myDbFacade = dbFacade;
    initElementMap(myDbFacade, this);
  }

  private static Map<AndroidDataSource, DbDataSourceElement> getElementsMap(final DbPsiFacade facade) {
    return facade.getProjectElement().getUserData(DS_MAP_KEY);
  }

  private static void initElementMap(final DbPsiFacade facade, final AndroidDbManager manager) {
    final THashMap<AndroidDataSource, DbDataSourceElement> map = new THashMap<AndroidDataSource, DbDataSourceElement>();
    for (final AndroidDataSource source : AndroidDataSourceStorage.getInstance(facade.getProject()).getDataSources()) {
      map.put(source, createDataSourceElement(facade, manager, source));
    }
    facade.getProjectElement().putUserData(DS_MAP_KEY, Collections.synchronizedMap(map));
  }

  @Override
  public boolean isDataSourceElementValid(@NotNull DbDataSourceElement element) {
    return getElementsMap(myDbFacade).containsKey((AndroidDataSource)element.getDelegate());
  }

  @Nullable
  @Override
  public DatabaseDialect getDatabaseDialect(@NotNull DbDataSourceElement element) {
    return SqliteDialect.INSTANCE;
  }

  @Override
  public void setDataSourceName(@NotNull DbDataSourceElement element, String name) {
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
  public List<DbDataSourceElement> getDataSources() {
    return new ArrayList<DbDataSourceElement>(getElementsMap(myDbFacade).values());
  }

  @Override
  public void removeDataSource(DbDataSourceElement element) {
    if (!(element.getDelegate() instanceof AndroidDataSource)) throw new UnsupportedOperationException();
    final AndroidDataSource dataSource = (AndroidDataSource)element.getDelegate();
    processAddOrRemove(dataSource, false);
  }

  @NotNull
  @Override
  public Configurable createDataSourceEditor(DbDataSourceElement template) {
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
      protected void run(final Result result) throws Throwable {
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
    final DbPsiFacade facade = DbPsiFacade.getInstance(project);
    final Map<AndroidDataSource, DbDataSourceElement> elementMap = getElementsMap(facade);
    final AndroidDataSourceStorage storage = AndroidDataSourceStorage.getInstance(project);
    storage.removeDataSource(dataSource);
    elementMap.remove(dataSource);
    clearCaches(null);
  }

  private void addDataSourceInner(final Project project, final AndroidDataSource dataSource) {
    final DbPsiFacade facade = DbPsiFacade.getInstance(project);
    final Map<AndroidDataSource, DbDataSourceElement> elementMap = getElementsMap(facade);
    final AndroidDataSourceStorage storage = AndroidDataSourceStorage.getInstance(project);
    storage.addDataSource(dataSource);
    elementMap.put(dataSource, createDataSourceElement(facade, this, dataSource));
    clearCaches(null);
  }

  private void clearCaches(@Nullable final DataSourceInfo info) {
    myDbFacade.clearCaches(info != null ? myDbFacade.findDataSource(info.getUniqueId()) : null);
  }

  private static DbDataSourceElement createDataSourceElement(final DbPsiFacade facade,
                                                             final AndroidDbManager manager,
                                                             final AndroidDataSource source) {
    return ((DbPsiFacadeImpl)facade).createDataSourceWrapperElement(source, manager);
  }

  @Override
  public boolean canCreateDataSourceByFiles(Collection<VirtualFile> files) {
    return false;
  }

  @NotNull
  @Override
  public Collection<DbDataSourceElement> createDataSourceByFiles(Collection<VirtualFile> files) {
    return Collections.emptyList();
  }

  @Override
  public void fireDataSourceUpdated(DbDataSourceElement element) {
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
    public DataSourceInfo createDataSource(@NotNull Project project, @Nullable DataSourceInfo copyFrom, @Nullable String newName) {
      AndroidDataSource result;
      if (copyFrom instanceof AndroidDataSource) {
        result = ((AndroidDataSource)copyFrom).copy();
      }
      else {
        result = new AndroidDataSource("");
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
