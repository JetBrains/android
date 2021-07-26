val varInt by extra(1)
val varString by extra("foo")
val varList by extra(listOf("bar", "baz"))
val varMap by extra(mapOf("key" to "value", "num" to varInt))