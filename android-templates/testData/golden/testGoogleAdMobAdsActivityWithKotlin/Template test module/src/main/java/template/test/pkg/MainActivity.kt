package template.test.pkg

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import android.widget.Button
import android.widget.TextView

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import template.test.pkg.databinding.ActivityMainBinding
import template.test.pkg.R

import java.util.Locale

// Remove the line below after defining your own ad unit ID.
private const val TOAST_TEXT = "Test ads are being shown. " +
        "To show live ads, replace the ad unit ID in res/values/strings.xml " +
        "with your own ad unit ID."
private const val START_LEVEL = 1

class MainActivity : AppCompatActivity() {

    private var currentLevel: Int = 0
    private var interstitialAd: InterstitialAd? = null
    private lateinit var nextLevelButton: Button
    private lateinit var levelTextView: TextView
    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MobileAds.initialize(
            this
        ) { }
        // Load the InterstitialAd and set the adUnitId (defined in values/strings.xml).
        loadInterstitialAd()

        // Create the next level button, which tries to show an interstitial when clicked.
        nextLevelButton = binding.nextLevelButton
        nextLevelButton.isEnabled = false
        nextLevelButton.setOnClickListener { showInterstitial() }

        levelTextView = binding.level
        // Create the text view to show the level number.
        currentLevel = START_LEVEL

        // Toasts the test ad message on the screen. Remove this after defining your own ad unit ID.
        Toast.makeText(this, TOAST_TEXT, Toast.LENGTH_LONG).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, getString(R.string.interstitial_ad_unit_id), adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    // The interstitialAd reference will be null until
                    // an ad is loaded.
                    interstitialAd = ad
                    nextLevelButton.setEnabled(true)
                    Toast.makeText(this@MainActivity, "onAdLoaded()", Toast.LENGTH_SHORT)
                        .show()
                    ad.setFullScreenContentCallback(
                        object : FullScreenContentCallback() {
                            override fun onAdDismissedFullScreenContent() {
                                // Called when fullscreen content is dismissed.
                                // Make sure to set your reference to null so you don't
                                // show it a second time.
                                interstitialAd = null
                                Log.d(TAG, "The ad was dismissed.")
                            }

                            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                // Called when fullscreen content failed to show.
                                // Make sure to set your reference to null so you don't
                                // show it a second time.
                                interstitialAd = null
                                Log.d(TAG, "The ad failed to show.")
                            }

                            override fun onAdShowedFullScreenContent() {
                                // Called when fullscreen content is shown.
                                Log.d(TAG, "The ad was shown.")
                            }
                        })
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    // Handle the error
                    Log.i(TAG, loadAdError.message)
                    interstitialAd = null
                    nextLevelButton.setEnabled(true)
                    val error: String = String.format(
                        Locale.ENGLISH,
                        "domain: %s, code: %d, message: %s",
                        loadAdError.domain,
                        loadAdError.code,
                        loadAdError.message
                    )
                    Toast.makeText(
                        this@MainActivity,
                        "onAdFailedToLoad() with error: $error", Toast.LENGTH_SHORT
                    )
                        .show()
                }
            })
    }

    private fun showInterstitial() {
        // Show the ad if it"s ready. Otherwise toast and reload the ad.
        if (interstitialAd != null) {
            interstitialAd!!.show(this)
        } else {
            Toast.makeText(this, "Ad did not load", Toast.LENGTH_SHORT).show()
            goToNextLevel()
        }
    }

    private fun goToNextLevel() {
        // Show the next level and reload the ad to prepare for the level after.
        levelTextView.text = "Level " + (++currentLevel)
        if (interstitialAd == null) {
            loadInterstitialAd()
        }
    }
}