package com.bizzeh.bruce.benchmark

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView

/**
 * A blank screen that keeps the app in front while a device benchmark runs. Android gives a process
 * on screen more CPU cores than one in the background (the Pixel 11 leaves its fastest core out of
 * the "foreground" group), so speeds measured off screen understate real use. MainActivity is not
 * used because it loads the owner's chosen model at launch.
 */
class BenchmarkScreenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(TextView(this).apply { text = "Bruce benchmark running" })
    }
}
