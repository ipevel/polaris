package com.slte.app.kernel

interface SpeedResultStore {
    fun saveSpeedResults(results: Map<String, Int>)

    fun getSpeedResults(): Map<String, Int>?
}
