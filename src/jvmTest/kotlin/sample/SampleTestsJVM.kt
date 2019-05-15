package sample

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleTestsJVM {
    @Test
    fun testHello() {
        println("hello")
        assertTrue("JVM" in hello())
    }

    @Test
    fun arrayCopyFloatArrayToDoubleArray() {
        val f = floatArrayOf(1f, 2f, 3f)
        val d = doubleArrayOf(0.0, 0.0, 0.0)
        System.arraycopy(f, 0, d, 0, 2)
        assertEquals(1.0, d[0])
        assertEquals(2.0, d[1])
        assertEquals(0.0, d[1])
    }
}