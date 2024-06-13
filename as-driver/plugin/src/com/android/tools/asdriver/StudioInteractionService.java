/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.asdriver;

import com.android.tools.asdriver.proto.ASDriver;
import com.intellij.BundleBase;
import com.intellij.ide.DataManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.ActionMenuItem;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.icons.CachedImageIcon;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.ui.components.labels.LinkLabel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.accessibility.AccessibleContext;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListModel;
import javax.swing.event.HyperlinkEvent;

/**
 * Service responsible for interacting with the interface.
 */
public class StudioInteractionService {

  /**
   * A prefix for logs so that we can identify them in idea.log if we have to.
   */
  private static final String LOG_PREFIX = "[StudioInteractionService]";

  public StudioInteractionService() { }

  public void findAndInvokeComponent(List<ASDriver.ComponentMatcher> matchers) throws InterruptedException, TimeoutException, InvocationTargetException {
    log("Attempting to find and invoke a component with matchers: " + matchers);
    // TODO(b/234067246): consider this timeout when addressing b/234067246. At 10000 or less, this fails occasionally on Windows.
    long timeoutMillis = 180000;
    Function<Component, Boolean> filter = this::isComponentInvokable;
    Consumer<Component> invoke = this::invokeComponent;
    long startTime = System.currentTimeMillis();
    filterAndExecuteComponent(matchers, timeoutMillis, filter, invoke);
    log(String.format(Locale.getDefault(), "Found and invoked the component in %dms", System.currentTimeMillis() - startTime));
  }

  public void waitForComponent(List<ASDriver.ComponentMatcher> matchers, boolean waitForEnabled)
    throws InterruptedException, TimeoutException, InvocationTargetException {
    log("Attempting to wait for a component with matchers: " + matchers);
    // TODO(b/234067246): consider this timeout when addressing b/234067246. This particular
    // timeout is so high because ComposePreviewKotlin performs a Gradle build, and that takes >10m
    // sometimes.
    final long timeoutMillis = java.time.Duration.ofMinutes(10).toMillis();
    Function<Component, Boolean> filter = c -> !waitForEnabled || isComponentInvokable(c);
    Consumer<Component> NOOP = c -> {};
    filterAndExecuteComponent(matchers, timeoutMillis, filter, NOOP);
  }

  private void log(String text) {
    System.out.printf("%s %s%n", LOG_PREFIX, text);
  }

  private void invokeComponent(Component component) {
    if (component instanceof ActionLink componentAsLink) {
      log("Invoking ActionLink: " + componentAsLink);
      performAction(componentAsLink.getAction(), componentAsLink);
    } else if (component instanceof LinkLabel<?> componentAsLink) {
      log("Invoking LinkLabel: " + componentAsLink);
      componentAsLink.doClick();
    } else if (component instanceof NotificationComponent componentAsNotification) {
      log("Invoking hyperlink in Notification: " + componentAsNotification);
      componentAsNotification.invokeHyperlink();
    } else if (component instanceof JListItemComponent componentAsListItem) {
      log("Invoking JListItemComponent item: " + componentAsListItem);
      componentAsListItem.invoke();
    } else if (component instanceof JButton componentAsButton) {
      log("Invoking JButton: " + componentAsButton);
      invokeButton(componentAsButton);
    } else if (component instanceof ActionButton componentAsButton) {
      log("Invoking ActionButton: " + componentAsButton);
      componentAsButton.click();
    } else if (component instanceof ActionMenu componentAsMenu) {
      log("Invoking ActionMenu: " + componentAsMenu);
      componentAsMenu.doClick();
    } else if (component instanceof ActionMenuItem componentAsMenuItem) {
      log("Invoking ActionMenuItem: " + componentAsMenuItem);
      componentAsMenuItem.doClick();
    } else {
      throw new IllegalArgumentException(String.format("Don't know how to invoke a component of class \"%s\"", component.getClass()));
    }
  }

  /**
   * Finds a component (if exactly one exists) based on a list of matchers.
   * <p>
   * This method abstracts the complexity of the platform so that callers have an easy-to-use API.
   */
  private Optional<Component> findComponentFromMatchers(List<ASDriver.ComponentMatcher> matchers) {
    Set<Component> componentsFound = getEntireSwingHierarchy();

    for (ASDriver.ComponentMatcher matcher : matchers) {
      if (matcher.hasComponentTextMatch()) {
        ASDriver.ComponentTextMatch match = matcher.getComponentTextMatch();
        componentsFound = findComponentsMatchingText(componentsFound, match.getText(), match.getMatchMode());
      } else if (matcher.hasSvgIconMatch()) {
        ASDriver.SvgIconMatch match = matcher.getSvgIconMatch();
        componentsFound = findLinksByIconNames(componentsFound, match.getIconList());
      } else if (matcher.hasSwingClassRegexMatch()) {
        ASDriver.SwingClassRegexMatch match = matcher.getSwingClassRegexMatch();
        String regex = match.getRegex();

        // Regex matchers have two uses:
        // 1. Narrowing down the search scope for subsequent matchers, e.g. "fetch me all JPanels
        //    and all of their descendants".
        // 2. Finding a single component, e.g. "now that we've found all of those, fetch me the
        //    single JPopupMenu without finding its descendants".
        //
        // Case #2 is only done when the matcher is the final matcher in the list, and it means we
        // don't want to return the subtree of whichever components we find.
        boolean isFinalMatcher = matchers.get(matchers.size() - 1) == matcher;
        componentsFound = findComponentsMatchingRegex(componentsFound, regex, !isFinalMatcher);
      } else {
        throw new IllegalArgumentException("ComponentMatcher doesn't have a recognized matcher");
      }
    }

    // b/243561571: Filter out ActionMenu components that have height 0 on Windows
    if (SystemInfo.isWindows) {
      componentsFound.removeIf((c) -> (c instanceof ActionMenu) && (c.getHeight() == 0));
    }

    int numComponentsFound = componentsFound.size();
    if (numComponentsFound > 1) {
      StringBuilder sb = new StringBuilder();
      int index = 1;
      for (Component component : componentsFound) {
        // Some components override toString(), which means the class isn't guaranteed to show.
        // Given that most searches for components rely on the class name, we explicitly print it.
        sb.append(String.format(Locale.getDefault(), "\t#%d: class: [%s] toString: %s%n", index++, component.getClass(), component));
      }
      throw new IllegalStateException(String.format("Found %s component(s) but expected exactly one:%n%s%n\tPlease construct more specific match criteria.",
                                                    numComponentsFound, sb));
    }

    return componentsFound.stream().findFirst();
  }

  /**
   * Checks if the component can be invoked. For example, suppose that a test calls
   * {@code invokeComponent} on a button that is present but disabled. In that case, we want
   * {@link StudioInteractionService#findAndInvokeComponent} to keep trying until it's enabled,
   * otherwise the test will attempt to invoke an uninvokable component.
   */
  private boolean isComponentInvokable(Component c) {
    return c.isEnabled();
  }

  /**
   * Finds all components whose class names match the given regex.
   *
   * @param includeSubtrees When true, return not only the matching components but also their
   *                        entire Swing subtrees.
   */
  private Set<Component> findComponentsMatchingRegex(Set<Component> componentsToLookUnder, String regex, boolean includeSubtrees) {
    Predicate<? super Component> classMatchesRegex = (c) -> c.getClass().toString().matches(regex);
    Set<Component> componentsFound = componentsToLookUnder.stream().filter(classMatchesRegex).collect(Collectors.toSet());

    if (includeSubtrees) {
      List<Component> componentsUnderFoundComponents = new ArrayList<>();
      for (Component component : componentsFound) {
        componentsUnderFoundComponents.addAll(getAllComponentsUnder(component));
      }
      componentsFound.addAll(componentsUnderFoundComponents);
    }

    return componentsFound;
  }

  private Set<Component> findComponentsMatchingText(
    Set<Component> componentsToLookUnder, String text, ASDriver.ComponentTextMatch.MatchMode matchMode) {
    Predicate<? super Component> filterByText = (c) -> {
      String componentText = getTextFromComponent(c);

      // Remove any escape characters introduced by mnemonics from the component's text.
      String textWithoutEscapeCharacter = componentText == null ? null : componentText.replaceAll(String.valueOf(BundleBase.MNEMONIC), "");
      return switch (matchMode) {
        case EXACT -> Objects.equals(componentText, text) || Objects.equals(textWithoutEscapeCharacter, text);
        case CONTAINS -> componentText != null && (componentText.contains(text) || textWithoutEscapeCharacter.contains(text));
        case REGEX -> {
          Pattern pattern = Pattern.compile(text);
          yield componentText != null &&
                 (pattern.matcher(componentText).matches() || pattern.matcher(textWithoutEscapeCharacter).matches());
        }
        case UNRECOGNIZED -> false;
      };
    };
    Set<Component> componentsFound = componentsToLookUnder.stream().filter(filterByText).collect(Collectors.toSet());

    // Notifications are searched separately because the text is embedded in an inaccessible way.
    componentsFound.addAll(findNotificationByDisplayId(text));

    componentsFound.addAll(findJListItems(componentsToLookUnder, text));

    return componentsFound;
  }

  private List<JListItemComponent> findJListItems(Set<Component> componentsToLookUnder, String text) {
    List<JListItemComponent> matchingComponents = new ArrayList<>();
    for (Component component : componentsToLookUnder) {
      if (!(component instanceof JList<?> jList)) {
        continue;
      }

      ListModel<?> model = jList.getModel();

      int numItems = model.getSize();
      for (int i = 0; i < numItems; i++) {
        if (model.getElementAt(i).toString().equals(text)) {
          JListItemComponent item = new JListItemComponent(jList, i);
          matchingComponents.add(item);
          break;
        }
      }
    }

    return matchingComponents;
  }

  private Collection<? extends Component> findNotificationByDisplayId(String displayId) {
    Notification[] allNotifications = NotificationsManager.getNotificationsManager().getNotificationsOfType(Notification.class, null);

    return Arrays.stream(allNotifications)
      .filter((n) -> Objects.equals(n.getDisplayId(), displayId))
      .map(NotificationComponent::new)
      .collect(Collectors.toList());
  }

  /**
   * Gets an icon's underlying icon name(s) ({@link LayeredIcon} instances can have multiple).
   */
  private List<String> getIconNamesFromIcon(Icon icon) {
    List<String> paths = new ArrayList<>();
    if (icon instanceof CachedImageIcon cachedImageIcon) {
      String path = cachedImageIcon.getOriginalPath();
      paths.add(path);
    }
    else if (icon instanceof LayeredIcon layeredIcon) {
      for (int i = 0; i < layeredIcon.getIconCount(); i++) {
        Icon subIcon = layeredIcon.getIcon(i);
        List<String> subPaths = getIconNamesFromIcon(subIcon);
        paths.addAll(subPaths);
      }
    }

    return paths;
  }

  private Set<Component> findLinksByIconNames(Collection<Component> components, List<String> iconsToMatchAgainst) {
    Set<Component> matchingLinks = new HashSet<>();
    for (Component c : components) {
      Icon icon;
      if (c instanceof ActionLink) {
        icon = ((ActionLink)c).getIcon();
      } else if (c instanceof ActionButton) {
        icon = ((ActionButton)c).getIcon();
      } else if (c instanceof JButton) {
        icon = ((JButton)c).getIcon();
      } else {
        continue;
      }

      List<String> iconNames = getIconNamesFromIcon(icon);
      for (String iconName : iconNames) {
        if (iconsToMatchAgainst.contains(iconName)) {
          // For some reason, new UI can have duplicate buttons in tool window toolbars.
          if (c.getLocation().x < Integer.MAX_VALUE) {
            matchingLinks.add(c);
          }
        }
      }
    }

    return new HashSet<>(matchingLinks);
  }

  private Set<Component> getEntireSwingHierarchy() {
    Set<Component> allComponents = new HashSet<>();
    for (Window window : Frame.getWindows()) {
      List<Component> componentsInWindow = getAllComponentsUnder(window);
      allComponents.addAll(componentsInWindow);
      allComponents.add(window);
    }

    return allComponents;
  }

  private void invokeButton(JButton button) {
    Action action = button.getAction();
    if (action == null) {
      System.out.println("JButton had no associated action. Falling back to doClick.");
      button.doClick();
    } else {
      ActionEvent ae = new ActionEvent(button, 0, null);
      action.actionPerformed(ae);
    }
  }

  /**
   * Invoke a particular component's action.
   *
   * @param c If specified, this will be used as the context for the action. If unspecified, the
   *          most recently focused window will be used instead.
   */
  private void performAction(AnAction action, Component c) {
    if (c == null) {
      c = ApplicationManager.getApplication().getService(IdeFocusManager.class).getLastFocusedIdeWindow();
    }

    DataContext context = DataManager.getInstance().getDataContext(c);
    AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, context);
    action.actionPerformed(event);
  }

  /**
   * Returns the text associated with the given component. Not all components have text, and not
   * all components that <i>do</i> have text are accounted for by this function (in which case,
   * feel free to modify the implementation to accommodate your needs).
   */
  private String getTextFromComponent(Component c) {
    if (c instanceof JLabel jl) {
      return jl.getText();
    }
    if (c instanceof AbstractButton ab) {
      return ab.getText();
    }
    if (c instanceof SimpleColoredComponent scc) {
      return scc.toString();
    }

    return null;
  }

  /**
   * Fetches all components under and including the {@code root} recursively.
   */
  private List<Component> getAllComponentsUnder(Component root) {
    List<Component> componentsFound = new ArrayList<>();
    Queue<Component> componentsToSearch = new LinkedList<>();
    componentsToSearch.add(root);
    while (!componentsToSearch.isEmpty()) {
      Component c = componentsToSearch.poll();
      if (c instanceof Container container) {
        Collections.addAll(componentsToSearch, container.getComponents());
      }
      componentsFound.add(c);
      if (c.getClass().toString().contains("Compose")) {
        Set<AccessibleContext> contexts = getAllAccessibleContext(c.getAccessibleContext());
        componentsFound.addAll(getComponentsFromContext(contexts));
      }
    }
    return componentsFound;
  }

  /**
   * Filters and invokes a component. The bulk of the complexity of this method's implementation
   * comes from concurrency and modality. There are three general scenarios that this method
   * covers:
   * <p>
   * <ol>
   *   <li>Successfully filtering and executing a component which spawns a modal dialog</li>
   *   <li>Successfully filtering and executing a component which does not spawn a modal dialog</li>
   *   <li>Unsuccessfully filtering and executing any component</li>
   * </ol>
   * <p>
   * In case #1, if we were to use {@link Application#invokeAndWait} to filter and invoke the
   * component, then the calling thread would not resume until the modal dialog is closed (due to
   * the "AndWait" part of "invokeAndWait"). In our case, the calling thread is the gRPC server's
   * thread, meaning no future requests from test code could be handled. This effectively means
   * that the test would be forever stalled—no requests can interact with the dialog (so it will
   * never close), and the calling thread is waiting forever in
   * {@link Application#invokeAndWait}.
   * <p>
   * In cases #2 and #3, {@link Application#invokeAndWait} <i>could</i> be used to filter and
   * invoke a component. However, because we have to accommodate case #1 anyway and because we
   * can't distinguish which case we'll be in ahead of time, we need to opt for
   * {@link Application#invokeLater}.
   * <p>
   * In all cases, we must ensure that the component does not disappear or otherwise become invalid
   * between <b>finding</b> and <b>invoking</b> (see b/235277847).
   */
  private void filterAndExecuteComponent(List<ASDriver.ComponentMatcher> matchers, long timeoutMillis, Function<Component, Boolean> filter, Consumer<Component> exec)
    throws InterruptedException, TimeoutException {
    long msBetweenRetries = 300;
    long startTime = System.currentTimeMillis();
    long elapsedTime = 0;
    final AtomicBoolean foundComponent = new AtomicBoolean(false);
    final AtomicBoolean invokedComponent = new AtomicBoolean(false);
    Application app = ApplicationManager.getApplication();

    while (elapsedTime < timeoutMillis) {
      app.invokeLater(() -> {
        Optional<Component> component = findComponentFromMatchers(matchers);
        if (component.isPresent() && filter.apply(component.get())) {
          foundComponent.set(true);
          Runnable execComponent = () -> exec.accept(component.get());
          ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(execComponent);
        }
      }, ModalityState.any()); // Using ModalityState.any() to ensure this runs even if there are modal dialogs open.

      // The invokeLater call above queues a Runnable to be executed on the UI thread at some point
      // in the future. This means that the calling thread continues its own execution immediately.
      // However, the calling thread needs to know whether the Runnable was successful so that we
      // can decide whether to return, retry, or timeout.
      //
      // In cases #2 and #3 from the method-level comment, the invokeAndWait Runnable below will
      // only run once the invokeLater Runnable is complete.
      //
      // In case #1 though, the invokeLater Runnable will eventually try spawning a modal dialog,
      // at which point Swing will know that it can execute the invokeAndWait Runnable even though
      // the invokeLater Runnable hasn't finished.
      app.invokeAndWait(() -> {
        if (foundComponent.get()) {
          // Note: all we can know is that we ATTEMPTED to invoke the component, not that it was
          // successful.
          invokedComponent.set(true);
        }
      }, ModalityState.any());

      if (invokedComponent.get()) {
        break;
      }
      Thread.sleep(msBetweenRetries);
      elapsedTime = System.currentTimeMillis() - startTime;
    }

    if (elapsedTime >= timeoutMillis) {
      throw new TimeoutException(
        String.format("Timed out after %dms to find and invoke a component with these matchers: %s", elapsedTime, matchers));
    }
  }

  /**
   * Wraps {@link Notification} in a {@link Component} so that it can be treated like other
   * {@link Component} instances for the sake of invoking them.
   */
  private static class NotificationComponent extends Component {
    private final Notification notification;

    public NotificationComponent(Notification notification) {
      this.notification = notification;
    }

    private void invokeHyperlinkViaListener() {
      try {
        String source = "Link inside notification";
        HyperlinkEvent e = new HyperlinkEvent(source, HyperlinkEvent.EventType.ACTIVATED, new URL("http://localhost/madeup"));
        Objects.requireNonNull(notification.getListener()).hyperlinkUpdate(notification, e);
      }
      catch (MalformedURLException ex) {
        ex.printStackTrace();
      }
    }

    private void invokeHyperlinkViaAction() {
      List<AnAction> actions = notification.getActions();
      System.out.printf("Invoking a hyperlink via its first action (of %d)%n", actions.size());
      NotificationAction action = (NotificationAction)actions.get(0);
      try {
        DataContext context = DataManager.getInstance().getDataContext();
        AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, context);
        action.actionPerformed(event, notification);
        System.out.println("Successfully ran the notification's action");
      }
      catch (Exception e) {
        System.err.println("Got exception: " + e);
        throw e;
      }
    }

    public void invokeHyperlink() {
      // A recent platform merge caused the listener to be null on Windows machines, in which case
      // we have to invoke the hyperlink differently.
      if (notification.getListener() == null) {
        invokeHyperlinkViaAction();
      }
      else {
        invokeHyperlinkViaListener();
      }
    }
  }

  /**
   * Given a set of AccessibleContext returns the Set of wrapper components from @ComposeJComponentsWrapper.kt if one
   * exists
   */
  private Set<Component> getComponentsFromContext(Set<AccessibleContext> contexts) {
    Set<Component> components = new HashSet<>();
    for (AccessibleContext context : contexts) {
      if (context.getAccessibleRole() != null && context.getAccessibleRole().toString().contains("label")) {
        JLabel label = new ComposeJLabelWrapper(context);
        components.add(label);
      }
      else if (context.getAccessibleRole() != null && context.getAccessibleRole().toString().contains("push button")) {
        JButton button = new ComposeJButtonWrapper(context);
        components.add(button);
      }
    }
    return components;
  }

  /**
   * Gets all the AccessibleContext from the children of the passed in context
   */
  private Set<AccessibleContext> getAllAccessibleContext(AccessibleContext context) {
    Set<AccessibleContext> allContext = new HashSet<>();
    if (context == null) {
      return allContext;
    }
    allContext.add(context);
    if (context.getAccessibleChildrenCount() == 0) {
      return allContext;
    }
    for (int i = 0; i < context.getAccessibleChildrenCount(); i++) {
      AccessibleContext c = context.getAccessibleChild(i).getAccessibleContext();
      allContext.addAll(getAllAccessibleContext(c));
    }
    return allContext;
  }

  private static class JListItemComponent extends Component {
    private final JList<?> parent;
    private final int itemIndex;

    public JListItemComponent(JList<?> parent, int itemIndex) {
      this.parent = parent;
      this.itemIndex = itemIndex;
    }

    public void invoke() {
      parent.setSelectedIndex(itemIndex);
    }
  }
}