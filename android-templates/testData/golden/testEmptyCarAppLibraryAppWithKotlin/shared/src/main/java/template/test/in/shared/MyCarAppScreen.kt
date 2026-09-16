package template.test.`in`.shared

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

class MyCarAppScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder("Hello world!")
            .setHeaderAction(Action.APP_ICON)
            .setTitle("MyCarAppScreen")
            .build()
    }
}