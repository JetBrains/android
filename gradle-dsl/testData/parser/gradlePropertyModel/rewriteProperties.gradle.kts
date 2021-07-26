val varInt by extra(1)
val varString by extra("foo")
val varList by extra(listOf<String>("bar", "baz"))
val varMap by extra(mapOf<String,Any>("key" to "value", "num" to varInt))