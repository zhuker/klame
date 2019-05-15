package mp3

/**
 * Structure to receive extracted header (toc may be null).
 *
 * @author Ken
 */
class VBRTagData {
    /**
     * From MPEG header 0=MPEG2, 1=MPEG1.
     */
    var hId: Int = 0
    /**
     * Sample rate determined from MPEG header.
     */
    var samprate: Int = 0
    /**
     * From Vbr header data.
     */
    var flags: Int = 0
    /**
     * Total bit stream frames from Vbr header data.
     */
    var frames: Int = 0
    /**
     * Total bit stream bytes from Vbr header data.
     */
    var bytes: Int = 0
    /**
     * Encoded vbr scale from Vbr header data.
     */
    var vbrScale: Int = 0
    /**
     * May be null if toc not desired.
     */
    var toc = ByteArray(VBRTag.NUMTOCENTRIES)
    /**
     * Size of VBR header, in bytes.
     */
    var headersize: Int = 0
    /**
     * Encoder delay.
     */
    var encDelay: Int = 0
    /**
     * Encoder padding added at end of stream.
     */
    var encPadding: Int = 0
}