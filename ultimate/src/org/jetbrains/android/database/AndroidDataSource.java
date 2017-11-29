package org.jetbrains.android.database;

import com.intellij.database.dataSource.LocalDataSource;
import com.intellij.database.model.DatabaseSystem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.classpath.SimpleClasspathElement;
import com.intellij.util.ui.classpath.SimpleClasspathElementFactory;
import com.intellij.util.xmlb.annotations.Tag;
import icons.AndroidIcons;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidDataSource extends LocalDataSource implements DatabaseSystem, ModificationTracker {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.database.AndroidDataSource");

  private State myState = new State();

  public AndroidDataSource() {
    super("", "org.sqlite.JDBC", "", null, null);
  }

  @NotNull
  public State getState() {
    return myState;
  }

  @NotNull
  public State buildFullState() {
    myState.name = getName();
    myState.comment = getComment();
    //myState.uuid = getUniqueId();
    myState.classpathElements = serializeClasspathElements();
    return myState;
  }

  public void loadState(@NotNull State state) {
    myState = state;
    setName(state.name);
    setComment(state.comment);
    // todo persist uuid must be preserved between sessions
    //setUniqueId(state.uuid);
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
    final Element[] serializedElements = myState.classpathElements;

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
    String path = buildLocalDbFileOsPath();
    return StringUtil.isEmpty(path) ? "" : "jdbc:sqlite:" + FileUtil.toSystemDependentName(FileUtil.toCanonicalPath(path));
  }

  @NotNull
  public String buildLocalDbFileOsPath() {
    final State state = getState();
    return AndroidRemoteDataBaseManager.buildLocalDbFileOsPath(
      state.deviceId, state.packageName, state.databaseName, state.external);
  }

  @Override
  public long getModificationCount() {
    return 0;
  }

  @NotNull
  public AndroidDataSource copy() {
    AndroidDataSource newSource = new AndroidDataSource();
    newSource.setName(getName());
    newSource.setDatabaseDriver(getDatabaseDriver());
    State newState = newSource.getState();
    State state = buildFullState();
    newState.name = state.name;
    newState.deviceId = state.deviceId;
    newState.packageName = state.packageName;
    newState.databaseName = state.databaseName;
    newState.external = state.external;
    newState.classpathElements = cloneElementsArray(state.classpathElements);
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
  public boolean equalConfiguration(@NotNull LocalDataSource o) {
    if (!(o instanceof AndroidDataSource)) return super.equalConfiguration(o);
    if (!Comparing.equal(getComment(), o.getComment())) return false;

    State s = ((AndroidDataSource)o).getState();
    if (!Comparing.equal(myState.deviceId, s.deviceId)) return false;
    if (!Comparing.equal(myState.packageName, s.packageName)) return false;
    if (!Comparing.equal(myState.databaseName, s.databaseName)) return false;
    if (!Comparing.equal(myState.external, s.external)) return false;

    return true;
  }

  @Tag("data-source")
  public static class State {
    //@Attribute
    //public String uuid = "";
    public String deviceId = "";
    public String name = "";
    public String comment = "";
    public String packageName = "";
    public String databaseName = "";
    public boolean external = false;
    @Tag("classpath-elements")
    public Element[] classpathElements = new Element[0];
  }
}
