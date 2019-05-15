package mp3

/* MPEG modes */
enum class MPEGMode {
    STEREO, JOINT_STEREO,
    /**
     * LAME doesn't supports this!
     */
    DUAL_CHANNEL,
    MONO, NOT_SET
}
