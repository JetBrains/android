val activity = mapOf<String,String>()
activity["foo"] = "bar"
val deps by extra(mapOf<String,Any>())
deps["activity"] = activity
