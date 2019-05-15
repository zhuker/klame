package mp3

import jdk.and
import jdk.makeString
import java.io.RandomAccessFile

/**
 * Write final VBR tag to the file.
 *
 * @param gfp
 * global flags
 * @param stream
 * stream to add the VBR tag to
 * @return 0 (OK), -1 else
 * @throws IOException
 * I/O error
 */
//    @Throws(IOException::class)
fun putVbrTag(
    tag: VBRTag,
    gfp: LameGlobalFlags,
    stream: RandomAccessFile
): Int {
    val gfc = gfp.internal_flags

    if (gfc!!.VBR_seek_table.pos <= 0)
        return -1

    // Seek to end of file
    stream.seek(stream.length())

    // Get file size, abort if file has zero length.
    if (stream.length() == 0L)
        return -1

    // The VBR tag may NOT be located at the beginning of the stream. If an
    // ID3 version 2 tag was added, then it must be skipped to write the VBR
    // tag data.
    val id3v2TagSize = skipId3v2(stream)

    // Seek to the beginning of the stream
    stream.seek(id3v2TagSize.toLong())

    val buffer = ByteArray(VBRTag.MAXFRAMESIZE)
    val bytes = tag.getLameTagFrame(gfp, buffer)
    if (bytes > buffer.size) {
        return -1
    }

    if (bytes < 1) {
        return 0
    }

    // Put it all to disk again
    stream.write(buffer, 0, bytes)
    // success
    return 0
}


//    @Throws(IOException::class)
private fun skipId3v2(fpStream: RandomAccessFile): Int {
    // seek to the beginning of the stream
    fpStream.seek(0)
    // read 10 bytes in case there's an ID3 version 2 header here
    val id3v2Header = ByteArray(10)
    fpStream.readFully(id3v2Header)
    /* does the stream begin with the ID3 version 2 file identifier? */
    val id3v2TagSize: Int
    if (!makeString(id3v2Header, "ISO-8859-1").startsWith("ID3")) {
        /*
         * the tag size (minus the 10-byte header) is encoded into four
         * bytes where the most significant bit is clear in each byte
         */
        id3v2TagSize = (id3v2Header[6] and 0x7f shl 21
                or (id3v2Header[7] and 0x7f shl 14)
                or (id3v2Header[8] and 0x7f shl 7) or (id3v2Header[9] and 0x7f)) + id3v2Header.size
    } else {
        /* no ID3 version 2 tag in this stream */
        id3v2TagSize = 0
    }
    return id3v2TagSize
}