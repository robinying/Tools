package com.robin.tools.feature.lightlux.data

/**
 * Navigation sealed class for LightLux screens.
 */
sealed class LightLuxNavHost {
    object Meter : LightLuxNavHost()
    object SnapshotList : LightLuxNavHost()
}
