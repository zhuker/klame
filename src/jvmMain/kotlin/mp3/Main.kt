/*
 *      Command line frontend program
 *
 *      Copyright (c) 1999 Mark Taylor
 *                    2000 Takehiro TOMINAGA
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

/* $Id: Main.java,v 1.38 2011/08/27 18:57:12 kenchis Exp $ */

package mp3

import jdk.and
import java.beans.PropertyChangeSupport
import java.io.Closeable
import java.io.DataOutput
import java.io.IOException
import java.io.RandomAccessFile
import java.util.ArrayList
import java.util.Locale
import java.util.StringTokenizer

import mp3.GetAudio.sound_file_format
import mpg.Common
import mpg.Interface
import mpg.MPGLib

class Main {

    private var gaud: GetAudio? = null
    private var id3: ID3Tag? = null
    private var lame: Lame? = null
    private var ga: GainAnalysis? = null
    private var bs: BitStream? = null
    private var p: Presets? = null
    private var qupvt: QuantizePVT? = null
    private var qu: Quantize? = null
    private var vbr: VBRTag? = null
    private var ver: Version? = null
    private var rv: Reservoir? = null
    private var tak: Takehiro? = null
    private var parse: Parse? = null
    private var hist: BRHist? = null

    private var mpg: MPGLib? = null
    private var intf: Interface? = null
    private var common: Common? = null

    val support = PropertyChangeSupport(this)

    private var last_time = 0.0

    private var oldPercent: Int = 0
    private var curPercent: Int = 0
    private var oldConsoleX: Int = 0

    /**
     * PURPOSE: MPEG-1,2 Layer III encoder with GPSYCHO psychoacoustic model.
     */
    private fun parse_args_from_string(gfp: LameGlobalFlags?,
                                       argv: String?, inPath: StringBuilder,
                                       outPath: StringBuilder): Int {
        /* Quick & very Dirty */
        if (argv == null || argv.length == 0)
            return 0

        val tok = StringTokenizer(argv, " ")
        val args = ArrayList<String>()
        while (tok.hasMoreTokens()) {
            args.add(tok.nextToken())
        }
        return parse!!.parse_args(gfp!!, args, inPath, outPath, emptyArray(), null)
    }

    fun init_files(gf: LameGlobalFlags,
                   inPath: String, outPath: String, enc: Enc): DataOutput? {
        /*
		 * Mostly it is not useful to use the same input and output name. This
		 * test is very easy and buggy and don't recognize different names
		 * assigning the same file
		 */
        if (inPath == outPath) {
            System.err
                    .println("Input file and Output file are the same. Abort.")
            return null
        }

        /*
		 * open the wav/aiff/raw pcm or mp3 input file. This call will open the
		 * file, try to parse the headers and set gf.samplerate,
		 * gf.num_channels, gf.num_samples. if you want to do your own file
		 * input, skip this call and set samplerate, num_channels and
		 * num_samples yourself.
		 */
        gaud!!.init_infile(gf, inPath, enc)

        val outf: DataOutput?
        outf = gaud!!.init_outfile(outPath)
        if (outf == null) {
            System.err.printf("Can't init outfile '%s'\n", outPath)
            return null
        }

        return outf
    }

    /**
     * the simple lame decoder
     *
     * After calling lame_init(), lame_init_params() and init_infile(), call
     * this routine to read the input MP3 file and output .wav data to the
     * specified file pointer
     *
     * lame_decoder will ignore the first 528 samples, since these samples
     * represent the mpglib delay (and are all 0). skip = number of additional
     * samples to skip, to (for example) compensate for the encoder delay
     */
    @Throws(IOException::class)
    private fun lame_decoder(gfp: LameGlobalFlags,
                             outf: DataOutput, skip_start: Int, inPath: String,
                             outPath: String, enc: Enc) {
        var skip_start = skip_start
        val Buffer = Array(2) { ShortArray(1152) }
        var iread: Int
        var skip_end = 0
        var i: Int
        val tmp_num_channels = gfp.num_channels

        if (parse!!.silent < 10)
            System.out.printf("\rinput:  %s%s(%g kHz, %d channel%s, ", inPath,
                    if (inPath.length > 26) "\n\t" else "  ",
                    gfp.in_samplerate / 1e3, tmp_num_channels,
                    if (tmp_num_channels != 1) "s" else "")

        when (parse!!.input_format) {
            sound_file_format.sf_mp123 /* FIXME: !!! */ -> throw RuntimeException("Internal error.  Aborting.")

            sound_file_format.sf_mp3 -> {
                if (skip_start == 0) {
                    if (enc.enc_delay > -1 || enc.enc_padding > -1) {
                        if (enc.enc_delay > -1)
                            skip_start = enc.enc_delay + 528 + 1
                        if (enc.enc_padding > -1)
                            skip_end = enc.enc_padding - (528 + 1)
                    } else
                        skip_start = gfp.encoder_delay + 528 + 1
                } else {
                    /* user specified a value of skip. just add for decoder */
                    skip_start += 528 + 1
                    /*
				 * mp3 decoder has a 528 sample delay, plus user supplied "skip"
				 */
                }

                if (parse!!.silent < 10)
                    System.out.printf("MPEG-%d%s Layer %s", 2 - gfp.version,
                            if (gfp.out_samplerate < 16000) ".5" else "", "III")
            }
            sound_file_format.sf_mp2 -> {
                skip_start += 240 + 1
                if (parse!!.silent < 10)
                    System.out.printf("MPEG-%d%s Layer %s", 2 - gfp.version,
                            if (gfp.out_samplerate < 16000) ".5" else "", "II")
            }
            sound_file_format.sf_mp1 -> {
                skip_start += 240 + 1
                if (parse!!.silent < 10)
                    System.out.printf("MPEG-%d%s Layer %s", 2 - gfp.version,
                            if (gfp.out_samplerate < 16000) ".5" else "", "I")
            }
            sound_file_format.sf_raw -> {
                if (parse!!.silent < 10)
                    System.out.printf("raw PCM data")
                parse!!.mp3input_data.nsamp = gfp.num_samples
                parse!!.mp3input_data.framesize = 1152
                skip_start = 0
            }
            sound_file_format.sf_wave -> {
                if (parse!!.silent < 10)
                    System.out.printf("Microsoft WAVE")
                parse!!.mp3input_data.nsamp = gfp.num_samples
                parse!!.mp3input_data.framesize = 1152
                skip_start = 0
            }
            sound_file_format.sf_aiff -> {
                if (parse!!.silent < 10)
                    System.out.printf("SGI/Apple AIFF")
                parse!!.mp3input_data.nsamp = gfp.num_samples
                parse!!.mp3input_data.framesize = 1152
                skip_start = 0
            }
            else -> {
                if (parse!!.silent < 10)
                    System.out.printf("unknown")
                parse!!.mp3input_data.nsamp = gfp.num_samples
                parse!!.mp3input_data.framesize = 1152
                skip_start = 0
                /* other formats have no delay */
                assert(false)
            }
        }/* other formats have no delay *//* other formats have no delay *//* other formats have no delay */

        if (parse!!.silent < 10) {
            System.out.printf(")\noutput: %s%s(16 bit, Microsoft WAVE)\n",
                    outPath, if (outPath.length > 45) "\n\t" else "  ")

            if (skip_start > 0)
                System.out
                        .printf("skipping initial %d samples (encoder+decoder delay)\n",
                                skip_start)
            if (skip_end > 0)
                System.out
                        .printf("skipping final %d samples (encoder padding-decoder delay)\n",
                                skip_end)
        }

        print("|")
        for (j in 0 until MAX_WIDTH - 2) {
            print("=")
        }
        println("|")
        oldConsoleX = 0
        curPercent = oldConsoleX
        oldPercent = curPercent

        if (!parse!!.disable_wav_header)
            gaud!!.WriteWaveHeader(outf, Integer.MAX_VALUE, gfp.in_samplerate,
                    tmp_num_channels, 16)
        /* unknown size, so write maximum 32 bit signed value */

        var wavsize = (-(skip_start + skip_end)).toLong()
        parse!!.mp3input_data.totalframes = parse!!.mp3input_data.nsamp / parse!!.mp3input_data.framesize

        assert(tmp_num_channels >= 1 && tmp_num_channels <= 2)

        do {
            iread = gaud!!.get_audio16(gfp, Buffer)
            /* read in 'iread' samples */
            if (iread >= 0) {
                parse!!.mp3input_data.framenum += iread / parse!!.mp3input_data.framesize
                wavsize += iread.toLong()

                if (parse!!.silent <= 0) {
                    timestatus(parse!!.mp3input_data.framenum,
                            parse!!.mp3input_data.totalframes)
                }

                i = if (skip_start < iread) skip_start else iread
                skip_start -= i
                /*
				 * 'i' samples are to skip in this frame
				 */

                if (skip_end > 1152 && parse!!.mp3input_data.framenum + 2 > parse!!.mp3input_data.totalframes) {
                    iread -= skip_end - 1152
                    skip_end = 1152
                } else if (parse!!.mp3input_data.framenum == parse!!.mp3input_data.totalframes && iread != 0)
                    iread -= skip_end

                while (i < iread) {
                    if (parse!!.disable_wav_header) {
                        if (parse!!.swapbytes) {
                            WriteBytesSwapped(outf, Buffer[0], i)
                        } else {
                            WriteBytes(outf, Buffer[0], i)
                        }
                        if (tmp_num_channels == 2) {
                            if (parse!!.swapbytes) {
                                WriteBytesSwapped(outf, Buffer[1], i)
                            } else {
                                WriteBytes(outf, Buffer[1], i)
                            }
                        }
                    } else {
                        gaud!!.write16BitsLowHigh(outf, Buffer[0][i] and 0xffff)
                        if (tmp_num_channels == 2)
                            gaud!!.write16BitsLowHigh(outf, Buffer[1][i] and 0xffff)
                    }
                    i++
                }
            }
        } while (iread > 0)

        i = 16 / 8 * tmp_num_channels
        assert(i > 0)
        if (wavsize <= 0) {
            if (parse!!.silent < 10)
                System.err.println("WAVE file contains 0 PCM samples")
            wavsize = 0
        } else if (wavsize > 0xFFFFFFD0L / i) {
            if (parse!!.silent < 10)
                System.err
                        .println("Very huge WAVE file, can't set filesize accordingly")
            wavsize = -0x30
        } else {
            wavsize *= i.toLong()
        }

        (outf as Closeable).close()
        /* if outf is seekable, rewind and adjust length */
        if (!parse!!.disable_wav_header) {
            val rf = RandomAccessFile(outPath, "rw")
            gaud!!.WriteWaveHeader(rf, wavsize.toInt(), gfp.in_samplerate,
                    tmp_num_channels, 16)
            rf.close()
        }

        print("|")
        for (j in 0 until MAX_WIDTH - 2) {
            print("=")
        }
        println("|")
    }

    private fun print_lame_tag_leading_info(gf: LameGlobalFlags) {
        if (gf.bWriteVbrTag)
            println("Writing LAME Tag...")
    }

    private fun print_trailing_info(gf: LameGlobalFlags) {
        if (gf.bWriteVbrTag)
            println("done\n")

        if (gf.findReplayGain) {
            val RadioGain = gf.internal_flags!!.RadioGain
            System.out.printf("ReplayGain: %s%.1fdB\n", if (RadioGain > 0)
                "+"
            else
                "", RadioGain / 10.0f)
            if (RadioGain > 0x1FE || RadioGain < -0x1FE)
                println("WARNING: ReplayGain exceeds the -51dB to +51dB range. Such a result is too\n" + "         high to be stored in the header.")
        }

        /*
		 * if (the user requested printing info about clipping) and (decoding on
		 * the fly has actually been performed)
		 */
        if (parse!!.print_clipping_info && gf.decode_on_the_fly) {
            val noclipGainChange = gf.internal_flags!!.noclipGainChange.toFloat() / 10.0f
            val noclipScale = gf.internal_flags!!.noclipScale

            if (noclipGainChange > 0.0) {
                /* clipping occurs */
                System.out
                        .printf("WARNING: clipping occurs at the current gain. Set your decoder to decrease\n" + "         the  gain  by  at least %.1fdB or encode again ",
                                noclipGainChange)

                /* advice the user on the scale factor */
                if (noclipScale > 0) {
                    System.out.printf(Locale.US, "using  --scale %.2f\n", noclipScale)
                    print("         or less (the value under --scale is approximate).\n")
                } else {
                    /*
					 * the user specified his own scale factor. We could suggest
					 * the scale factor of
					 * (32767.0/gfp->PeakSample)*(gfp->scale) but it's usually
					 * very inaccurate. So we'd rather advice him to disable
					 * scaling first and see our suggestion on the scale factor
					 * then.
					 */
                    print("using --scale <arg>\n"
                            + "         (For   a   suggestion  on  the  optimal  value  of  <arg>  encode\n"
                            + "         with  --scale 1  first)\n")
                }

            } else { /* no clipping */
                if (noclipGainChange > -0.1)
                    print("\nThe waveform does not clip and is less than 0.1dB away from full scale.\n")
                else
                    System.out
                            .printf("\nThe waveform does not clip and is at least %.1fdB away from full scale.\n",
                                    -noclipGainChange)
            }
        }

    }

    private fun write_xing_frame(gf: LameGlobalFlags,
                                 outf: RandomAccessFile): Int {
        val mp3buffer = ByteArray(Lame.LAME_MAXMP3BUFFER)

        val imp3 = vbr!!.getLameTagFrame(gf, mp3buffer)
        if (imp3 > mp3buffer.size) {
            System.err
                    .printf("Error writing LAME-tag frame: buffer too small: buffer size=%d  frame size=%d\n",
                            mp3buffer.size, imp3)
            return -1
        }
        if (imp3 <= 0) {
            return 0
        }
        try {
            outf.write(mp3buffer, 0, imp3)
        } catch (e: IOException) {
            System.err.println("Error writing LAME-tag")
            return -1
        }

        return imp3
    }

    private fun lame_encoder(gf: LameGlobalFlags,
                             outf: DataOutput, nogap: Boolean,
                             inPath: String, outPath: String): Int {
        val mp3buffer = ByteArray(Lame.LAME_MAXMP3BUFFER)
        val Buffer = Array(2) { IntArray(1152) }
        var iread: Int

        encoder_progress_begin(gf, inPath, outPath)

        var imp3 = id3!!.lame_get_id3v2_tag(gf, mp3buffer, mp3buffer.size)
        if (imp3 > mp3buffer.size) {
            encoder_progress_end(gf)
            System.err
                    .printf("Error writing ID3v2 tag: buffer too small: buffer size=%d  ID3v2 size=%d\n",
                            mp3buffer.size, imp3)
            return 1
        }
        try {
            outf.write(mp3buffer, 0, imp3)
        } catch (e: IOException) {
            encoder_progress_end(gf)
            System.err.printf("Error writing ID3v2 tag \n")
            return 1
        }

        val id3v2_size = imp3

        /* encode until we hit eof */
        do {
            /* read in 'iread' samples */
            iread = gaud!!.get_audio(gf, Buffer)

            if (iread >= 0) {
                encoder_progress(gf)

                /* encode */
                imp3 = lame!!.lame_encode_buffer_int(gf, Buffer[0], Buffer[1],
                        iread, mp3buffer, 0, mp3buffer.size)

                /* was our output buffer big enough? */
                if (imp3 < 0) {
                    if (imp3 == -1)
                        System.err.printf("mp3 buffer is not big enough... \n")
                    else
                        System.err.printf(
                                "mp3 internal error:  error code=%d\n", imp3)
                    return 1
                }

                try {
                    outf.write(mp3buffer, 0, imp3)
                } catch (e: IOException) {
                    encoder_progress_end(gf)
                    System.err.printf("Error writing mp3 output \n")
                    return 1
                }

            }
        } while (iread > 0)

        if (nogap)
            imp3 = lame!!
                    .lame_encode_flush_nogap(gf, mp3buffer, mp3buffer.size)
        else
            imp3 = lame!!.lame_encode_flush(gf, mp3buffer, 0, mp3buffer.size)/*
		 * may return one more mp3 frame
		 */
        /*
		 * may return one more mp3 frame
		 */

        if (imp3 < 0) {
            if (imp3 == -1)
                System.err.printf("mp3 buffer is not big enough... \n")
            else
                System.err.printf("mp3 internal error:  error code=%d\n", imp3)
            return 1

        }

        encoder_progress_end(gf)

        try {
            outf.write(mp3buffer, 0, imp3)
        } catch (e: IOException) {
            encoder_progress_end(gf)
            System.err.printf("Error writing mp3 output \n")
            return 1
        }

        imp3 = id3!!.lame_get_id3v1_tag(gf, mp3buffer, mp3buffer.size)
        if (imp3 > mp3buffer.size) {
            System.err
                    .printf("Error writing ID3v1 tag: buffer too small: buffer size=%d  ID3v1 size=%d\n",
                            mp3buffer.size, imp3)
        } else {
            if (imp3 > 0) {
                try {
                    outf.write(mp3buffer, 0, imp3)
                } catch (e: IOException) {
                    encoder_progress_end(gf)
                    System.err.printf("Error writing ID3v1 tag \n")
                    return 1
                }

            }
        }

        if (parse!!.silent <= 0) {
            print_lame_tag_leading_info(gf)
        }
        try {
            (outf as Closeable).close()
            val rf = RandomAccessFile(outPath, "rw")
            rf.seek(id3v2_size.toLong())
            write_xing_frame(gf, rf)
            rf.close()
        } catch (e: IOException) {
            System.err.printf("fatal error: can't update LAME-tag frame!\n")
        }

        print_trailing_info(gf)
        return 0
    }

    private fun brhist_init_package(gf: LameGlobalFlags) {
        if (parse!!.brhist) {
            if (hist!!.brhist_init(gf, gf.VBR_min_bitrate_kbps,
                            gf.VBR_max_bitrate_kbps) != 0) {
                /* fail to initialize */
                parse!!.brhist = false
            }
        } else {
            hist!!.brhist_init(gf, 128, 128)
            /* Dirty hack */
        }
    }

    private fun parse_nogap_filenames(nogapout: Int, inPath: String,
                                      outPath: StringBuilder, outdir: StringBuilder) {
        outPath.setLength(0)
        outPath.append(outdir)
        if (0 == nogapout) {
            outPath.setLength(0)
            outPath.append(inPath)
            /* nuke old extension, if one */
            if (outPath.toString().endsWith(".wav")) {
                outPath.setLength(0)
                outPath.append(outPath.substring(0, outPath.length - 4) + ".mp3")
            } else {
                outPath.setLength(0)
                outPath.append("$outPath.mp3")
            }
        } else {
            var slasher = inPath.lastIndexOf(System
                    .getProperty("file.separator"))

            /* backseek to last dir delemiter */

            /* skip one foward if needed */
            if (slasher != 0 && (outPath.toString().endsWith(
                            System.getProperty("file.separator")) || outPath
                            .toString().endsWith(":")))
                slasher++
            else if (slasher == 0 && (!outPath.toString().endsWith(
                            System.getProperty("file.separator")) || outPath
                            .toString().endsWith(":")))
                outPath.append(System.getProperty("file.separator"))

            outPath.append(inPath.substring(slasher))
            /* nuke old extension */
            if (outPath.toString().endsWith(".wav")) {
                val string = outPath.substring(0, outPath.length - 4) + ".mp3"
                outPath.setLength(0)
                outPath.append(string)
            } else {
                val string = "$outPath.mp3"
                outPath.setLength(0)
                outPath.append(string)
            }
        }
    }

    @Throws(IOException::class)
    fun run(args: Array<String>): Int {
        // encoder modules
        lame = Lame()
        gaud = GetAudio()
        ga = GainAnalysis()
        bs = BitStream()
        p = Presets()
        qupvt = QuantizePVT()
        qu = Quantize()
        vbr = VBRTag()
        ver = Version()
        id3 = ID3Tag()
        rv = Reservoir()
        tak = Takehiro()
        parse = Parse()
        hist = BRHist()

        mpg = MPGLib()
        intf = Interface()
        common = Common()

        lame!!.setModules(ga!!, bs!!, p!!, qupvt!!, qu!!, vbr!!, ver!!, id3!!, mpg!!)
        bs!!.setModules(ga!!, mpg!!, ver!!, vbr!!)
        id3!!.setModules(bs!!, ver!!)
        p!!.setModules(lame!!)
        qu!!.setModules(bs!!, rv!!, qupvt!!, tak!!)
        qupvt!!.setModules(tak!!, rv!!, lame!!.enc.psy)
        rv!!.setModules(bs!!)
        tak!!.setModules(qupvt!!)
        vbr!!.setModules(lame!!, bs!!, ver!!)
        gaud!!.setModules(parse!!, mpg!!)
        parse!!.setModules(ver!!, id3!!, p!!)

        // decoder modules
        mpg!!.setModules(intf!!, common!!)
        intf!!.setModules(vbr!!, common!!)

        val outPath = StringBuilder()
        var nogapdir = StringBuilder()
        val inPath = StringBuilder()

        /* add variables for encoder delay/padding */
        val enc = Enc()

        /* support for "nogap" encoding of up to 200 .wav files */
        var nogapout = 0
        var max_nogap = MAX_NOGAP
        val nogap_inPath = arrayOfNulls<String>(max_nogap)
        var outf: DataOutput?

        /* initialize libmp3lame */
        parse!!.input_format = sound_file_format.sf_unknown
        val gf = lame!!.lame_init()
        if (args.size < 1) {
            parse!!.usage(System.err, "lame")
            /*
			 * no command-line args, print usage, exit
			 */
            lame!!.lame_close(gf)
            return 1
        }

        /*
		 * parse the command line arguments, setting various flags in the struct
		 * 'gf'. If you want to parse your own arguments, or call libmp3lame
		 * from a program which uses a GUI to set arguments, skip this call and
		 * set the values of interest in the gf struct. (see the file API and
		 * lame.h for documentation about these parameters)
		 */
        parse_args_from_string(gf, System.getenv("LAMEOPT"), inPath, outPath)
        val argsList = ArrayList<String>()
        for (i in args.indices) {
            argsList.add(args[i])
        }
        val ng = Parse.NoGap()
        var ret = parse!!.parse_args(gf!!, argsList, inPath, outPath, nogap_inPath, ng)
        max_nogap = ng.num_nogap
        if (ret < 0) {
            lame!!.lame_close(gf)
            return if (ret == -2) 0 else 1
        }

        if (parse!!.update_interval < 0.0)
            parse!!.update_interval = 2f

        if (outPath.length != 0 && max_nogap > 0) {
            nogapdir = outPath
            nogapout = 1
        }

        /*
		 * initialize input file. This also sets samplerate and as much other
		 * data on the input file as available in the headers
		 */
        if (max_nogap > 0) {
            /*
			 * for nogap encoding of multiple input files, it is not possible to
			 * specify the output file name, only an optional output directory.
			 */
            parse_nogap_filenames(nogapout, nogap_inPath[0]!!, outPath, nogapdir)
            outf = init_files(gf, nogap_inPath[0]!!, outPath.toString(), enc)
        } else {
            outf = init_files(gf, inPath.toString(), outPath.toString(), enc)
        }
        if (outf == null) {
            lame!!.lame_close(gf)
            return -1
        }
        /*
		 * turn off automatic writing of ID3 tag data into mp3 stream we have to
		 * call it before 'lame_init_params', because that function would spit
		 * out ID3v2 tag data.
		 */
        gf.write_id3tag_automatic = false

        /*
		 * Now that all the options are set, lame needs to analyze them and set
		 * some more internal options and check for problems
		 */
        var i = lame!!.lame_init_params(gf)
        if (i < 0) {
            if (i == -1) {
                parse!!.display_bitrates(System.err)
            }
            System.err.println("fatal error during initialization")
            lame!!.lame_close(gf)
            return i
        }
        if (parse!!.silent > 0) {
            parse!!.brhist = false /* turn off VBR histogram */
        }

        if (gf.decode_only) {
            /* decode an mp3 file to a .wav */
            if (parse!!.mp3_delay_set)
                lame_decoder(gf, outf, parse!!.mp3_delay, inPath.toString(),
                        outPath.toString(), enc)
            else
                lame_decoder(gf, outf, 0, inPath.toString(),
                        outPath.toString(), enc)

        } else {
            if (max_nogap > 0) {
                /*
				 * encode multiple input files using nogap option
				 */
                i = 0
                while (i < max_nogap) {
                    val use_flush_nogap = i != max_nogap - 1
                    if (i > 0) {
                        parse_nogap_filenames(nogapout, nogap_inPath[i]!!,
                                outPath, nogapdir)
                        /*
						 * note: if init_files changes anything, like
						 * samplerate, num_channels, etc, we are screwed
						 */
                        outf = init_files(gf, nogap_inPath[i]!!,
                                outPath.toString(), enc)
                        /*
						 * reinitialize bitstream for next encoding. this is
						 * normally done by lame_init_params(), but we cannot
						 * call that routine twice
						 */
                        lame!!.lame_init_bitstream(gf)
                    }
                    brhist_init_package(gf)
                    gf.internal_flags!!.nogap_total = max_nogap
                    gf.internal_flags!!.nogap_current = i

                    ret = lame_encoder(gf, outf!!, use_flush_nogap,
                            nogap_inPath[i]!!, outPath.toString())

                    (outf as Closeable).close()
                    gaud!!.close_infile() /* close the input file */
                    ++i

                }
            } else {
                /*
				 * encode a single input file
				 */
                brhist_init_package(gf)

                ret = lame_encoder(gf, outf, false, inPath.toString(),
                        outPath.toString())

                (outf as Closeable).close()
                gaud!!.close_infile() /* close the input file */
            }
        }
        lame!!.lame_close(gf)
        return ret
    }

    private fun encoder_progress_begin(gf: LameGlobalFlags,
                                       inPath: String, outPath: String) {
        if (parse!!.silent < 10) {
            lame!!.lame_print_config(gf)
            /* print useful information about options being used */

            System.out.printf("Encoding %s%s to %s\n", inPath, if (inPath.length + outPath.length < 66)
                ""
            else
                "\n     ", outPath)

            System.out.printf("Encoding as %g kHz ", 1e-3 * gf.out_samplerate)

            run {
                val mode_names = arrayOf(arrayOf("stereo", "j-stereo", "dual-ch", "single-ch"), arrayOf("stereo", "force-ms", "dual-ch", "single-ch"))
                when (gf.VBR) {
                    VbrMode.vbr_rh -> System.out.printf(
                            "%s MPEG-%d%s Layer III VBR(q=%g) qval=%d\n",
                            mode_names[if (gf.force_ms) 1 else 0][gf.mode.ordinal],
                            2 - gf.version, if (gf.out_samplerate < 16000)
                        ".5"
                    else
                        "", gf.VBR_q + gf.VBR_q_frac, gf.quality)
                    VbrMode.vbr_mt, VbrMode.vbr_mtrh -> System.out.printf("%s MPEG-%d%s Layer III VBR(q=%d)\n",
                            mode_names[if (gf.force_ms) 1 else 0][gf.mode.ordinal],
                            2 - gf.version, if (gf.out_samplerate < 16000)
                        ".5"
                    else
                        "", gf.quality)
                    VbrMode.vbr_abr -> System.out
                            .printf("%s MPEG-%d%s Layer III (%gx) average %d kbps qval=%d\n",
                                    mode_names[if (gf.force_ms) 1 else 0][gf.mode
                                            .ordinal],
                                    2 - gf.version,
                                    if (gf.out_samplerate < 16000) ".5" else "",
                                    0.1 * (10.0 * gf.compression_ratio + 0.5).toInt(),
                                    gf.VBR_mean_bitrate_kbps, gf.quality)
                    else -> System.out.printf(
                            "%s MPEG-%d%s Layer III (%gx) %3d kbps qval=%d\n",
                            mode_names[if (gf.force_ms) 1 else 0][gf.mode.ordinal],
                            2 - gf.version, if (gf.out_samplerate < 16000)
                        ".5"
                    else
                        "",
                            0.1 * (10.0 * gf.compression_ratio + 0.5).toInt(),
                            gf.brate, gf.quality)
                }
            }

            if (parse!!.silent <= -10) {
                lame!!.lame_print_internals(gf)
            }
            print("|")
            for (i in 0 until MAX_WIDTH - 2) {
                print("=")
            }
            println("|")
            oldConsoleX = 0
            curPercent = oldConsoleX
            oldPercent = curPercent
        }
    }

    private fun encoder_progress(gf: LameGlobalFlags) {
        if (parse!!.silent <= 0) {
            val frames = gf.frameNum
            if (parse!!.update_interval <= 0) {
                /* most likely --disptime x not used */
                if (frames % 100 != 0) {
                    /* true, most of the time */
                    return
                }
            } else {
                if (frames != 0 && frames != 9) {
                    val act = System.currentTimeMillis().toDouble()
                    val dif = act - last_time
                    if (dif >= 0 && dif < parse!!.update_interval) {
                        return
                    }
                }
                last_time = System.currentTimeMillis().toDouble()
                /* from now! disp_time seconds */
            }
            if (parse!!.brhist) {
                hist!!.brhist_jump_back()
            }
            timestatus(gf.frameNum, lame_get_totalframes(gf))
            if (parse!!.brhist) {
                hist!!.brhist_disp(gf)
            }
        }
    }

    private fun encoder_progress_end(gf: LameGlobalFlags) {
        if (parse!!.silent <= 0) {
            if (parse!!.brhist) {
                hist!!.brhist_jump_back()
            }
            timestatus(gf.frameNum, lame_get_totalframes(gf))
            if (parse!!.brhist) {
                hist!!.brhist_disp(gf)
            }
            print("|")
            for (i in 0 until MAX_WIDTH - 2) {
                print("=")
            }
            println("|")
        }
    }

    private fun timestatus(frameNum: Int, totalframes: Int) {
        val percent: Int

        if (frameNum < totalframes) {
            percent = (100.0 * frameNum / totalframes + 0.5).toInt()
        } else {
            percent = 100
        }
        var stepped = false
        if (oldPercent != percent) {
            progressStep()
            stepped = true
        }
        oldPercent = percent
        if (percent == 100) {
            for (i in curPercent..99) {
                progressStep()
                stepped = true
            }
        }
        if (percent == 100 && stepped) {
            println()
        }
    }

    private fun progressStep() {
        curPercent++
        val consoleX = curPercent.toFloat() * MAX_WIDTH / 100f
        if (consoleX.toInt() != oldConsoleX)
            print("")
        oldConsoleX = consoleX.toInt()
        support.firePropertyChange("progress", oldPercent, curPercent)
    }

    /**
     * LAME's estimate of the total number of frames to be encoded. Only valid
     * if calling program set num_samples.
     */
    private fun lame_get_totalframes(gfp: LameGlobalFlags): Int {
        /* estimate based on user set num_samples: */

        return (2 + gfp.num_samples.toDouble() * gfp.out_samplerate / (gfp.in_samplerate.toDouble() * gfp.framesize)).toInt()
    }

    @Throws(IOException::class)
    private fun WriteBytesSwapped(fp: DataOutput, p: ShortArray,
                                  pPos: Int) {
        fp.writeShort(p[pPos].toInt())
    }

    @Throws(IOException::class)
    private fun WriteBytes(fp: DataOutput, p: ShortArray,
                           pPos: Int) {
        /* No error condition checking */
        fp.write(p[pPos] and 0xff)
        fp.write(p[pPos] and 0xffff shr 8 and 0xff)
    }

    companion object {

        private val MAX_NOGAP = 200

        @JvmStatic
        fun main(args: Array<String>) {
            try {
                Main().run(args)
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }

        private val MAX_WIDTH = 79
    }

}
