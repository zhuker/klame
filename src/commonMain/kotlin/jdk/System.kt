package jdk

object System {
    val out: PrintStream = PrintStream()
    val err: PrintStream = PrintStream()

    fun arraycopy(src: FloatArray, srcpos: Int, dst: FloatArray, dstpos: Int, len: Int) {
        src.copyInto(dst, dstpos, srcpos, srcpos + len)
    }

    fun arraycopy(src: DoubleArray, srcpos: Int, dst: DoubleArray, dstpos: Int, len: Int) {
        src.copyInto(dst, dstpos, srcpos, srcpos + len)
    }

    fun arraycopy(src: IntArray, srcpos: Int, dst: IntArray, dstpos: Int, len: Int) {
        src.copyInto(dst, dstpos, srcpos, srcpos + len)
    }

    fun arraycopy(src: ByteArray, srcpos: Int, dst: ByteArray, dstpos: Int, len: Int) {
        src.copyInto(dst, dstpos, srcpos, srcpos + len)
    }

    fun arraycopy(src: FloatArray, srcpos: Int, dst: DoubleArray, dstpos: Int, len: Int) {
        throw IllegalArgumentException("ArrayStoreException cant arraycopy FloatArray to DoubleArray")
    }
}

fun assert(expect: Boolean) {
    if (!expect) throw  IllegalStateException("assertion failed")
}

inline infix fun Byte.and(i: Int): Int = this.toInt() and i
inline infix fun Byte.xor(i: Int): Int = this.toInt() xor i
inline infix fun Byte.shl(i: Int): Int = this.toInt() shl i
inline infix fun Byte.shr(i: Int): Int = this.toInt() shr i
inline infix fun Short.shr(i: Int): Int = this.toInt() shr i
inline infix fun Short.and(i: Int): Int = this.toInt() and i

fun FloatArray.clone() = this.copyOf()
fun IntArray.clone() = this.copyOf()

fun String.Companion.format(template: String, vararg args: Any): String {
    return template
}

object Integer {
    val MAX_VALUE = Int.MAX_VALUE

    fun parseInt(str: String): Int {
        return str.toInt()
    }

    fun valueOf(x: Int): Int {
        return x
    }

    fun valueOf(x: String): Int {
        return x.toInt()
    }
}

object Character {
    fun toUpperCase(c: Char): Char {
        return c.toUpperCase()
    }

    fun toLowerCase(c: Char): Char {
        return c.toLowerCase()
    }
}

fun String.toCharArray(): CharArray {
    return this.map { it }.toCharArray()
}

fun makeString(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size, charset: String): String {
    val chars = bytes.copyOfRange(offset, offset + length).map { it.toChar() }.toCharArray()
    return String(chars)
}

fun makeString(bytes: ByteArray, charset: String): String {
    val chars = bytes.map { it.toChar() }.toCharArray()
    return String(chars)
}
