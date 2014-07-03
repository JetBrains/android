package org.jetbrains.android.database;

import com.intellij.database.dataSource.DataSourceTemplate;
import com.intellij.database.dataSource.LocalDataSource;
import com.intellij.database.model.info.DataSourceInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ui.classpath.SimpleClasspathElement;
import com.intellij.util.ui.classpath.SimpleClasspathElementFactory;
import com.intellij.util.xmlb.annotations.Tag;
import icons.AndroidIcons;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidDataSource extends LocalDataSource implements DataSourceInfo, ModificationTracker {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.database.AndroidDataSource");

  private State myState = new State();

  public AndroidDataSource(@Nullable String name) {
    super(name, "org.sqlite.JDBC", "", null, null);
  }

  @NotNull
  public State getState() {
    return myState;
  }

  @NotNull
  public State buildFullState() {
    myState.setName(getName());
    myState.setClasspathElements(serializeClasspathElements());
    return myState;
  }

  public void loadState(@NotNull State state) {
    myState = state;
    setName(state.getName());
    setClasspathElements(deserializeClasspathElements());
    resetUrl();
  }

  @NotNull
  private Element[] serializeClasspathElements() {
    final List<SimpleClasspathElement> elements = getClasspathElements();

    if (elements.isEmpty()) {
      return new Element[0];
    }
    final Element[] serializedElements = new Element[elements.size()];
    int i = 0;

    for (SimpleClasspathElement element : elements) {
      final Element serializedElement = new Element("element");
      try {
        element.serialize(serializedElement);
      }
      catch (IOException e) {
        LOG.warn(e);
      }
      serializedElements[i++] = serializedElement;
    }
    return serializedElements;
  }

  @NotNull
  private List<SimpleClasspathElement> deserializeClasspathElements() {
    final Element[] serializedElements = myState.getClasspathElements();

    if (serializedElements == null || serializedElements.length == 0) {
      return Collections.emptyList();
    }
    final List<SimpleClasspathElement> elements = new ArrayList<SimpleClasspathElement>(serializedElements.length);

    for (Element serializedElement : serializedElements) {
      elements.addAll(SimpleClasspathElementFactory.createElements(null, serializedElement));
    }
    return elements;
  }

  void resetUrl() {
    setUrl(buildUrl());
  }

  @NotNull
  public String buildUrl() {
    return "jdbc:sqlite:" + FileUtil.toSystemDependentName(FileUtil.toCanonicalPath(buildLocalDbFileOsPath()));
  }

  @NotNull
  public String buildLocalDbFileOsPath() {
    final State state = getState();
    return AndroidRemoteDataBaseManager.buildLocalDbFileOsPath(
      state.getDeviceId(), state.getPackageName(), state.getDatabaseName(), state.isExternal());
  }

  @Override
  public long getModificationCount() {
    return 0;
  }

  @NotNull
  public AndroidDataSource copy() {
    final AndroidDataSource newSource = new AndroidDataSource(getName());
    final State newState = newSource.getState();
    final State state = buildFullState();
    newState.setName(state.getName());
    newState.setDeviceId(state.getDeviceId());
    newState.setPackageName(state.getPackageName());
    newState.setDatabaseName(state.getDatabaseName());
    newState.setExternal(state.isExternal());
    newState.setClasspathElements(cloneElementsArray(state.getClasspathElements()));
    newSource.resetUrl();
    return newSource;
  }

  @NotNull
  private static Element[] cloneElementsArray(@NotNull Element[] list) {
    final Element[] copy = new Element[list.length];

    for (int i = 0; i < list.length; i++) {
      copy[i] = list[i].clone();
    }
    return copy;
  }

  @Override
  public Icon getBaseIcon() {
    return AndroidIcons.Android;
  }

  @Override
  public DataSourceTemplate getTemplate() {
    return AndroidDbManager.DEFAULT_TEMPLATE;
  }

  @Override
  public boolean equalConfiguration(@NotNull LocalDataSource o) {
    if (!(o instanceof AndroidDataSource)) return super.equalConfiguration(o);

    State s = ((AndroidDataSource)o).getState();
    if (!Comparing.equal(myState.myDeviceId, s.myDeviceId)) return false;
    if (!Comparing.equal(myState.myPackageName, s.myPackageName)) return false;
    if (!Comparing.equal(myState.myDatabaseName, s.myDatabaseName)) return false;
    if (!Comparing.equal(myState.myExternal, s.myExternal)) return false;
    if (!Comparing.equal(myState.myExternal, s.myExternal)) return false;

    return true;
  }

  @Tag("data-source")
  public static class State {
    private String myName = "";
    private String myDeviceId = "";
    private String myPackageName = "";
    private String myDatabaseName = "";
    private boolean myExternal = false;
    private Element[] myClasspathElements = new Element[0];

    public String getDeviceId() {
      return myDeviceId;
    }

    public String getDatabaseName() {
      return myDatabaseName;
    }

    public String getPackageName() {
      return myPackageName;
    }

    public String getName() {
      return myName;
    }

    public boolean isExternal() {
      return myExternal;
    }

    @Tag("classpath-elements")
    public Element[] getClasspathElements() {
      return myClasspathElements;
    }

    public void setDeviceId(String deviceId) {
      myDeviceId = deviceId;
    }

    public void setDatabaseName(String databaseName) {
      myDatabaseName = databaseName;
    }

    public void setPackageName(String packageName) {
      myPackageName = packageName;
    }

    public void setExternal(boolean external) {
      myExternal = external;
    }

    public void setName(String name) {
      myName = name;
    }

    public void setClasspathElements(Element[] classpathElements) {
      myClasspathElements = classpathElements;
    }
  }
}