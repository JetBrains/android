Normally, I would discourage writing custom wrappers on top of protobuf APIs, as it means how you
instantiate objects is inconsistent. Really, the ideal solution is if tools generated this Kotlin
code for us in the first place.

But for now, as tests in this package need to create handcrafted proto messages that are pretty
extensive, it's a win to keep the builder boilerplate minimized. And it's just for test code, so
the scope of these utility methods is limited.

The rule of thumb for adding a utility method in this package or not is if you find yourself
otherwise writing a ton of `Proto.Class.newBuilder().apply` noise in your tests, especially if
within deep, nested tree structures.
