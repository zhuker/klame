package mp3

enum class VbrMode {
    vbr_off, vbr_mt, vbr_rh, vbr_abr, vbr_mtrh;


    companion object {
        val vbr_default = vbr_mtrh
    }
}
