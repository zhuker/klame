/*
 *      LAME MP3 encoding engine
 *
 *      Copyright (c) 1999 Mark Taylor
 *      Copyright (c) 2000-2002 Takehiro Tominaga
 *      Copyright (c) 2000-2005 Robert Hegemann
 *      Copyright (c) 2001 Gabriel Bouvigne
 *      Copyright (c) 2001 John Dahlstrom
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

/* $Id: Encoder.java,v 1.21 2011/05/24 21:45:14 kenchis Exp $ */
package mp3
import jdk.Math
import jdk.System
import jdk.assert

class Encoder {
    internal lateinit var bs: BitStream
    lateinit var psy: PsyModel
    internal lateinit var vbr: VBRTag
    internal lateinit var qupvt: QuantizePVT

    private val newMDCT = NewMDCT()

    fun setModules(bs: BitStream, psy: PsyModel, qupvt: QuantizePVT,
                   vbr: VBRTag) {
        this.bs = bs
        this.psy = psy
        this.vbr = vbr
        this.qupvt = qupvt
    }

    /**
     * auto-adjust of ATH, useful for low volume Gabriel Bouvigne 3 feb 2001
     *
     * modifies some values in gfp.internal_flags.ATH (gfc.ATH)
     */
    private fun adjust_ATH(gfc: LameInternalFlags) {
        var gr2_max: Float
        var max_pow: Float

        val ATH = gfc.ATH!!
        if (ATH.useAdjust == 0) {
            ATH.adjust = 1.0f
            /* no adjustment */
            return
        }

        /* jd - 2001 mar 12, 27, jun 30 */
        /* loudness based on equal loudness curve; */
        /* use granule with maximum combined loudness */
        max_pow = gfc.loudness_sq[0][0]
        gr2_max = gfc.loudness_sq[1][0]
        if (gfc.channels_out == 2) {
            max_pow += gfc.loudness_sq[0][1]
            gr2_max += gfc.loudness_sq[1][1]
        } else {
            max_pow += max_pow
            gr2_max += gr2_max
        }
        if (gfc.mode_gr == 2) {
            max_pow = Math.max(max_pow, gr2_max)
        }
        max_pow *= 0.5f /* max_pow approaches 1.0 for full band noise */

        /* jd - 2001 mar 31, jun 30 */
        /* user tuning of ATH adjustment region */
        max_pow *= ATH.aaSensitivityP

        /*
		 * adjust ATH depending on range of maximum value
		 */

        /* jd - 2001 feb27, mar12,20, jun30, jul22 */
        /* continuous curves based on approximation */
        /* to GB's original values. */
        /* For an increase in approximate loudness, */
        /* set ATH adjust to adjust_limit immediately */
        /* after a delay of one frame. */
        /* For a loudness decrease, reduce ATH adjust */
        /* towards adjust_limit gradually. */
        /* max_pow is a loudness squared or a power. */
        if (max_pow > 0.03125) { /* ((1 - 0.000625)/ 31.98) from curve below */
            if (ATH.adjust >= 1.0) {
                ATH.adjust = 1.0f
            } else {
                /* preceding frame has lower ATH adjust; */
                /* ascend only to the preceding adjust_limit */
                /* in case there is leading low volume */
                if (ATH.adjust < ATH.adjustLimit) {
                    ATH.adjust = ATH.adjustLimit
                }
            }
            ATH.adjustLimit = 1.0f
        } else { /* adjustment curve */
            /* about 32 dB maximum adjust (0.000625) */
            val adj_lim_new = 31.98f * max_pow + 0.000625f
            if (ATH.adjust >= adj_lim_new) { /* descend gradually */
                ATH.adjust *= (adj_lim_new * 0.075 + 0.925).toFloat()
                if (ATH.adjust < adj_lim_new) { /* stop descent */
                    ATH.adjust = adj_lim_new
                }
            } else { /* ascend */
                if (ATH.adjustLimit >= adj_lim_new) {
                    ATH.adjust = adj_lim_new
                } else {
                    /* preceding frame has lower ATH adjust; */
                    /* ascend only to the preceding adjust_limit */
                    if (ATH.adjust < ATH.adjustLimit) {
                        ATH.adjust = ATH.adjustLimit
                    }
                }
            }
            ATH.adjustLimit = adj_lim_new
        }
    }

    /**
     * <PRE>
     * some simple statistics
     *
     * bitrate index 0: free bitrate . not allowed in VBR mode
     * : bitrates, kbps depending on MPEG version
     * bitrate index 15: forbidden
     *
     * mode_ext:
     * 0:  LR
     * 1:  LR-i
     * 2:  MS
     * 3:  MS-i
    </PRE> *
     */
    private fun updateStats(gfc: LameInternalFlags) {
        var gr: Int
        var ch: Int
        assert(0 <= gfc.bitrate_index && gfc.bitrate_index < 16)
        assert(0 <= gfc.mode_ext && gfc.mode_ext < 4)

        /* count bitrate indices */
        gfc.bitrate_stereoMode_Hist[gfc.bitrate_index][4]++
        gfc.bitrate_stereoMode_Hist[15][4]++

        /* count 'em for every mode extension in case of 2 channel encoding */
        if (gfc.channels_out == 2) {
            gfc.bitrate_stereoMode_Hist[gfc.bitrate_index][gfc.mode_ext]++
            gfc.bitrate_stereoMode_Hist[15][gfc.mode_ext]++
        }
        gr = 0
        while (gr < gfc.mode_gr) {
            ch = 0
            while (ch < gfc.channels_out) {
                var bt = gfc.l3_side.tt[gr][ch].block_type
                if (gfc.l3_side.tt[gr][ch].mixed_block_flag != 0)
                    bt = 4
                gfc.bitrate_blockType_Hist[gfc.bitrate_index][bt]++
                gfc.bitrate_blockType_Hist[gfc.bitrate_index][5]++
                gfc.bitrate_blockType_Hist[15][bt]++
                gfc.bitrate_blockType_Hist[15][5]++
                ++ch
            }
            ++gr
        }
    }

    private fun lame_encode_frame_init(gfp: LameGlobalFlags,
                                       inbuf: Array<FloatArray>) {
        val gfc = gfp.internal_flags

        var ch: Int
        var gr: Int

        if (gfc!!.lame_encode_frame_init == 0) {
            /* prime the MDCT/polyphase filterbank with a short block */
            var i: Int
            var j: Int
            val primebuff0 = FloatArray(286 + 1152 + 576)
            val primebuff1 = FloatArray(286 + 1152 + 576)
            gfc.lame_encode_frame_init = 1
            i = 0
            j = 0
            while (i < 286 + 576 * (1 + gfc.mode_gr)) {
                if (i < 576 * gfc.mode_gr) {
                    primebuff0[i] = 0f
                    if (gfc.channels_out == 2)
                        primebuff1[i] = 0f
                } else {
                    primebuff0[i] = inbuf[0][j]
                    if (gfc.channels_out == 2)
                        primebuff1[i] = inbuf[1][j]
                    ++j
                }
                ++i
            }
            /* polyphase filtering / mdct */
            gr = 0
            while (gr < gfc.mode_gr) {
                ch = 0
                while (ch < gfc.channels_out) {
                    gfc.l3_side.tt[gr][ch].block_type = SHORT_TYPE
                    ch++
                }
                gr++
            }
            newMDCT.mdct_sub48(gfc, primebuff0, primebuff1)

            /* check FFT will not use a negative starting offset */
            assert(576 >= FFTOFFSET)
            /* check if we have enough data for FFT */
            assert(gfc.mf_size >= BLKSIZE + gfp.framesize - FFTOFFSET)
            /* check if we have enough data for polyphase filterbank */
            assert(gfc.mf_size >= 512 + gfp.framesize - 32)
        }

    }

    fun lame_encode_mp3_frame(gfp: LameGlobalFlags,
                              inbuf_l: FloatArray, inbuf_r: FloatArray, mp3buf: ByteArray,
                              mp3bufPos: Int, mp3buf_size: Int): Int {
        val mp3count: Int
        val masking_LR = Array<Array<III_psy_ratio>>(2) { Array(2) { III_psy_ratio() } } /*
																 * LR masking &
																 * energy
																 */
        val masking_MS = Array<Array<III_psy_ratio>>(2) { Array(2) { III_psy_ratio() } }
        /* MS masking & energy */
        val masking: Array<Array<III_psy_ratio>>
        /* pointer to selected maskings */
        val gfc = gfp.internal_flags

        val tot_ener = Array(2) { FloatArray(4) }
        val ms_ener_ratio = floatArrayOf(.5f, .5f)
        val pe = arrayOf(floatArrayOf(0f, 0f), floatArrayOf(0f, 0f))
        val pe_MS = arrayOf(floatArrayOf(0f, 0f), floatArrayOf(0f, 0f))
        val pe_use: Array<FloatArray>

        var ch: Int
        var gr: Int

        val inbuf = arrayOf(inbuf_l, inbuf_r)

        if (gfc!!.lame_encode_frame_init == 0) {
            /* first run? */
            lame_encode_frame_init(gfp, inbuf)

        }

        /********************** padding  */
        /**
         * <PRE>
         * padding method as described in
         * "MPEG-Layer3 / Bitstream Syntax and Decoding"
         * by Martin Sieler, Ralph Sperschneider
         *
         * note: there is no padding for the very first frame
         *
         * Robert Hegemann 2000-06-22
        </PRE> *
         */
        gfc.padding = 0
        gfc.slot_lag -= gfc.frac_SpF
        if (gfc.slot_lag < 0) {
            gfc.slot_lag += gfp.out_samplerate
            gfc.padding = 1
        }

        /****************************************
         * Stage 1: psychoacoustic model *
         */

        if (gfc.psymodel != 0) {
            /*
			 * psychoacoustic model psy model has a 1 granule (576) delay that
			 * we must compensate for (mt 6/99).
			 */
            var ret: Int
            val bufp = arrayOf(FloatArray(0), FloatArray(0))
            /* address of beginning of left & right granule */
            var bufpPos = 0
            /* address of beginning of left & right granule */
            val blocktype = IntArray(2)

            gr = 0
            while (gr < gfc.mode_gr) {

                ch = 0
                while (ch < gfc.channels_out) {
                    bufp[ch] = inbuf[ch]
                    bufpPos = 576 + gr * 576 - FFTOFFSET
                    ch++
                }
                if (gfp.VBR == VbrMode.vbr_mtrh || gfp.VBR == VbrMode.vbr_mt) {
                    ret = psy.L3psycho_anal_vbr(gfp, bufp, bufpPos, gr,
                            masking_LR, masking_MS, pe[gr], pe_MS[gr],
                            tot_ener[gr], blocktype)
                } else {
                    ret = psy.L3psycho_anal_ns(gfp, bufp, bufpPos, gr,
                            masking_LR, masking_MS, pe[gr], pe_MS[gr],
                            tot_ener[gr], blocktype)
                }
                if (ret != 0)
                    return -4

                if (gfp.mode == MPEGMode.JOINT_STEREO) {
                    ms_ener_ratio[gr] = tot_ener[gr][2] + tot_ener[gr][3]
                    if (ms_ener_ratio[gr] > 0)
                        ms_ener_ratio[gr] = tot_ener[gr][3] / ms_ener_ratio[gr]
                }

                /* block type flags */
                ch = 0
                while (ch < gfc.channels_out) {
                    val cod_info = gfc.l3_side.tt[gr][ch]
                    cod_info.block_type = blocktype[ch]
                    cod_info.mixed_block_flag = 0
                    ch++
                }
                gr++
            }
        } else {
            /* no psy model */
            gr = 0
            while (gr < gfc.mode_gr) {
//                {
                    ch = 0
                    while (ch < gfc.channels_out) {
                        gfc.l3_side.tt[gr][ch].block_type = NORM_TYPE
                        gfc.l3_side.tt[gr][ch].mixed_block_flag = 0
                        pe[gr][ch] = 700f
                        pe_MS[gr][ch] = pe[gr][ch]
                        ch++
                    }
//                }
                gr++
            }
        }

        /* auto-adjust of ATH, useful for low volume */
        adjust_ATH(gfc)

        /****************************************
         * Stage 2: MDCT *
         */

        /* polyphase filtering / mdct */
        newMDCT.mdct_sub48(gfc, inbuf[0], inbuf[1])

        /****************************************
         * Stage 3: MS/LR decision *
         */

        /* Here will be selected MS or LR coding of the 2 stereo channels */
        gfc.mode_ext = MPG_MD_LR_LR

        if (gfp.force_ms) {
            gfc.mode_ext = MPG_MD_MS_LR
        } else if (gfp.mode == MPEGMode.JOINT_STEREO) {
            /*
			 * ms_ratio = is scaled, for historical reasons, to look like a
			 * ratio of side_channel / total. 0 = signal is 100% mono .5 = L & R
			 * uncorrelated
			 */

            /**
             * <PRE>
             * [0] and [1] are the results for the two granules in MPEG-1,
             * in MPEG-2 it's only a faked averaging of the same value
             * _prev is the value of the last granule of the previous frame
             * _next is the value of the first granule of the next frame
            </PRE> *
             */

            var sum_pe_MS = 0f
            var sum_pe_LR = 0f
            gr = 0
            while (gr < gfc.mode_gr) {
                ch = 0
                while (ch < gfc.channels_out) {
                    sum_pe_MS += pe_MS[gr][ch]
                    sum_pe_LR += pe[gr][ch]
                    ch++
                }
                gr++
            }

            /* based on PE: M/S coding would not use much more bits than L/R */
            if (sum_pe_MS <= 1.00 * sum_pe_LR) {

                val gi0 = gfc.l3_side.tt[0]
                val gi1 = gfc.l3_side.tt[gfc.mode_gr - 1]

                if (gi0[0].block_type == gi0[1].block_type && gi1[0].block_type == gi1[1].block_type) {

                    gfc.mode_ext = MPG_MD_MS_LR
                }
            }
        }

        /* bit and noise allocation */
        if (gfc.mode_ext == MPG_MD_MS_LR) {
            masking = masking_MS /* use MS masking */
            pe_use = pe_MS
        } else {
            masking = masking_LR /* use LR masking */
            pe_use = pe
        }

        /* copy data for MP3 frame analyzer */
        val pinfo = gfc.pinfo
        if (gfp.analysis && pinfo != null) {
            gr = 0
            while (gr < gfc.mode_gr) {
                ch = 0
                while (ch < gfc.channels_out) {
                    pinfo.ms_ratio[gr] = gfc.ms_ratio[gr].toDouble()
                    pinfo.ms_ener_ratio[gr] = ms_ener_ratio[gr].toDouble()
                    pinfo.blocktype[gr][ch] = gfc.l3_side.tt[gr][ch].block_type
                    pinfo.pe[gr][ch] = pe_use[gr][ch].toDouble()
                    System.arraycopy(gfc.l3_side.tt[gr][ch].xr, 0,
                            pinfo.xr[gr][ch], 0, 576)
                    /*
					 * in psymodel, LR and MS data was stored in pinfo. switch
					 * to MS data:
					 */
                    if (gfc.mode_ext == MPG_MD_MS_LR) {
                        pinfo.ers[gr][ch] = pinfo.ers[gr][ch + 2]
                        System.arraycopy(pinfo.energy[gr][ch + 2], 0,
                                pinfo.energy[gr][ch], 0,
                                pinfo.energy[gr][ch].size)
                    }
                    ch++
                }
                gr++
            }
        }

        /****************************************
         * Stage 4: quantization loop *
         */

        if (gfp.VBR == VbrMode.vbr_off || gfp.VBR == VbrMode.vbr_abr) {

            var i: Int
            var f: Float

            i = 0
            while (i < 18) {
                gfc.nsPsy.pefirbuf[i] = gfc.nsPsy.pefirbuf[i + 1]
                i++
            }

            f = 0.0f
            gr = 0
            while (gr < gfc.mode_gr) {
//                {
                    ch = 0
                    while (ch < gfc.channels_out) {
                        f += pe_use[gr][ch]
                        ch++
                    }
//                }
                gr++
            }
            gfc.nsPsy.pefirbuf[18] = f

            f = gfc.nsPsy.pefirbuf[9]
            i = 0
            while (i < 9) {
                f += (gfc.nsPsy.pefirbuf[i] + gfc.nsPsy.pefirbuf[18 - i]) * fircoef[i]
                i++
            }

            f = 670 * 5 * gfc.mode_gr * gfc.channels_out / f
            gr = 0
            while (gr < gfc.mode_gr) {
                ch = 0
                while (ch < gfc.channels_out) {
                    pe_use[gr][ch] *= f
                    ch++
                }
                gr++
            }
        }
        gfc.iteration_loop!!.iteration_loop(gfp, pe_use, ms_ener_ratio, masking)

        /****************************************
         * Stage 5: bitstream formatting *
         */

        /* write the frame to the bitstream */
        bs.format_bitstream(gfp)

        /* copy mp3 bit buffer into array */
        mp3count = bs.copy_buffer(gfc, mp3buf, mp3bufPos, mp3buf_size, 1)

        if (gfp.bWriteVbrTag)
            vbr.addVbrFrame(gfp)

        if (gfp.analysis && pinfo != null) {
            ch = 0
            while (ch < gfc.channels_out) {
                var j: Int
                j = 0
                while (j < FFTOFFSET) {
                    pinfo.pcmdata[ch][j] = pinfo.pcmdata[ch][j + gfp.framesize]
                    j++
                }
                j = FFTOFFSET
                while (j < 1600) {
                    pinfo.pcmdata[ch][j] = inbuf[ch][j - FFTOFFSET].toDouble()
                    j++
                }
                ch++
            }
            qupvt.set_frame_pinfo(gfp, masking)
        }

        updateStats(gfc)

        return mp3count
    }

    companion object {

        /***********************************************************************
         *
         * encoder and decoder delays
         *
         */

        /**
         * <PRE>
         * layer III enc->dec delay:  1056 (1057?)   (observed)
         * layer  II enc->dec delay:   480  (481?)   (observed)
         *
         * polyphase 256-16             (dec or enc)        = 240
         * mdct      256+32  (9*32)     (dec or enc)        = 288
         * total:    512+16
         *
         * My guess is that delay of polyphase filterbank is actualy 240.5
         * (there are technical reasons for this, see postings in mp3encoder).
         * So total Encode+Decode delay = ENCDELAY + 528 + 1
        </PRE> *
         */

        /**
         * ENCDELAY The encoder delay.
         *
         * Minimum allowed is MDCTDELAY (see below)
         *
         * The first 96 samples will be attenuated, so using a value less than 96
         * will result in corrupt data for the first 96-ENCDELAY samples.
         *
         * suggested: 576 set to 1160 to sync with FhG.
         */
        const val ENCDELAY = 576

        /**
         * make sure there is at least one complete frame after the last frame
         * containing real data
         *
         * Using a value of 288 would be sufficient for a a very sophisticated
         * decoder that can decode granule-by-granule instead of frame by frame. But
         * lets not assume this, and assume the decoder will not decode frame N
         * unless it also has data for frame N+1
         */
        const val POSTDELAY = 1152

        /**
         * delay of the MDCT used in mdct.c original ISO routines had a delay of
         * 528! Takehiro's routines:
         */
        const val MDCTDELAY = 48
        const val FFTOFFSET = 224 + MDCTDELAY

        /**
         * Most decoders, including the one we use, have a delay of 528 samples.
         */
        const val DECDELAY = 528

        /**
         * number of subbands
         */
        const val SBLIMIT = 32

        /**
         * parition bands bands
         */
        const val CBANDS = 64

        /**
         * number of critical bands/scale factor bands where masking is computed
         */
        const val SBPSY_l = 21
        const val SBPSY_s = 12

        /**
         * total number of scalefactor bands encoded
         */
        const val SBMAX_l = 22
        const val SBMAX_s = 13
        const val PSFB21 = 6
        const val PSFB12 = 6

        /**
         * FFT sizes
         */
        const val BLKSIZE = 1024
        const val HBLKSIZE = BLKSIZE / 2 + 1
        const val BLKSIZE_s = 256
        const val HBLKSIZE_s = BLKSIZE_s / 2 + 1

        const val NORM_TYPE = 0
        const val START_TYPE = 1
        const val SHORT_TYPE = 2
        const val STOP_TYPE = 3

        /**
         * <PRE>
         * Mode Extention:
         * When we are in stereo mode, there are 4 possible methods to store these
         * two channels. The stereo modes -m? are using a subset of them.
         *
         * -ms: MPG_MD_LR_LR
         * -mj: MPG_MD_LR_LR and MPG_MD_MS_LR
         * -mf: MPG_MD_MS_LR
         * -mi: all
        </PRE> *
         */
        const val MPG_MD_LR_LR = 0
        const val MPG_MD_LR_I = 1
        const val MPG_MD_MS_LR = 2
        const val MPG_MD_MS_I = 3

        /**
         * <PRE>
         * encodeframe()           Layer 3
         *
         * encode a single frame
         *
         *
         * lame_encode_frame()
         *
         *
         * gr 0            gr 1
         * inbuf:           |--------------|--------------|--------------|
         *
         *
         * Polyphase (18 windows, each shifted 32)
         * gr 0:
         * window1          <----512---.
         * window18                 <----512---.
         *
         * gr 1:
         * window1                         <----512---.
         * window18                                <----512---.
         *
         *
         *
         * MDCT output:  |--------------|--------------|--------------|
         *
         * FFT's                    <---------1024---------.
         * <---------1024-------.
         *
         *
         *
         * inbuf = buffer of PCM data size=MP3 framesize
         * encoder acts on inbuf[ch][0], but output is delayed by MDCTDELAY
         * so the MDCT coefficints are from inbuf[ch][-MDCTDELAY]
         *
         * psy-model FFT has a 1 granule delay, so we feed it data for the
         * next granule.
         * FFT is centered over granule:  224+576+224
         * So FFT starts at:   576-224-MDCTDELAY
         *
         * MPEG2:  FFT ends at:  BLKSIZE+576-224-MDCTDELAY      (1328)
         * MPEG1:  FFT ends at:  BLKSIZE+2*576-224-MDCTDELAY    (1904)
         *
         * MPEG2:  polyphase first window:  [0..511]
         * 18th window:   [544..1055]          (1056)
         * MPEG1:            36th window:   [1120..1631]         (1632)
         * data needed:  512+framesize-32
         *
         * A close look newmdct.c shows that the polyphase filterbank
         * only uses data from [0..510] for each window.  Perhaps because the window
         * used by the filterbank is zero for the last point, so Takehiro's
         * code doesn't bother to compute with it.
         *
         * FFT starts at 576-224-MDCTDELAY (304)  = 576-FFTOFFSET
         *
        </PRE> *
         */

        private val fircoef = floatArrayOf(-0.0207887f * 5, -0.0378413f * 5, -0.0432472f * 5, -0.031183f * 5, 7.79609e-18f * 5, 0.0467745f * 5, 0.10091f * 5, 0.151365f * 5, 0.187098f * 5)
    }

}
