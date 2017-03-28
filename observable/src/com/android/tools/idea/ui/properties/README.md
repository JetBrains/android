# Properties

**Properties** are, in a nutshell, values that can be queried, set, and chained together via bindings.

In Java, the concept of properties is traditionally implemented with strictly named getXXX and setXXX methods. However, wrapping this concept in a class makes the relationship more explicit and enables additional features, which this document aims to enumerate.

[TOC]

## Basics

It can be instructive to compare `Property`s with Java's traditional convention for defining properties.

**Traditional**

```java
private String myName = "";
private int myAge;
public String getName() { return myName; }
public void setName(String name) { myName = name; }
public int getAge() { return myAge; }
public void setAge(int age) { myAge = age; }
```

**Property classes**

```java
private StringProperty myName = new StringValueProperty();
private IntProperty myAge = new IntValueProperty();
public StringProperty name() { return myName; }
public IntProperty age() { return myAge; }
```

Even without any additional features, we've already cut down on a bit of boilerplate. On top of that, property classes enjoy some extra benefits:

* They support the adding of listeners
* They support chaining, e.g. `myAge.isGreaterThan(10).and(myAge.isLessThan(20))`
* They enforce non-null semantics, so no more worrying about `""` vs. `null`.
* They provide better reflection support: if you had to, you can now enumerate a class's properties via class type (`instanceof Property`),
  instead of relying on a get/set naming convention.

## One Way Bindings

Bindings are hands down the most useful feature that properties offer. They allow you to set up relationships between properties, which continue to maintain themselves over time. To do this, you first create a `BindingsManager` (this seems like an extra step but this helps avoid memory leaks, more on that later), and then call `bind`. This links a source property to a destination value.

```java
BindingsManager bindings = new BindingsManager();

StringProperty name = new StringValueProperty();
IntProperty age = new IntValueProperty();
BoolProperty isCitizen = new BoolValueProperty();
BoolProperty canVote = new BoolValueProperty();
BoolProperty validName = new BoolValueProperty();
StringProperty message = new StringValueProperty();

bindings.bind(canVote, age.isGreaterThanEqual(16).and(isCitizen));
bindings.bind(validName, not(name.isEmpty());
bindings.bind(message, new FormatExpression("Hello, %1$s", name));

name.set("Joe Random");
age.set(22);
isCitizen.set(true);
assert canVote.get() == true;
assert validName.get() == true;
assert message.get() == "Hello, Joe Random"
```

## Expressions

Although you can bind one property directly to another, very often you want to transform your data along the way. This is done via classes that are subclassed from `Expression`.

For example, `NotExpression` listens to a target boolean and returns its opposite value. `ToUpperExpression` listens to a target string and returns a modified, upper-cased version.

```java
BoolProperty isLoggedIn = ...;
BoolProperty isLoggedOut = new BoolValueProperty();
bindings.bind(isLoggedOut, new NotExpression(isLoggedIn));
// Or, a shortcut: bindings.bind(isLoggedOut, isLoggedIn.not())
```

`Expressions` can also convert one data type to another - for example, `ParseIntExpression` listens to a target string and returns its value as an integer (if the string could be parsed). `ToStringExpression` converts any object type to its string representation.

An expression can even wrap many properties. `SumExpression` listens to a list of integers and folds them into a single integer, their sum. And `AnyExpression` listens to a list of boolean properties and returns true if any one of them are true. In this way, you can map many inputs to a single output.

Finally, you can always instantiate an anonymous `Expression` on the fly. If you want to convert, say, a `Person` instance to a integer value representing the person's age, you can write:

```java
bindings.bind(myAge, new Expression<Integer>(myPerson) {
  @NotNull
  @Override
  public Integer get() { return myPerson.getAge(); }
});
```

Expressions are, in summary, read-only transformations that properties can bind to.

## Two Way Bindings

One-way bindings cover most ground, but two-way bindings between two properties are an important use case as well. One situation where two-way bindings really shine is attaching UI components to data properties.

We're going to gloss over some of the details of Swing properties for now (covered in a later section), but assume we have a property (`myName`), associated text field (`myNameField`), and a button (`myResetButton`). The button, when pressed, resets the name field to the empty string.

We accomplish this by setting up a two-way binding between the name property and the Swing text field. We also listen to the button press, clearing the name property in response. Any changes we make to the name property automatically update the bound text field.

```java
public class NameEditorScreen {
  private JTextField myNameField = …;
  private JButton myResetButton = …;

  private BindingsManager myBindings = new BindingsManager();
  private StringProperty myName = new StringValueProperty();

  public void onEnter() {
    // This initializes myNameField to myName, and then keeps changes in sync between them
    myBindings.bindTwoWay(new TextProperty(myNameField), myName);    
    myResetButton.addActionListener(e -> myName.set(""));
  }

  @Override
  public void dispose() {
    myBindings.releaseAll(); // Always remember to releaseAll!
  }
}
```

## InvalidationListener

Every property inherits from a subclass called `ObservableValue`. This class represents a value that, when changed, notifies any listeners of the fact.

```java
IntProperty age = new IntValueProperty();
age.addListener(o -> LOG("Age changed: " + age.get()));
```

Note there is also an `addWeakListener` method. You may wish to use this if you don't own the property yourself but are handed it from some external location. That way, if your class wants to go out of scope, the external property won't keep it alive. See [Avoiding Memory Leaks](#Avoiding-Memory-Leaks) for more details.

## Composite listening

Often you have several properties where, if one or more of them change, you want to fire some action as a result. This sounds similar to bindings, except we don't care about chaining values or even what the values are. We just care that they changed.

For example, you may want to regenerate a Bitmap preview whenever any of the size fields change, but regenerating the image is an expensive operation.

It may be tempting just to create a single invalidation listener and add it to each property, but then you can't batch property changes. Each property changed fires the listener immediately!

```java
// Anti-code pattern, do not copy!
IntProperty x, y, w, h;
InvalidationListener onSizeChanged = o -> recreateBitmap(x.get(), y.get(), w.get(), h.get());
x.addListener(onSizeChanged);
y.addListener(onSizeChanged);
w.addListener(onSizeChanged);
h.addListener(onSizeChanged);

x.set(10); // recreateBitmap is called once
y.set(30); // recreateBitmap is called twice…
w.set(640);
h.set(480);
```

The `ListenerManager` class provides a better way:

```java
ListenerManager listeners = new ListenerManager();
IntProperty x, y, w, h;
Runnable onSizeChanged = () -> recreateBitmap(x.get(), y.get(), w.get(), h.get());
listeners.listenAll(x, y, w, h).with(onSizeChanged); 
```

This second solution will only call the expensive method once, even if `x`, `y`, `w`, and `h` all change on the same frame.

*** aside
With Swing, sometimes this batching is already done for you. For example, adding the repaint call to several listeners seems like it might be expensive, but the method is, itself, batched by the UI framework and only triggers a single `paintComponent` call under the hood. In that case, using the first approach is fine.
***

## UI and MVVM

Properties can be useful even in a console application, but they shine when gluing together a UI and data. The recommended approach is to use a UI development pattern called [Model View ViewModel](http://en.wikipedia.org/wiki/Model_View_ViewModel).

_Model ← ViewModel ↔ View_

To summarize MVVM: On one end, you have a model, which is a data class that is written without any consideration for how UIs will read from it. On the other, you have your UI, which is a bunch of widgets. In the middle, you have a _view model_, which is itself a data class and acts as an ambassador between both sides. It pulls out just what it needs from the underlying model while also being intimately aware of the UI it is providing information for.

*** aside
The `ViewModel` is similar to the `Controller` from MVC, but you can think of it more as a transformation layer with utility methods, vs simply being a home for business logic
***

To provide a concrete example, imagine a _create Android project_ wizard. It has a step where you choose a minimum target API from a list. Later, there's a summary panel that confirms the project you are about to create, including a list which displays the selected API.

We now have the following files:

```
AndroidProjectModel.java  // model
SelectAndroidApiStep.java // view-model
SelectAndroidApiStep.form // view
ProjectSummaryStep.java   // view-model
ProjectSummaryStep.form   // view
```

`AndroidProjectModel` here would contain many properties, such as `projectName`, `projectLocation`, `defaultNamespace`, etc. Among them, there is a `targetApi` property. At some point, a project model is initialized and passed into a new wizard.

The wizard soon initializes `SelectAndroidApiStep` with an `AndroidProjectModel`, which binds `targetApi` to a combobox. Later, the `ProjectSummaryStep` is also initializes to the same `AndroidProjectModel`, but it binds `targetApi` to a label, instead.

```java
// ProjectModel.java
public final ObjectProperty<ApiVersion> targetApi = ...;

// SelectAndroidApiStep.java
myBindings.bindTwoWay(
   new SelectedItemProperty(myApiCombo),
   project.targetApi);

// ProjectSummaryStep.java
ObservableValue<String> apiName = new StringExpression(project.targetApi) {
  @NotNull
  @Override
  public String get() { return project.targetApi.getDisplayName(); }
}
myBindings.bind(new TextProperty(myApiLabel), apiName);
```


## Swing Properties

Swing, of course, does not use properties - it is an API over a decade old. Instead, we provide adapter classes that wrap Swing components, allowing our property framework to interact with them. This is accomplished through the various classes in the `idea.ui.properties.swing` namespace.

Examples will demonstrate this best. Two common UI widgets are text inputs and checkboxes, and we provide one class which wraps a `JTextField`'s text value (`TextProperty`) and another which wraps a `JCheckbox`'s selected value (`SelectedProperty`):

```java
public class UserPreferencesModel {
  public StringProperty username() { return myUsername; }
  public BoolProperty isAdmin() { return myIsAdmin; }
}

public class UserPreferencesPage {
  private JTextField myUsernameField;
  private JCheckbox myIsAdminCheckbox;
  private BindingsManager myBindings;

  public void onEnter(UserPreferencesModel userPreferences) {
    myBindings.bindTwoWay(new TextProperty(myUsernameField), userPreferences.username());
    myBindings.bindTwoWay(new SelectedProperty(myIsAdminCheckbox), userPreferences.isAdmin());
  }

  public void onExit() {
    myBindings.releaseAll();
  }
}
```
  
## Bringing it all together

Let's finish our discussion with a moderately complex UI example, which is slightly contrived but represents the sort of scenarios that show up in real projects all the time.

We'll have two text fields and a checkbox, representing 1) a project's name, 2) an activity's name, and 3) whether we should skip creating the activity.

```
Project Name:  [ MyProject                  ]
Activity Name: [ MyProjectActivity          ]
☑ Create an Activity
```

To make things interesting, the activity name should be tied to the project name unless we type something in manually ourselves, which would break the link. For example, if my project's name is _GrumpyCatSimulator_, the generated activity name should be _GrumpyCatSimulatorActivity_.

```
Project Name:  [ GrumpyCatSimulator         ]
Activity Name: [ GrumpyCatSimulatorActivity ] # Automatically updated
```

but if I manually rename the activity to _MainActivity_, changing the project name later will no longer modify my activity name.

```
Project Name:  [ GrumpyCatSimulator         ]
Activity Name: [ MainActivity               ]

then, later:

Project Name:  [ FlappyCat                  ]
Activity Name: [ MainActivity               ]
```

Finally, the "create an activity" checkbox, when unchecked, should disable the activity name text field, to better visually indicate the action's consequence. 

The following excerpt demonstrates how you could accomplish all this with a minimal amount of code:

```java
  private JTextField myProjectNameField;
  private JTextField myActivityNameField;
  private JCheckBox myCreateActivityCheckbox;
  private BindingsManager myBindings;

  public void init() {
    // Wrap UI elements in properties
    final StringProperty projectText = new TextProperty(myProjectNameField);
    final StringProperty activityText = new TextProperty(myActivityNameField);
    BoolProperty isActivityEnabled = new EnabledProperty(myActivityNameField);
    final BoolProperty isSynced = new BoolValueProperty(true); // False once user types manually
    final BoolProperty createActivity = new SelectedProperty(myCreateActivityCheckbox);

    projectText.set("MyProject");

    // Bind activityText <- nameExpression(projectText), but only if conditions are met
    final FormatExpression activityNameExpression = new FormatExpression("%1$sActivity", projectText);
    myBindings.bind(activityText, activityNameExpression, isSynced.and(createActivity));

    myBindings.bind(isActivityEnabled, createActivity);

    // Listen to activityText - if it is changed by the user and not its binding, break syncing!
    activityText.addListener(new InvalidationListener() {
      @Override
      protected void onInvalidated(@NotNull Observable sender) {
        isSynced.set(activityText.get().equals(activityNameExpression.get()));
      }
    });
  }
```

## Avoiding Memory Leaks

The following habits should help avoid memory leaks and keep complexity down.

### Weak Listener

Prefer `Property.addWeakListener` over `Property.addListener` whenever you're interacting with an external `Property` that you don't own.

```java
private InvalidationListener myCountListener = (o) -> updateUi();
public void init(UsersModel model) {
   model.numUsers().addWeakListener(myCountListener);
}
```

For comparison, here's an example of using a (strongly referenced) listener:

```java
private IntProperty myNumUsers = new IntProperty();
public void init() {
   myNumUsers.addListener(o -> updateUi());
}
```

### Listener Manager

A listener manager class is provided, which you can optionally use to manage your listeners for you.

```java
  private IntProperty myNumUsers = new IntProperty();
  private BoolProperty myInAdminMode = new BoolProperty();
  private ListenersManager myListeners = new ListenersManager();
  public void init() {
     myListeners.listen(myNumUsers, count -> System.out.println("User count changed: " + count));
     myListeners.listenAndFire(myInAdminMode, isAdmin -> System.out.println("Admin? " + isAdmin)); 
     // listenAndFire immediately fires the listener after hooking it up
  }
  public void dispose() {
     myListeners.releaseAll();
  }
```

*** promo
Get in the habit of releasing all your bindings when you are done with them, via `BindingsManager.releaseAll()`. If you manage your listeners with a `ListenersManager`, call `releaseAll()` for that as well.
***

## Naming Conventions

A property should be declared as a private field inside a class. Name it like you would any normal field.

```java
private StringProperty myName
private IntProperty myCount
private BoolProperty myEnabled
```

An accessor method should have exactly the same name as the property, except with parentheses and without the prepended `my`.

```java
public StringProperty name()
public IntProperty count()
public BoolProperty enabled()
```

This naming convention allows properties to correspond 1:1 to the old get/set approach.

|||---|||
#### Traditional
```java
String title = myWindow.getTitle();
myWindow.setVisible(true);
```

#### Property
```java
String title = myWindow.title().get();
myWindow.visible().set(true);
```
|||---|||

This naming convention is inspired by [properties in languages that have them](https://msdn.microsoft.com/en-us/library/aa288470.aspx). There's also precedence dropping the get/set prefix for properties [in modern Java code](https://docs.oracle.com/javafx/2/binding/jfxpub-binding.htm#sthref4).

## Property vs ValueProperty

Although it may be confusing when you first see it, for each primitive type, there is an abstract base class and a given implementation that is backed by a primitive value. This relationship is similar to, say, a `List` and an `ArrayList`. 

For example, compare `IntProperty` with `IntValueProperty`. The first is simply the interface for setting/getting integer values in general, while the latter wraps an `Integer` which you can set/get directly. The recommended practice is to declare your fields with the abstract version but instantiate them with the concrete version:

```java
private IntProperty myAge = new IntValueProperty(42);
```

Of course there are other subclasses of `IntProperty` (and `BoolProperty` and `StringProperty`, etc.) besides the `ValueProperty` versions. For example, a Swing combobox has a selected index, and the integer property for that concept could be instantiated as follows:

```java
IntProperty selectedIndex = new SelectedIndexProperty(myComboBox);
```

## Readonly Properties vs Settable Properties

Imagine you have the following class field:

```java
private StringProperty myTitle;
```

If you want external callers to be able to modify it, return it directly:

```java
public StringProperty title() { return myTitle; }
```

If you only want to give read access to callers, return the instance via its `Observable...` base class version:

```java
public ObservableString title() { return myTitle; }
```

Every property type has its corresponding observable (read-only version), so for example...

```
BoolProperty -> ObservableBool
IntProperty -> ObservableInt
StringProperty -> ObservableString
OptionalProperty -> ObservableOptional
ObjectProperty -> ObservableObject
```

## When to bind, when to listen, and when to Swing

### Overview

One aspect of bindings that can be admittedly tricky is knowing when to bind and when to listen. And moreover, with Swing UI code, you'll have the additional decision of whether to use Swing directly or interact with it using Swing Properties. There can be more than one way to accomplish the same goal and it can be hard to know if one approach is any better than the other.

For example, any of the following solutions can be used to listen to a `JTextField`, updating a `JLabel` "hello" message whenever its value changes:

```java
JTextField myNameField;
JLabel myMessageLabel;

// Vanilla Swing
myNameField.getDocument().addDocumentListener(d -> myMessageLabel.setText("Hello, " + myNameField.getText()));
myMessageLabel.setText("Hello, " + myNameField.getText());

// Property listener
TextProperty name = new TextProperty(myNameField);
name.addListener(o -> myMessageLabel.setText("Hello, " + name.get());
myMessageLabel.setText("Hello, " + name.get());

// Listener manager
ListenerManager listeners = new ListenerManager(); // and call listeners.releaseAll() on dispose
TextProperty name = new TextProperty(myNameField);
listeners.listenAndFire(name, nameStr -> myMessageLabel.setText("Hello, " + nameStr);

// Bindings manager
BindingsManager bindings = new BindingsManager(); // and call bindings.releaseAll() on dispose
TextProperty name = new TextProperty(myNameField);
TextProperty message = new TextProperty(myMessageLabel);
bindings.bind(message, new StringExpression(name) { @Override get() { return "Hello, " + name.get(); } }
```

When in doubt, keep these two rules of thumb in mind:

1. Prefer bindings whenever possible, and fall back on listeners otherwise.
1. Use Swing directly when a concept doesn't map cleanly to properties or it's a one-off case and not worth wrapping.

### Property bindings and listeners

The main reason you should always try to bind properties if you can is that properties are very concrete concepts, and connecting two properties together is very easy to reason about. They are also easy to chain and, later, decouple if you change your implementation.

Listeners can do anything bindings can do, and more - after all, bindings use listeners under the hood! But when you use listeners directly, they fire immediately, while bindings queue up their change to happen later. Delaying the change allows for coalescing of redundant updates and also prevents accidental recursive relationships from locking the EDT thread.

Listeners simply exist at a more foundational level than bindings. They are less sophisticated but also more flexible. For example, you cannot have a single binding that represents a 1-to-many relationship. The best you can do is bind multiple receiving properties to the same source property. Although this works, it may be wasteful and require a lot more boilerplate. However, it is trivial to write a listener which does many things per notification.

There are also times where a listener is undisputedly the correct choice, which is usually when you care more about the most recent change that happened rather than the full value of the property itself. For example, let's say you want to shake the window every time the user types in an invalid character for a particular text field, such as whitespace or symbols. The fact that the user typed an invalid character does not map cleanly to a property, nor does the ability to shake the window; rather, the invalid character is a single event you want to respond to, and the window shaking is the direct response.

### Vanilla swing

Sometimes you're faced with Swing code that does not have a corresponding Swing Property, and you're left with a dilemma. Should we keep using that Swing code as is, or should we port it over to a property concept?

It's not always an easy answer. Not all Swing code can be ported over, and not all Swing code that can be is worth porting over.

Keep in mind, in order to create a Swing Property, the value you are interested must be settable, queryable, and it must fire an event notifying you when it changes. Many core Swing components are good about this, but you may find yourself using some third-party component that doesn't fire an event upon updating the value. Or maybe you've got a way to set a value but not query it. If you encounter either of these issues, it means you simply cannot wrap the concept using Properties and must stick to pure Swing calls.

Sometimes, code may technically be a candidate for porting over to properties, but it's not worth it. To reiterate, properties are great for setting, getting, and chaining values, but if you don't need to do all those things, then they may be heavier weight than required. For example, say a component has a "regex filter" value, which you always simply just set and then trust the component to handle the rest. While you might be able to create a `RegexFilterProperty`, hooking up a listener and a get method, you should think if you really want to. Adding properties on top of such values just adds extra code and takes up more memory for essentially no gain. 

You may also find yourself being the only user of a custom Swing class, so it may be best just to use the Swing class directly instead of adding another custom Property class on top of that. In such edge cases, the fewer classes, the better.

However, if a Swing Property is missing and you think it's worth adding one, you should feel free to do so. A major advantage of properties is that their interface is simple and consistent.

For example, in Swing, you have to use a different text listener depending on whether you're listening to a button, a label, or a text field, and often the only way to remember which listener you're supposed to use is by doing a search on StackOverflow. A `TextProperty`, meanwhile, has a single `InvalidationListener` hook which always just works, making it a satisfying abstraction.

### Conclusion

In summary, bindings are the most restrictive use-case, which makes them easiest to reason about. When it makes sense to use them, use them! When you can't get a Swing to play nice with the property system, then don't force it and just use Swing code directly. And for everything else, use listeners.

## Debugging Properties

Although properties are powerful, when something goes wrong with a system that has dozens of bound properties in them, it can be hard to know where to set a breakpoint. To make matters trickier, properties chain so by the time you get a bad value, the original cause may be lost. Or, maybe, something's not updating and you're not sure why.

In these cases, the easiest way to debug properties may be by adding print statements. Don't forget you can always do this:

```java
myName.addListener(o -> System.out.println("Name changed to " + myName.get()));
```
