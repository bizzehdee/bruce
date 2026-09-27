package com.bizzeh.bruce.inference

internal object LlamaNative {
    init {
        System.loadLibrary("bruce")
    }

    external fun version(): String
}
