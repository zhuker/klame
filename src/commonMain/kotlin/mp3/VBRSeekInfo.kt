package mp3

class VBRSeekInfo {
    /**
     * What we have seen so far.
     */
    internal var sum: Int = 0
    /**
     * How many frames we have seen in this chunk.
     */
    internal var seen: Int = 0
    /**
     * How many frames we want to collect into one chunk.
     */
    internal var want: Int = 0
    /**
     * Actual position in our bag.
     */
    internal var pos: Int = 0
    /**
     * Size of our bag.
     */
    internal var size: Int = 0
    /**
     * Pointer to our bag.
     */
    internal var bag: IntArray? = null
    internal var nVbrNumFrames: Int = 0
    internal var nBytesWritten: Int = 0
    /* VBR tag data */
    internal var TotalFrameSize: Int = 0
}