package mp3

import mp3.ID3Tag.MimeType

class ID3TagSpec {
    internal var flags: Int = 0
    internal var year: Int = 0
    internal var title: String? = null
    internal var artist: String? = null
    internal var album: String? = null
    internal var comment: String? = null
    internal var track_id3v1: Int = 0
    internal var genre_id3v1: Int = 0
    internal var albumart: ByteArray? = null
    internal var albumart_size: Int = 0
    internal var padding_size: Int = 0
    internal var albumart_mimetype: MimeType? = null
    internal var values = mutableListOf<String>()
    internal var num_values: Int = 0
    internal var v2_head: FrameDataNode? = null
    internal var v2_tail: FrameDataNode? = null
}