package mp3

class FrameDataNode {
    internal var nxt: FrameDataNode? = null
    /**
     * Frame Identifier
     */
    internal var fid: Int = 0
    /**
     * 3-character language descriptor
     */
    internal var lng: String? = null

    internal var dsc = Inf()
    internal var txt = Inf()
}