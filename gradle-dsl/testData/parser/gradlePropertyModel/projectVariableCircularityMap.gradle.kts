project.extra["foo"] = mapOf("bar" to "buzz")
val foo = project.extra["foo"]
val group by extra(foo["bar"])
