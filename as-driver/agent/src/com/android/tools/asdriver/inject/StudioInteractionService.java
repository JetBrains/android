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
package com.android.tools.asdriver.inject;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;

/**
 * Service responsible for interacting with the interface.
 */
public class StudioInteractionService {

  enum TaskInvocationContext {
    /**
     * Invoke using {@code SwingUtilities.invokeAndWait}.
     *
     * Swing's {@code invokeAndWait} is used over the one in {@code ApplicationManager} in very
     * specific cases, e.g. when needing to interact with old components like {@link LinkLabel}. If
     * uncertain about which to use and {@code APPLICATION_INVOKE_AND_WAIT} works instead, opt for
     * {@code APPLICATION_INVOKE_AND_WAIT}.
     */
    SWING_INVOKE_AND_WAIT,
    /**
     * Invoke using {@code ApplicationManager.getApplication().invokeAndWait}.
     */
    APPLICATION_INVOKE_AND_WAIT,
    /**
     * Invoke outside the UI thread.
     */
    NON_UI_THREAD
  }

  public StudioInteractionService() { }

  /**
   * Runs the entire update flow, i.e. checking for the update, downloading and installing, then
   * restarting.
   */
  public void runUpdateFlow() throws InterruptedException, InvocationTargetException, TimeoutException {
    final Window welcomeWindow = findWelcomeWindow(30000);

    // This does not need to be in a retry loop since it's directly calling an API.
    ApplicationManager.getApplication().invokeAndWait(() -> checkForUpdates(welcomeWindow));

    keepTryingTask(() -> invokeWelcomeScreenUpdateButton(welcomeWindow), 10000, TaskInvocationContext.APPLICATION_INVOKE_AND_WAIT);

    // Wait for the update link to exist, that way the call to SwingUtilities.invokeLater can
    // succeed.
    keepTryingTask(() -> ensureUpdateLinkExists(welcomeWindow), 10000, TaskInvocationContext.APPLICATION_INVOKE_AND_WAIT);

    // This must be invoked via SwingUtilities given how LinkLabel#doClick blocks future
    // invocations.
    SwingUtilities.invokeLater(() -> clickUpdateLink(welcomeWindow));

    // Waiting for the new dialog has to be done off of the UI thread.
    keepTryingTask(this::getDialogWrapperDialog, 10000, TaskInvocationContext.NON_UI_THREAD);
    keepTryingTask(() -> clickUpdateAndRestartButton(welcomeWindow), 10000, TaskInvocationContext.SWING_INVOKE_AND_WAIT);
    keepTryingTask(() -> invokeWelcomeScreenUpdateButton(welcomeWindow), 10000, TaskInvocationContext.APPLICATION_INVOKE_AND_WAIT);
    keepTryingTask(this::activateRestartHyperlink, 20000, TaskInvocationContext.APPLICATION_INVOKE_AND_WAIT);
  }

  /**
   * Clicks the button labeled "Update and Restart" in the dialog spawned from
   * the "Welcome" window.
   */
  private void clickUpdateAndRestartButton(Window welcomeWindow) {
    Window dialogWindow = getDialogWrapperDialog();

    String buttonText = IdeBundle.message("updates.download.and.restart.button");
    JButton updateButton = (JButton)findComponentByText(dialogWindow, buttonText);
    invokeButton(updateButton);

    System.out.printf("Clicked the button labeled \"%s\"%n", buttonText);
  }

  /**
   * @throws NoSuchElementException Thrown when the window doesn't exist.
   */
  private Window getDialogWrapperDialog() {
    Window[] windows = Frame.getWindows();
    for (Window window : windows) {
      if (window instanceof DialogWrapperDialog) {
        return window;
      }
    }

    throw new NoSuchElementException("Dialog window not found");
  }

  /**
   * Throws an exception if the "Update" link doesn't exist, that way a caller can loop on this
   * function to wait for it to appear.
   */
  private void ensureUpdateLinkExists(Window welcomeWindow) {
    LinkLabel updateLink = getUpdateLink(welcomeWindow);
    if (updateLink == null) {
      throw new NoSuchElementException("No update link found");
    }
  }

  private LinkLabel getUpdateLink(Window welcomeWindow) {
    Component updateLink = findComponentByText(welcomeWindow, IdeBundle.message("updates.notification.update.action"));
    return (LinkLabel)updateLink;
  }

  private void clickUpdateLink(Window welcomeWindow) {
    LinkLabel updateLinkLabel = getUpdateLink(welcomeWindow);
    updateLinkLabel.doClick();
  }

  /**
   * Directly invokes the "check for updates" functionality of the platform (as opposed to going
   * through the UI).
   */
  private void checkForUpdates(Window welcomeWindow) {
    ActionManager am = ApplicationManager.getApplication().getService(ActionManager.class);
    AnAction checkForUpdate = am.getAction("CheckForUpdate");
    DataContext context = DataManager.getInstance().getDataContext(welcomeWindow);
    AnActionEvent event = AnActionEvent.createFromAnAction(checkForUpdate, null, ActionPlaces.UNKNOWN, context);
    checkForUpdate.actionPerformed(event);
  }

  /**
   * Finds the "Welcome" window (NOT the "Welcome" wizard) that shows every time you start Android
   * Studio normally.
   *
   * @throws NoSuchElementException Thrown when the window cannot be located after the timeout.
   */
  private Window findWelcomeWindow(long timeoutMillis) {
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < timeoutMillis) {
      Frame[] frames = Frame.getFrames();
      for (Frame frame : frames) {
        if (frame instanceof FlatWelcomeFrame) {
          System.out.println("Found the welcome window after " + (System.currentTimeMillis() - startTime) + "ms");
          return frame;
        }
      }
    }

    throw new NoSuchElementException("Could not find a FlatWelcomeFrame");
  }

  /**
   * Activates the "Restart" link in Android Studio, which will trigger the patching process before
   * restarting Android Studio.
   *
   * @throws NullPointerException Thrown if the link cannot be found or activated.
   */
  private void activateRestartHyperlink() {
    // There may be multiple notifications (even multiple with the "IDE and Plugin Updates" group
    // ID), so we filter by the display ID since there's only ever one "Restart" link.
    Notification[] allNotifications =
      ApplicationManager.getApplication().getService(NotificationsManager.class).getNotificationsOfType(Notification.class, null);
    Notification notification = Arrays.stream(allNotifications)
      .filter((n) -> Objects.equals(n.getDisplayId(), "ide.update.suggest.restart")).findFirst().get();
    try {
      // This string can seemingly be anything, so it's not like "Restart link" is a special value
      String source = "Restart link";
      HyperlinkEvent e = new HyperlinkEvent(source, HyperlinkEvent.EventType.ACTIVATED, new URL("http://localhost/madeup"));
      notification.getListener().hyperlinkUpdate(notification, e);
    }
    catch (MalformedURLException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * Invokes the "Update" button which shows at the bottom right of the "Welcome" window. This
   * button can either be a yellow arrow or a green or yellow "teardrop" with a number in it.
   */
  private void invokeWelcomeScreenUpdateButton(Window welcomeWindow) {
    // The button we're looking for is an ActionLink inside an ActionLinkPanel.
    List<String> ancestorClassRegexes = Arrays.asList(".*\\bFlatWelcomeFrame$", ".*\\bJActionLinkPanel$", ".*\\bActionLink$");
    Set<Component> actionLinks = findComponentsByAncestryClassNames(welcomeWindow, ancestorClassRegexes);

    // The link we want has no text, and its icon can differ depending on how many notifications
    // there are.
    List<Icon> iconsToMatchAgainst =
      Arrays.asList(AllIcons.Ide.Notification.IdeUpdate, AllIcons.Ide.Notification.InfoEvents, AllIcons.Ide.Notification.WarningEvents);
    ActionLink updateLink = findLinkByIcon(new ArrayList<>(actionLinks), iconsToMatchAgainst);

    if (updateLink == null) {
      throw new NoSuchElementException("No update link found.");
    }

    performAction(updateLink.getAction(), updateLink);
  }

  /**
   * Retries the given {@code Runnable} in a loop until it either succeeds or times out. This is
   * done to prevent having to litter test code with calls to {@code Thread.sleep}.
   */
  private void keepTryingTask(Runnable task, long timeoutMillis, TaskInvocationContext context)
    throws TimeoutException, InterruptedException {
    long msBetweenRetries = 300;
    long startTime = System.currentTimeMillis();
    Exception lastException = null;
    while (true) {
      try {
        switch (context) {
          case SWING_INVOKE_AND_WAIT:
            SwingUtilities.invokeAndWait(task);
            break;
          case APPLICATION_INVOKE_AND_WAIT:
            ApplicationManager.getApplication().invokeAndWait(task);
            break;
          case NON_UI_THREAD:
            task.run();
            break;
          default:
            throw new IllegalArgumentException("Unrecognized invocation context: " + context.name());
        }
        break;
      }
      catch (Exception e) {
        lastException = e;
      }

      long elapsedTime = System.currentTimeMillis() - startTime;
      if (elapsedTime >= timeoutMillis) {
        throw new TimeoutException("Timed out after " + elapsedTime + "ms. Last exception caught: " + lastException + " " +
                                   Arrays.toString(lastException.getStackTrace()));
      }
      Thread.sleep(msBetweenRetries);
    }
  }

  private void invokeButton(JButton button) {
    Action action = button.getAction();
    ActionEvent ae = new ActionEvent(button, 0, null);
    action.actionPerformed(ae);
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
    if (c instanceof JLabel) {
      return ((JLabel)c).getText();
    }
    if (c instanceof AbstractButton) {
      return ((AbstractButton)c).getText();
    }

    return null;
  }

  /**
   * Thinly wraps an overload of this function such that callers can search within a window.
   *
   * @param window The window to search within.
   */
  private Component findComponentByText(Window window, String text) throws NoSuchElementException {
    List<Component> allComponents = getAllComponentsUnder(window);
    return findComponentByText(allComponents, text);
  }

  /**
   * Locates the first component matching the specified text. Not all components have text.
   *
   * @param components The components to search within.
   */
  private Component findComponentByText(List<Component> components, String text) throws NoSuchElementException {
    for (Component c : components) {
      String componentText = getTextFromComponent(c);

      if (componentText != null && componentText.equals(text)) {
        return c;
      }
    }

    // If no component is found and the string passed in has an escape character (from a mnemonic),
    // then try again without it.
    String textWithoutEscapeCharacter = text.replaceAll("[\\x1B]", "");
    if (!text.equals(textWithoutEscapeCharacter)) {
      return findComponentByText(components, textWithoutEscapeCharacter);
    }

    throw new NoSuchElementException(String.format("No component found with text==\"%s\"", text));
  }

  /**
   * Gets an icon's "sub icons" ({@link LayeredIcon} instances can have multiple).
   */
  private List<Icon> getAllIconsFromIcon(Icon icon) {
    List<Icon> icons = new ArrayList<>();
    if (icon instanceof LayeredIcon) {
      LayeredIcon layeredIcon = (LayeredIcon)icon;
      for (int i = 0; i < layeredIcon.getIconCount(); i++) {
        Icon subIcon = layeredIcon.getIcon(i);
        List<Icon> subIcons = getAllIconsFromIcon(subIcon);
        icons.addAll(subIcons);
      }
    } else {
      icons.add(icon);
    }

    return icons;
  }

  /**
   * Finds an {@link ActionLink} by its icon.
   *
   * @param components The components to search through.
   * @param iconsToMatchAgainst If an ActionLink matches any of these icons, it is returned.
   */
  private ActionLink findLinkByIcon(List<Component> components, List<Icon> iconsToMatchAgainst) {
    Set<Component> matchingLinks = new HashSet<>();
    for (Component c : components) {
      if (!(c instanceof ActionLink)) {
        continue;
      }
      ActionLink link = (ActionLink)c;
      List<Icon> icons = getAllIconsFromIcon(link.getIcon());
      for (Icon icon : icons) {
        if (iconsToMatchAgainst.contains(icon)) {
          matchingLinks.add(c);
        }
      }
    }

    if (matchingLinks.isEmpty()) {
      return null;
    }
    if (matchingLinks.size() > 1) {
      System.err.println(String.format("Multiple links found matching the icons passed in, using one of them."));
    }

    return (ActionLink)matchingLinks.stream().findFirst().get();
  }

  /**
   * Finds components in a simplified, XPath-like manner. This works by:
   *
   * 1. Finding all components under and including the {@code root}.
   * 2. Filtering those components by the next regex from {@code classNameRegexes}.
   * 3. Recursing until there are no more regexes to search for.
   *
   * @param root             The component to start searching from. The root and all of its descendants are
   *                         considered.
   * @param classNameRegexes Regexes in order of increasing specificity.
   */
  private Set<Component> findComponentsByAncestryClassNames(Component root, List<String> classNameRegexes) {
    Set<Component> componentsToLookUnder = new HashSet<>();
    componentsToLookUnder.add(root);

    for (String classNameRegex : classNameRegexes) {
      // Get all children of all components to search
      Set<Component> allComponents = new HashSet<>();
      for (Component c : componentsToLookUnder) {
        allComponents.addAll(getAllComponentsUnder(c));

        // Add the root as well in case getAllComponentsUnder stops returning it.
        allComponents.add(c);
      }

      Predicate<? super Component> classMatchesRegex = (c) -> c.getClass().toString().matches(classNameRegex);
      componentsToLookUnder = allComponents.stream().filter(classMatchesRegex).collect(Collectors.toSet());
    }

    return componentsToLookUnder;
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
      if (c instanceof Container) {
        Container container = (Container)c;
        Collections.addAll(componentsToSearch, container.getComponents());
      }
      componentsFound.add(c);
    }
    return componentsFound;
  }
}
