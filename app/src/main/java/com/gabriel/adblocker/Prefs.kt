package com.gabriel.adblocker

import android.content.Context

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    fun wanted(c: Context) = p(c).getBoolean("wanted", false)
    fun setWanted(c: Context, v: Boolean) = p(c).edit().putBoolean("wanted", v).apply()
    fun cutEnabled(c: Context) = p(c).getBoolean("cut", false)
    fun setCutEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("cut", v).apply()
    fun markerCutEnabled(c: Context) = p(c).getBoolean("markercut", false)
    fun setMarkerCutEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("markercut", v).apply()
}
