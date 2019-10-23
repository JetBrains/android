package test.langdb.bitmaps

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavHost
import androidx.navigation.Navigation

class MainActivity : FragmentActivity(), NavHost {
    private lateinit var navController: NavController

    override fun getNavController() = navController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        navController = Navigation.findNavController(this, R.id.nav_host_fragment)
    }
}
