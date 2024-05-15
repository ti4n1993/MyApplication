package com.example.remote

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.remote.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale


val eventChanged = MutableSharedFlow<Unit>(0, 1)

class MainActivity : FragmentActivity() {
    private var password by mutableStateOf("")
    private val keyguardManager by lazy { getSystemService<KeyguardManager>() }
    private val mediaProjectionManager by lazy { getSystemService<MediaProjectionManager>() }
    private var mediaProjection: MediaProjection? = null
    private var mResultCode = 0
    private var mResultData: Intent? = null
    private val metrics by lazy {
        DisplayMetrics().apply {
            windowManager.defaultDisplay.getMetrics(this)
        }
    }
    private val mScreenDensity by lazy {
        metrics.densityDpi
    }
    private var mVirtualDisplay: VirtualDisplay? = null
    private val imageReader by lazy {
        ImageReader.newInstance(
            metrics.widthPixels, metrics.heightPixels, android.graphics.PixelFormat.RGBA_8888, 2
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .systemBarsPadding()
                    ) {
                        Text(text = password)
                        Button(
                            onClick = {
                                val biometricPrompt = BiometricPrompt(
                                    this@MainActivity,
                                    object : BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                            super.onAuthenticationSucceeded(result)
                                            lifecycleScope.launch {
                                                Network.setPassword(
                                                    Settings.Secure.getString(
                                                        contentResolver,
                                                        Settings.Secure.ANDROID_ID
                                                    ), "1", password
                                                )
                                            }
                                        }

                                        override fun onAuthenticationError(
                                            errorCode: Int,
                                            errString: CharSequence
                                        ) {
                                            super.onAuthenticationError(errorCode, errString)
                                        }
                                    })
                                // 创建锁屏密码输入的弹框以及需要显示的文字
                                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                    .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                                    .setTitle("锁屏密码")
                                    .setConfirmationRequired(true)
                                    .setSubtitle("请输入锁屏密码验证您的身份")
                                    .build()
                                // 发起验证
                                biometricPrompt.authenticate(promptInfo)
                            }
                        ) {
                            Text(text = "打开锁屏")
                        }
                    }
                }
            }
        }
        getPermission()
        initPassword()
        if (savedInstanceState != null) {
            mResultCode = savedInstanceState.getInt("mResultCode");
            mResultData = savedInstanceState.getParcelable("mResultData");
        }
        startScreenCapture()
        eventChanged.debounce(1000).onEach {
            captureScreenshot()
        }.launchIn(lifecycleScope)
        lifecycleScope.launch {
            Network.addPhone(
                Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID),
                Locale.getDefault().language,
                "${metrics.widthPixels}",
                "${metrics.heightPixels}",
                "1",
                "1",
                "${getSystemService<BatteryManager>()?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}"
            )
        }
        monitorBattery()
        lifecycleScope.launch {
            Network.remoteControl()
        }
    }

    private fun initPassword() {
        // 在Activity中注册广播接收器
        val messageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if ("PASSWORD" == intent.action) {
                    val data = intent.getStringExtra("data")
                    password = data ?: ""
                }
            }
        }

        val filter = IntentFilter("PASSWORD")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(messageReceiver, filter, RECEIVER_NOT_EXPORTED)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 3) {
            if (resultCode == RESULT_OK) {
                println("--------------密码正确")
                // 用户成功验证了他们的密码

            } else {
                // 用户取消了验证或验证失败
            }
        }
        if (requestCode == 100) {
            if (resultCode != RESULT_OK) {
                return
            }
            mResultCode = resultCode
            mResultData = data
            setUpMediaProjection()
            setUpVirtualDisplay()
        }
    }

    private fun getPermission() {
//        if (!Settings.canDrawOverlays(this)) {
//            val intent = Intent(
//                Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + this.packageName)
//            )
//            startActivityForResult(intent, 1)
//        }
        if (!isAccessibilityServiceEnabled()) {
            AlertDialog.Builder(this).setTitle("Enable Accessibility Service")
                .setMessage("This app requires you to enable the MyAccessibilityService.")
                .setPositiveButton("Go to Settings") { dialog, which ->
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }.setNegativeButton("Cancel", null).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        // 获取AccessibilityManager实例
        val am = getSystemService<AccessibilityManager>()

        // 创建一个查询特定无障碍服务的ID字符串
// 格式为: "包名/服务全路径类名"
        val myAccessibilityServiceId = "com.example.remote/.MyAccessibilityService"

        val my = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?.find { myAccessibilityServiceId == it.id }
        return my != null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mResultData != null) {
            outState.putInt("mResultCode", mResultCode);
            outState.putParcelable("mResultData", mResultData);
        }
    }

    private fun startScreenCapture() {
        val intent = Intent(this, MediaProjectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        if (mediaProjection != null) {
            setUpVirtualDisplay()
        } else if (mResultCode != 0 && mResultData != null) {
            setUpMediaProjection()
            setUpVirtualDisplay()
        } else {
            startActivityForResult(
                mediaProjectionManager?.createScreenCaptureIntent()!!, 100
            )
        }
    }

    private fun setUpMediaProjection() {
        if (mResultCode != 0 && mResultData != null) mediaProjection =
            mediaProjectionManager?.getMediaProjection(mResultCode, mResultData!!)
    }

    private fun setUpVirtualDisplay() {
        mVirtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            metrics.widthPixels,
            metrics.heightPixels,
            mScreenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            null
        )
    }

    private fun captureScreenshot() {
        val image: Image? = imageReader.acquireLatestImage()
        val bitmap: Bitmap? = image?.let { imageToBitmap(it) }
        image?.close()
        //save bitmap to sdcard
        if (bitmap != null) {
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            val encoded: String = Base64.encodeToString(byteArray, Base64.DEFAULT)
            lifecycleScope.launch {
                Network.sendMessage(encoded)
            }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * metrics.widthPixels

        val bitmap = Bitmap.createBitmap(
            metrics.widthPixels + rowPadding / pixelStride,
            metrics.heightPixels,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(
            bitmap, 0, 0, metrics.widthPixels, metrics.heightPixels
        )
    }

    private fun saveBitmapToSdCard(bitmap: Bitmap) {
        val externalStorageDirectory = cacheDir
        val appDirectory = File(externalStorageDirectory, "My App")
        appDirectory.mkdirs()
        val file = File(appDirectory, "my_bitmap_${System.currentTimeMillis()}.png")
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
    }

    private fun monitorBattery() {
        registerReceiver(
            BatteryChangeReceiver(), IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        lifecycleScope.launch {
            battery.drop(1).debounce(1000).collectLatest {
                Network.changeStatus(
                    Settings.Secure.getString(
                        contentResolver, Settings.Secure.ANDROID_ID
                    ),
                    "1",
                    if (getSystemService<PowerManager>()?.isInteractive == true) "1" else "0",
                    if (getSystemService<KeyguardManager>()?.isDeviceLocked == false) "1" else "0",
                    "$it"
                )
            }
        }
    }
}