package mp3

class HuffCodeTab(
        /**
         * max. x-index+
         */
        internal val xlen: Int,
        /**
         * max number to be stored in linbits
         */
        internal val linmax: Int,
        /**
         * pointer to array[xlen][ylen]
         */
        internal val table: IntArray,
        /**
         * pointer to array[xlen][ylen]
         */
        internal val hlen: IntArray)