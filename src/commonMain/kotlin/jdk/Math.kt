package jdk

import kotlin.math.pow

object Math {
    const val PI: Double = kotlin.math.PI

    fun min(a: Int, b: Int): Int = kotlin.math.min(a, b)
    fun min(a: Float, b: Float): Float = kotlin.math.min(a, b)
    fun min(a: Double, b: Double): Double = kotlin.math.min(a, b)
    fun max(a: Int, b: Int): Int = kotlin.math.max(a, b)
    fun max(a: Float, b: Float): Float = kotlin.math.max(a, b)
    fun max(a: Double, b: Double): Double = kotlin.math.max(a, b)
    fun log10(x: Double): Double = kotlin.math.log10(x)
    fun pow(x: Double, pow: Double): Double = x.pow(pow)
    fun abs(x: Int): Int = kotlin.math.abs(x)
    fun abs(x: Float): Float = kotlin.math.abs(x)
    fun ceil(x: Double): Double = kotlin.math.ceil(x)
    fun sqrt(x: Double): Double = kotlin.math.sqrt(x)
    fun cos(x: Double): Double = kotlin.math.cos(x)
    fun sin(x: Double): Double = kotlin.math.sin(x)
    fun exp(x: Double): Double = kotlin.math.exp(x)
    fun abs(x: Double): Double = kotlin.math.abs(x)
    fun floor(x: Double): Double = kotlin.math.floor(x)
    fun tan(x: Double): Double = kotlin.math.tan(x)
    fun atan(x: Double): Double = kotlin.math.atan(x)
    fun log(d: Double): Double = kotlin.math.ln(d)
}