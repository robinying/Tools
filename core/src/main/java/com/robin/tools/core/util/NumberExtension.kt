package com.robin.tools.core.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

const val DEFAULT_DECIMAL_NUMBER = 2
const val DEFAULT_SEPARATE_NUMBER = 3

fun Number.formatNumber(
    addComma: Boolean = false,
    modeFloor: Boolean = true,
    decimalNum: Int? = DEFAULT_DECIMAL_NUMBER
): String {
    var decimal = decimalNum
    if (decimal == null) {
        decimal = DEFAULT_DECIMAL_NUMBER
    }
    val decimalFormat = DecimalFormat()
    decimalFormat.maximumFractionDigits = decimal
    decimalFormat.groupingSize = if (addComma) DEFAULT_SEPARATE_NUMBER else 0
    if (modeFloor) decimalFormat.roundingMode = RoundingMode.FLOOR
    return decimalFormat.format(this)
}

fun String.formatNumber(
    addComma: Boolean = false, modeFloor: Boolean = true,
    decimalNum: Int? = DEFAULT_DECIMAL_NUMBER
): String =
    this.toBigDecimalWithNull().formatNumber(addComma, modeFloor, decimalNum)

fun String?.toBigDecimalWithNull(default: BigDecimal = BigDecimal.ZERO): BigDecimal {
    if (this.isNullOrBlank()) return default
    return try {
        this.toBigDecimal()
    } catch (e: NumberFormatException) {
        default
    }
}

fun String?.toIntWithNull(default: Int = 0): Int {
    if (this.isNullOrBlank()) return default
    return try {
        this.toInt()
    } catch (e: NumberFormatException) {
        default
    }
}

fun String?.toFloatWithNull(default: Float = 0F): Float {
    if (this.isNullOrBlank()) return default
    return try {
        this.toFloat()
    } catch (e: NumberFormatException) {
        default
    }
}

fun String?.toDoubleWithNull(default: Double = 0.toDouble()): Double {
    if (this.isNullOrBlank()) return default
    return try {
        this.toDouble()
    } catch (e: NumberFormatException) {
        default
    }
}