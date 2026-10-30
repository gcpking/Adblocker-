package com.gabriel.adblocker

import android.content.Context

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    fun wanted(c: Context) = p(c).getBoolean("wanted", false)
    fun setWanted(c: Context, v: Boolean) = p(c).edit().putBoolean("wanted", v).apply()
}
