val activity = mutableMapOf<String,String>()
activity["foo"] = "bar"
val deps by extra(mutableMapOf<String,Any>())
deps["activity"] = activity
