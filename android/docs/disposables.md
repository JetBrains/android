# Disposables

## Background

As a part of one of the unconference talks at Android Studio Offsite 2017 in Kirkland, shiufai@, ralucas@ and adehtiarov@ scratched the
surface of what can lead to suboptimal performance and memory usage of the IDE, especially within one ongoing user session. One of the
points we touched briefly was the use of `Disposable` and `Disposer`. In short, this concept in IntelliJ OpenAPI is designed in a way
similar to what some other frameworks have (e.g. Qt): objects implementing `Disposable` interface are connected with each other on the
basis of parent-child relationship, so that when the parent gets disposed, all of its children get disposed recursively too.

This allows a great extent of freedom and determinism in object's lifetime control, but also means that if the parent instance is chosen too
gratuitously, the child may consume resources for a far longer period of time than it actually should. This can be a serious source of
contention for repeated activities executed in the same instance of Studio due to leaving some zombie objects behind as a result of each
invocation.

An additional challenge is that this kind of issues won't be reported by the regular leak checker utilities, because technically, it's not a
memory leak from the test suite perspective.

This document focuses on this aspect and dives into commonly used contexts where this is likely to be a problem, what are the implications,
fixes, pitfalls etc.

## Message bus connection

Consider the following snippet:

```
project.getMessageBus().connect(project).subscribe(_TOPIC_, new SomeListenerInterface() {...});
```

This is a typical way of creating subscriptions to various IDE model events in Studio. It probably looks familiar to many, but the truth is
that it's not always innocent.

Here is a short recap of what happens above:

* `project.getMessageBus().connect(disposable)` creates a connection object whose lifetime is bound to that of the passed disposable
  instance.
* Since `Project` implements `Disposable` and is the "easiest" object to access almost everywhere, I've seen some ~100 occurrences of this
  pattern, just by performing a full text search by `.connect(project`, `.connect(myProject` etc.
* Now, the listener, being an anonymous or inner class, holds an implicit reference to the enclosing instance. Since the listener belongs to
  the connection, the enclosing instance  lifetime is therefore also bound to the lifetime of the passed Disposable.

So virtually every object subscribed in such a way remains in the memory as long as the entire project is alive. While this is indeed valid
in some cases like project-level IDEA services, this can be highly suboptimal when the enclosing instance is relatively heavy (which is
often the case), and doesn't have to be alive till the project is closed.

### Where such a subscription may cause issues

For example, this is clearly not valid for temporary UI components or their children. They are often subscribed to IDE events in their
constructors, in order to update UI when e.g. VFS roots change or the IDE enters dumb mode. Once the UI component is not needed any more, it
may still remain in memory due to the subscription it has, and even continue to process the model events spending CPU cycles needlessly.

I've also seen examples of window content being cleared with dispose=true, which did not in fact release the UI components as expected.

## Choice of `Disposable` parent

To sum up, my feeling is that normally there can be more fine-grained disposables figured out in each particular context. This is applicable
not only to `MessageBus` connections, but to any context when a `Disposable` parent is being passed to a method.

A few alternatives to project instance which come to my mind:

* If it's a custom component, just implement `Disposable` for it, and `dispose()` body can well be empty - but this will already do the job of
  eliminating implicit references to the enclosing instance etc.
* It's likely that some of the objects you're dealing with around already implement `Disposable`, so they can be used as a parent if their
  lifetime is greater or equals to the object being created.
* You can call `Disposer.newDisposable()` to create a fresh `Disposable` that you can manually dispose at the end of your method, if such
  fine grained control is needed.

### Pitfalls

Although project instance is typically the longest living Disposable, it still gets disposed at some point, and in those situations you
don't want your objects to be alive either, both the child and the carefully chosen parent. Therefore it's not enough to establish
parent-child relationship between two objects in question, but register the parent against the project instance (or, another longer-lived
parent, for that matter):

```
Disposer.register(project, parent);
```

This way, the child is guaranteed to be disposed either when the parent is disposed explicitly via `Disposer.dispose(parent)`, or when the
project is no longer alive - whichever comes first.

For example, in [ag/3057999](http://ag/3057999) the old notification panel is disposed explicitly when a new one is ready, but also each
notification panel is registered against the project instance. In turn, the MessageBus connection created for the panel is bound to the
panel instance itself, so with all these registrations in place the panel is guaranteed not to exceed its necessary lifetime.

Omitting the registration against the project will lead to creating another root in the Disposables tree, so the instances there will be
agnostic to project lifetime, but will most likely hold a reference to it, which will lead to the project instance leak, and fortunately is
likely to result in massive leakhunter reports on PSQ.
