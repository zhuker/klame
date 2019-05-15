package mp3

class MP3Data {
    /**
     * true if header was parsed and following data was computed
     */
    var header_parsed: Boolean = false
    /**
     * number of channels
     */
    var stereo: Int = 0
    /**
     * sample rate
     */
    var samplerate: Int = 0
    /**
     * bitrate
     */
    var bitrate: Int = 0
    /**
     * mp3 frame type
     */
    var mode: Int = 0
    /**
     * mp3 frame type
     */
    var mode_ext: Int = 0
    /**
     * number of samples per mp3 frame
     */
    var framesize: Int = 0

    /* this data is only computed if mpglib detects a Xing VBR header */

    /**
     * number of samples in mp3 file.
     */
    var nsamp: Int = 0
    /**
     * total number of frames in mp3 file
     */
    var totalframes: Int = 0

    /* this data is not currently computed by the mpglib routines */

    /**
     * frames decoded counter
     */
    var framenum: Int = 0
}