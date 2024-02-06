package com.coderGtm.yantra.activities

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.KeyEvent
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.coderGtm.yantra.R
import com.coderGtm.yantra.SHARED_PREFS_FILE_NAME
import com.coderGtm.yantra.YantraLauncher
import com.coderGtm.yantra.databinding.ActivityMainBinding
import com.coderGtm.yantra.getInit
import com.coderGtm.yantra.isNetworkAvailable
import com.coderGtm.yantra.requestCmdInputFocusAndShowKeyboard
import com.coderGtm.yantra.requestUpdateIfAvailable
import com.coderGtm.yantra.runInitTasks
import com.coderGtm.yantra.setWallpaperFromUri
import com.coderGtm.yantra.terminal.Terminal
import com.coderGtm.yantra.toast
import com.coderGtm.yantra.views.TerminalGestureListenerCallback
import com.limurse.iap.BillingClientConnectionListener
import com.limurse.iap.DataWrappers
import com.limurse.iap.IapConnector
import com.limurse.iap.PurchaseServiceListener
import java.util.Locale


class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener, TerminalGestureListenerCallback {

    private lateinit var primaryTerminal: Terminal
    private lateinit var app: YantraLauncher
    private lateinit var binding: ActivityMainBinding
    private lateinit var iapConnector: IapConnector

    var tts: TextToSpeech? = null
    var ttsTxt = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as YantraLauncher
        app.preferenceObject = applicationContext.getSharedPreferences(SHARED_PREFS_FILE_NAME,0)

        primaryTerminal = Terminal(
            activity = this@MainActivity,
            binding = binding,
            preferenceObject = app.preferenceObject
        )
        primaryTerminal.initialize()

        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Handle the back button event
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (primaryTerminal.initialized) {
            Thread {
                val initList = getInit(app.preferenceObject)
                runInitTasks(initList, app.preferenceObject, primaryTerminal)
            }.start()
        }
    }
    override fun onRestart() {
        super.onRestart()
        val unwrappedCursorDrawable = AppCompatResources.getDrawable(this,
            R.drawable.cursor_drawable
        )
        val wrappedCursorDrawable = DrawableCompat.wrap(unwrappedCursorDrawable!!)
        DrawableCompat.setTint(wrappedCursorDrawable, primaryTerminal.theme.buttonColor)
        Thread {
            requestUpdateIfAvailable(app.preferenceObject, this@MainActivity)
        }.start()
    }
    override fun onDestroy() {
        super.onDestroy()
        try {
            iapConnector.destroy()
        }
        catch(_: java.lang.Exception) {}
    }
    override fun onSingleTap() {
        val oneTapKeyboardActivation = app.preferenceObject.getBoolean("oneTapKeyboardActivation",true)
        if (oneTapKeyboardActivation) {
            requestCmdInputFocusAndShowKeyboard(this@MainActivity, binding)
        }
    }
    override fun onDoubleTap() {
        val cmdToExecute = app.preferenceObject.getString("doubleTapCommand", "lock")
        if (cmdToExecute != "") {
            //execute command
            primaryTerminal.handleCommand(cmdToExecute!!)
        }
    }

    override fun onSwipeRight() {
        val cmdToExecute = app.preferenceObject.getString("swipeRightCommand", "echo Right Swipe detected! You can change the command in settings.")
        if (cmdToExecute != "") {
            //execute command
            primaryTerminal.handleCommand(cmdToExecute!!)
        }
    }

    override fun onSwipeLeft() {
        val cmdToExecute = app.preferenceObject.getString("swipeLeftCommand", "echo Left Swipe detected! You can change the command in settings.")
        if (cmdToExecute != "") {
            //execute command
            primaryTerminal.handleCommand(cmdToExecute!!)
        }
    }

    override fun onInit(status: Int) {
        //TTS Initialization function
        if (status == TextToSpeech.SUCCESS) {
            val result = tts!!.setLanguage(Locale.getDefault())

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                primaryTerminal.output("Error: TTS language not supported!", primaryTerminal.theme.errorTextColor, null)
            } else {
                tts!!.setSpeechRate(.7f)
                tts!!.speak(ttsTxt, TextToSpeech.QUEUE_FLUSH, null,"")
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                primaryTerminal.output("TTS synthesized! Playing now...", primaryTerminal.theme.successTextColor, null)
            }
            override fun onDone(utteranceId: String) {
                primaryTerminal.output("Shutting down TTS engine...", primaryTerminal.theme.resultTextColor, null)

                if (tts != null) {
                    tts!!.stop()
                    tts!!.shutdown()
                }
                primaryTerminal.output("TTS engine shutdown.", primaryTerminal.theme.resultTextColor, null)
            }
            override fun onError(utteranceId: String) {
                primaryTerminal.output("TTS error!!", primaryTerminal.theme.errorTextColor, null)
                primaryTerminal.output("Shutting down TTS engine...", primaryTerminal.theme.resultTextColor, null)

                if (tts != null) {
                    tts!!.stop()
                    tts!!.shutdown()
                }
                primaryTerminal.output("TTS engine shutdown.", primaryTerminal.theme.resultTextColor, null)

            }
        })
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_UP) {
            primaryTerminal.cmdUp()
        }
        else if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_UP) {
            primaryTerminal.cmdDown()
        }
        else if (event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
            val inputReceived = binding.cmdInput.text.toString().trim()
            primaryTerminal.handleInput(inputReceived)
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            primaryTerminal.output("Permission denied!", primaryTerminal.theme.errorTextColor, null)
        } else {
            primaryTerminal.output("Permission Granted", primaryTerminal.theme.successTextColor, null)
        }
    }

    private fun initializeIAP(purchasePostInitialization: Boolean = false, skuId: String = "", purchaseRetryCount: Int = 0) {
        iapConnector = IapConnector(
            context = this,
            nonConsumableKeys = listOf("fontpack","gupt","customtheme"),
            consumableKeys = listOf("donate0","donate1","donate2","donate3","donate4"),
            subscriptionKeys = listOf(),
            key = packageManager.getApplicationInfo(this.packageName, PackageManager.GET_META_DATA).metaData["LICENSE_KEY"] as String, // pass your app's license key
            enableLogging = false
        )
        iapConnector.addBillingClientConnectionListener(object : BillingClientConnectionListener {
            override fun onConnected(status: Boolean, billingResponseCode: Int) {
                if (status && purchasePostInitialization && purchaseRetryCount>0) {
                    initializeProductPurchase(skuId, purchaseRetryCount-1)
                }
            }

        })
        iapConnector.addPurchaseListener(object : PurchaseServiceListener {
            override fun onPricesUpdated(iapKeyPrices: Map<String, List<DataWrappers.ProductDetails>>) {
                // list of available products will be received here, so you can update UI with prices if needed
            }

            override fun onProductPurchased(purchaseInfo: DataWrappers.PurchaseInfo) {
                app.preferenceObject.edit().putBoolean(purchaseInfo.sku+"___purchased", true).apply()
                runOnUiThread {
                    toast(baseContext, "Purchase successful")
                    primaryTerminal.output("[+] Purchase successful. Thank you for your support!", primaryTerminal.theme.successTextColor, null)
                }
            }

            override fun onProductRestored(purchaseInfo: DataWrappers.PurchaseInfo) {
                app.preferenceObject.edit().putBoolean(purchaseInfo.sku+"___purchased", true).apply()
            }

            override fun onPurchaseFailed(purchaseInfo: DataWrappers.PurchaseInfo?, billingResponseCode: Int?) {
                // avoiding setting purchased to false here, because not sure if it's a purchase failure (onPurchaseFailed is a relatively new function)
                runOnUiThread {
                    toast(baseContext, "Purchase failed")
                    primaryTerminal.output("[-] Purchase failed. Please try again.", primaryTerminal.theme.errorTextColor, null)
                }
            }
        })
    }
    fun initializeProductPurchase(skuId: String, retryCount: Int = 5) {
        primaryTerminal.output("Initializing purchase...Please wait.",primaryTerminal.theme.resultTextColor, null)
        // check internet connection
        if (!isNetworkAvailable(this@MainActivity)) {
            primaryTerminal.output("No internet connection. Please connect to the internet and try again.",primaryTerminal.theme.errorTextColor, null)
            return
        }
        try {
            iapConnector.purchase(this, skuId)
        } catch (e: Exception) {
            if (retryCount > 0) {
                initializeIAP(purchasePostInitialization = true, skuId = skuId, purchaseRetryCount = retryCount)
            }
            else {
                primaryTerminal.output("Error initializing purchase. Please try again.",primaryTerminal.theme.errorTextColor, null)
            }
        }
    }

    // Registers a photo picker activity launcher in single-select mode.
    val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        // Callback is invoked after the user selects a media item or closes the
        // photo picker.
        if (uri != null) {
            setWallpaperFromUri(uri, this, primaryTerminal.theme.bgColor, app.preferenceObject)
            primaryTerminal.output("Selected Wallpaper applied!", primaryTerminal.theme.successTextColor, null)
        } else {
            primaryTerminal.output("No Image selected!", primaryTerminal.theme.resultTextColor, Typeface.ITALIC)
        }
    }
    var yantraSettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            if (data != null) {
                val settingsChanged = data.getBooleanExtra("settingsChanged", false)
                if (settingsChanged) {
                    recreate()
                }
            }
        }
    }
}
