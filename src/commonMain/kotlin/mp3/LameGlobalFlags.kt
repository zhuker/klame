package mp3

/**
 * Control Parameters set by User. These parameters are here for backwards
 * compatibility with the old, non-shared lib API. Please use the
 * lame_set_variablename() functions below
 *
 * @author Ken
 */
class LameGlobalFlags {

    var class_id: Long = 0

    /* input description */

    /**
     * number of samples. default=-1
     */
    var num_samples: Int = 0
    /**
     * input number of channels. default=2
     */
    var num_channels: Int = 0
    /**
     * input_samp_rate in Hz. default=44.1 kHz
     */
    var in_samplerate: Int = 0
    /**
     * output_samp_rate. default: LAME picks best value at least not used for
     * MP3 decoding: Remember 44.1 kHz MP3s and AC97
     */
    var out_samplerate: Int = 0
    /**
     * scale input by this amount before encoding at least not used for MP3
     * decoding
     */
    var scale: Float = 0.toFloat()
    /**
     * scale input of channel 0 (left) by this amount before encoding
     */
    var scale_left: Float = 0.toFloat()
    /**
     * scale input of channel 1 (right) by this amount before encoding
     */
    var scale_right: Float = 0.toFloat()

    /* general control params */
    /**
     * collect data for a MP3 frame analyzer?
     */
    var analysis: Boolean = false
    /**
     * add Xing VBR tag?
     */
    var bWriteVbrTag: Boolean = false
    /**
     * use lame/mpglib to convert mp3 to wav
     */
    var decode_only: Boolean = false
    /**
     * quality setting 0=best, 9=worst default=5
     */
    var quality: Int = 0
    /**
     * see enum default = LAME picks best value
     */
    var mode = MPEGMode.STEREO
    /**
     * force M/S mode. requires mode=1
     */
    var force_ms: Boolean = false
    /**
     * use free format? default=0
     */
    var free_format: Boolean = false
    /**
     * find the RG value? default=0
     */
    var findReplayGain: Boolean = false
    /**
     * decode on the fly? default=0
     */
    var decode_on_the_fly: Boolean = false
    /**
     * 1 (default) writes ID3 tags, 0 not
     */
    var write_id3tag_automatic: Boolean = false

    /*
	 * set either brate>0 or compression_ratio>0, LAME will compute the value of
	 * the variable not set. Default is compression_ratio = 11.025
	 */
    /**
     * bitrate
     */
    var brate: Int = 0
    /**
     * sizeof(wav file)/sizeof(mp3 file)
     */
    var compression_ratio: Float = 0.toFloat()

    /* frame params */
    /**
     * mark as copyright. default=0
     */
    var copyright: Int = 0
    /**
     * mark as original. default=1
     */
    var original: Int = 0
    /**
     * the MP3 'private extension' bit. Meaningless
     */
    var extension: Int = 0
    /**
     * Input PCM is emphased PCM (for instance from one of the rarely emphased
     * CDs), it is STRONGLY not recommended to use this, because psycho does not
     * take it into account, and last but not least many decoders don't care
     * about these bits
     */
    var emphasis: Int = 0
    /**
     * use 2 bytes per frame for a CRC checksum. default=0
     */
    var error_protection: Boolean = false
    /**
     * enforce ISO spec as much as possible
     */
    var strict_ISO: Boolean = false

    /**
     * use bit reservoir?
     */
    var disable_reservoir: Boolean = false

    /* quantization/noise shaping */
    var quant_comp: Int = 0
    var quant_comp_short: Int = 0
    var experimentalY: Boolean = false
    var experimentalZ: Int = 0
    var exp_nspsytune: Int = 0

    var preset: Int = 0

    /* VBR control */
    var VBR: VbrMode? = null
    /**
     * Range [0,...,1[
     */
    var VBR_q_frac: Float = 0.toFloat()
    /**
     * Range [0,...,9]
     */
    var VBR_q: Int = 0
    var VBR_mean_bitrate_kbps: Int = 0
    var VBR_min_bitrate_kbps: Int = 0
    var VBR_max_bitrate_kbps: Int = 0
    /**
     * strictly enforce VBR_min_bitrate normaly, it will be violated for analog
     * silence
     */
    var VBR_hard_min: Int = 0

    /* resampling and filtering */

    /**
     * freq in Hz. 0=lame choses. -1=no filter
     */
    var lowpassfreq: Int = 0
    /**
     * freq in Hz. 0=lame choses. -1=no filter
     */
    var highpassfreq: Int = 0
    /**
     * freq width of filter, in Hz (default=15%)
     */
    var lowpasswidth: Int = 0
    /**
     * freq width of filter, in Hz (default=15%)
     */
    var highpasswidth: Int = 0

    /*
	 * psycho acoustics and other arguments which you should not change unless
	 * you know what you are doing
	 */

    var maskingadjust: Float = 0.toFloat()
    var maskingadjust_short: Float = 0.toFloat()
    /**
     * only use ATH
     */
    var ATHonly: Boolean = false
    /**
     * only use ATH for short blocks
     */
    var ATHshort: Boolean = false
    /**
     * disable ATH
     */
    var noATH: Boolean = false
    /**
     * select ATH formula
     */
    var ATHtype: Int = 0
    /**
     * change ATH formula 4 shape
     */
    var ATHcurve: Float = 0.toFloat()
    /**
     * lower ATH by this many db
     */
    var ATHlower: Float = 0.toFloat()
    /**
     * select ATH auto-adjust scheme
     */
    var athaa_type: Int = 0
    /**
     * select ATH auto-adjust loudness calc
     */
    var athaa_loudapprox: Int = 0
    /**
     * dB, tune active region of auto-level
     */
    var athaa_sensitivity: Float = 0.toFloat()
    var short_blocks: ShortBlock? = null
    /**
     * use temporal masking effect
     */
    var useTemporal: Boolean? = null
    var interChRatio: Float = 0.toFloat()
    /**
     * Naoki's adjustment of Mid/Side maskings
     */
    var msfix: Float = 0.toFloat()

    /**
     * 0 off, 1 on
     */
    var tune: Boolean = false
    /**
     * used to pass values for debugging and stuff
     */
    var tune_value_a: Float = 0.toFloat()

    /** */
    /* internal variables, do not set... */
    /* provided because they may be of use to calling application */
    /** */

    /**
     * 0=MPEG-2/2.5 1=MPEG-1
     */
    var version: Int = 0
    var encoder_delay: Int = 0
    /**
     * number of samples of padding appended to input
     */
    var encoder_padding: Int = 0
    var framesize: Int = 0
    /**
     * number of frames encoded
     */
    var frameNum: Int = 0
    /**
     * is this struct owned by calling program or lame?
     */
    var lame_allocated_gfp: Int = 0
    /** */
    /* more internal variables are stored in this structure: */
    /** */
    var internal_flags: LameInternalFlags? = null

}
