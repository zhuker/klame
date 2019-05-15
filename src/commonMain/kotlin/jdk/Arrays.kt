package jdk

object Arrays {
    fun fill(arr: IntArray, v: Int) {
        for (i in arr.indices) {
            arr[i] = v
        }
    }

    fun fill(arr: ShortArray, v: Short) {
        for (i in arr.indices) {
            arr[i] = v
        }
    }

    fun fill(a: IntArray, fromIndex: Int, toIndex: Int, v: Int) {
        rangeCheck(a.size, fromIndex, toIndex)
        for (i in fromIndex until toIndex)
            a[i] = v
    }

    fun fill(a: ByteArray, fromIndex: Int, toIndex: Int, v: Byte) {
        rangeCheck(a.size, fromIndex, toIndex)
        for (i in fromIndex until toIndex)
            a[i] = v
    }

    fun fill(a: ShortArray, fromIndex: Int, toIndex: Int, v: Short) {
        rangeCheck(a.size, fromIndex, toIndex)
        for (i in fromIndex until toIndex)
            a[i] = v
    }

    fun fill(a: FloatArray, fromIndex: Int, toIndex: Int, v: Float) {
        rangeCheck(a.size, fromIndex, toIndex)
        for (i in fromIndex until toIndex)
            a[i] = v
    }

    fun sort(a: FloatArray, fromIndex: Int, toIndex: Int) {
        rangeCheck(a.size, fromIndex, toIndex)
        val slice = a.sliceArray(fromIndex until toIndex)
        slice.sort()
        slice.copyInto(a, fromIndex)
    }

    /**
     * Checks that `from` and `to` are in
     * the range of [0..size] and throws an appropriate exception, if they aren't.
     */
    private fun rangeCheck(size: Int, fromIndex: Int, toIndex: Int) {
        when {
            fromIndex > toIndex -> throw IllegalArgumentException("fromIndex ($fromIndex) is greater than toIndex ($toIndex).")
            fromIndex < 0 -> throw IndexOutOfBoundsException("fromIndex ($fromIndex) is less than zero.")
            toIndex > size -> throw IndexOutOfBoundsException("toIndex ($toIndex) is greater than size ($size).")
        }
    }


}