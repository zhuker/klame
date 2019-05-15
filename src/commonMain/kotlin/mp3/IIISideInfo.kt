package mp3

class IIISideInfo {

    var tt = Array(2) { Array(2){GrInfo()} }
    var main_data_begin: Int = 0
    var private_bits: Int = 0
    var resvDrain_pre: Int = 0
    var resvDrain_post: Int = 0
    var scfsi = Array(2) { IntArray(4) }
}