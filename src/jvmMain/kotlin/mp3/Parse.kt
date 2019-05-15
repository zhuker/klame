/*
 *      Command line parsing related functions
 *
 *      Copyright (c) 1999 Mark Taylor
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

/* $Id: Parse.java,v 1.28 2011/05/24 22:17:17 kenchis Exp $ */

package mp3

import java.io.FileNotFoundException
import java.io.IOException
import java.io.PrintStream
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.util.ArrayList
import java.util.Scanner

import mp3.GetAudio.sound_file_format

class Parse {

    internal lateinit var ver: Version
    internal lateinit var id3: ID3Tag
    internal lateinit var pre: Presets

    lateinit var input_format: GetAudio.sound_file_format
    /**
     * force byte swapping default=0
     */
    var swapbytes = false
    /**
     * Verbosity
     */
    var silent: Int = 0
    /**
     * Ignore errors in values passed for tags
     */
    private var ignore_tag_errors: Boolean = false
    var brhist: Boolean = false
    /**
     * to use Frank's time status display
     */
    var update_interval: Float = 0f
    /**
     * to adjust the number of samples truncated during decode
     */
    var mp3_delay: Int = 0
    /**
     * user specified the value of the mp3 encoder delay to assume for decoding
     */
    var mp3_delay_set: Boolean = false

    var disable_wav_header: Boolean = false
    /**
     * used by MP3
     */
    var mp3input_data = MP3Data()
    /**
     * print info whether waveform clips
     */
    var print_clipping_info: Boolean = false

    /**
     * WAV signed
     */
    var in_signed = true

    var in_endian = ByteOrder.LITTLE_ENDIAN

    var in_bitwidth = 16

    fun setModules(ver2: Version, id32: ID3Tag, pre2: Presets) {
        this.ver = ver2
        this.id3 = id32
        this.pre = pre2
    }

    /**
     * possible text encodings
     */
    private enum class TextEncoding {
        /**
         * bytes will be stored as-is into ID3 tags, which are Latin1/UCS2 per
         * definition
         */
        TENC_RAW,
        /**
         * text will be converted from local encoding to Latin1, as
         * ID3 needs it
         */
        TENC_LATIN1,
        /**
         * text will be converted from local encoding to UCS-2, as
         * ID3v2 wants it
         */
        TENC_UCS2
    }

    private fun set_id3tag(gfp: LameGlobalFlags, type: Char,
                           str: String?): Boolean {
        when (type.toChar()) {
            'a' -> {
                id3.id3tag_set_artist(gfp, str)
                return false
            }
            't' -> {
                id3.id3tag_set_title(gfp, str)
                return false
            }
            'l' -> {
                id3.id3tag_set_album(gfp, str)
                return false
            }
            'g' -> {
                id3.id3tag_set_genre(gfp, str)
                return false
            }
            'c' -> {
                id3.id3tag_set_comment(gfp, str)
                return false
            }
            'n' -> {
                id3.id3tag_set_track(gfp, str)
                return false
            }
            'y' -> {
                id3.id3tag_set_year(gfp, str)
                return false
            }
            'v' -> {
                id3.id3tag_set_fieldvalue(gfp, str)
                return false
            }
        }
        return false
    }

    private fun set_id3v2tag(gfp: LameGlobalFlags, type: Char,
                             str: String?): Boolean {
        when (type.toChar()) {
            'a' -> {
                id3.id3tag_set_textinfo_ucs2(gfp, "TPE1", str)
                return false
            }
            't' -> {
                id3.id3tag_set_textinfo_ucs2(gfp, "TIT2", str)
                return false
            }
            'l' -> {
                id3.id3tag_set_textinfo_ucs2(gfp, "TALB", str)
                return false
            }
            'g' -> {
                id3.id3tag_set_textinfo_ucs2(gfp, "TCON", str)
                return false
            }
            'c' -> {
                id3.id3tag_set_comment(gfp, null, null, str!!, 0)
                return false
            }
            'n' -> {
                id3.id3tag_set_textinfo_ucs2(gfp, "TRCK", str)
                return false
            }
        }
        return false
    }

    private fun id3_tag(gfp: LameGlobalFlags, type: Char,
                        enc: TextEncoding, str: String): Boolean {
        var x: String? = null
        val result: Boolean
        x = when (enc) {
            TextEncoding.TENC_RAW -> str
            TextEncoding.TENC_LATIN1 -> str/* toLatin1(str) */
            TextEncoding.TENC_UCS2 -> str/* toUcs2(str) */
            else -> str
        }
        result = when (enc) {
            TextEncoding.TENC_RAW, TextEncoding.TENC_LATIN1 -> set_id3tag(gfp, type, x)
            TextEncoding.TENC_UCS2 -> set_id3v2tag(gfp, type, x)
            else -> set_id3tag(gfp, type, x)
        }
        return result
    }

    /**
     * PURPOSE: Writes version and license to the file specified by fp
     */
    private fun lame_version_print(fp: PrintStream) {
        val b = ver.lameOsBitness
        val v = ver.lameVersion
        val u = ver.lameUrl
        val lenb = b.length
        val lenv = v.length
        val lenu = u.length
        /* line width of terminal in characters */
        val lw = 80
        /* static width of text */
        val sw = 16

        if (lw >= lenb + lenv + lenu + sw || lw < lenu + 2)
        /* text fits in 80 chars per line, or line even too small for url */
            if (lenb > 0)
                fp.printf("LAME %s version %s (%s)\n\n", b, v, u)
            else
                fp.printf("LAME version %s (%s)\n\n", v, u)
        else {
            /* text too long, wrap url into next line, right aligned */
            if (lenb > 0)
                fp.printf("LAME %s version %s\n%*s(%s)\n\n", b, v, lw - 2
                        - lenu, "", u)
            else
                fp.printf("LAME version %s\n%*s(%s)\n\n", v, lw - 2 - lenu, "",
                        u)
        }
    }

    private fun print_license(fp: PrintStream) {
        /* print version & license */
        lame_version_print(fp)
        fp.printf("Can I use LAME in my commercial program?\n"
                + "\n"
                + "Yes, you can, under the restrictions of the LGPL.  In particular, you\n"
                + "can include a compiled version of the LAME library (for example,\n"
                + "lame.dll) with a commercial program.  Some notable requirements of\n"
                + "the LGPL:\n" + "\n")
        fp.printf("1. In your program, you cannot include any source code from LAME, with\n"
                + "   the exception of files whose only purpose is to describe the library\n"
                + "   interface (such as lame.h).\n" + "\n")
        fp.printf("2. Any modifications of LAME must be released under the LGPL.\n"
                + "   The LAME project (www.mp3dev.org) would appreciate being\n"
                + "   notified of any modifications.\n" + "\n")
        fp.printf("3. You must give prominent notice that your program is:\n"
                + "      A. using LAME (including version number)\n"
                + "      B. LAME is under the LGPL\n"
                + "      C. Provide a copy of the LGPL.  (the file COPYING contains the LGPL)\n"
                + "      D. Provide a copy of LAME source, or a pointer where the LAME\n"
                + "         source can be obtained (such as http://sourceforge.net/projects/jsidplay2/)\n"
                + "   An example of prominent notice would be an \"About the LAME encoding engine\"\n"
                + "   button in some pull down menu within the executable of your program.\n"
                + "\n")
        fp.printf("4. If you determine that distribution of LAME requires a patent license,\n"
                + "   you must obtain such license.\n" + "\n" + "\n")
        fp.printf("*** IMPORTANT NOTE ***\n"
                + "\n"
                + "The decoding functions provided in LAME use the mpglib decoding engine which\n"
                + "is under the GPL.  They may not be used by any program not released under the\n"
                + "GPL unless you obtain such permission from the MPG123 project (www.mpg123.de).\n"
                + "\n")
    }

    /**
     * PURPOSE: Writes command line syntax to the file specified by fp
     */
    fun usage(fp: PrintStream, ProgramName: String) {
        // print general syntax
        lame_version_print(fp)
        fp.printf(
                "usage: %s [options] <infile> [outfile]\n"
                        + "\n"
                        + "    <infile> and/or <outfile> can be \"-\", which means stdin/stdout.\n"
                        + "\n"
                        + "Try:\n"
                        + "     \"%s --help\"           for general usage information\n"
                        + " or:\n"
                        + "     \"%s --preset help\"    for information on suggested predefined settings\n"
                        + " or:\n"
                        + "     \"%s --longhelp\"\n"
                        + "  or \"%s -?\"              for a complete options list\n\n",
                ProgramName, ProgramName, ProgramName, ProgramName, ProgramName)
    }

    /**
     * PURPOSE: Writes command line syntax to the file specified by fp but only
     * the most important ones, to fit on a vt100 terminal
     */
    private fun short_help(gfp: LameGlobalFlags, fp: PrintStream,
                           ProgramName: String) {
        /* print short syntax help */
        lame_version_print(fp)
        fp.printf(
                "usage: %s [options] <infile> [outfile]\n"
                        + "\n"
                        + "    <infile> and/or <outfile> can be \"-\", which means stdin/stdout.\n"
                        + "\n" + "RECOMMENDED:\n"
                        + "    lame -V 2 input.wav output.mp3\n" + "\n",
                ProgramName)
        fp.printf(
                "OPTIONS:\n"
                        + "    -b bitrate      set the bitrate, default 128 kbps\n"
                        + "    -h              higher quality, but a little slower.  Recommended.\n"
                        + "    -f              fast mode (lower quality)\n"
                        + "    -V n            quality setting for VBR.  default n=%d\n"
                        + "                    0=high quality,bigger files. 9=smaller files\n",
                gfp.VBR_q)
        fp.printf("    --preset type   type must be \"medium\", \"standard\", \"extreme\", \"insane\",\n"
                + "                    or a value for an average desired bitrate and depending\n"
                + "                    on the value specified, appropriate quality settings will\n"
                + "                    be used.\n"
                + "                    \"--preset help\" gives more info on these\n"
                + "\n")
        fp.printf("    --longhelp      full list of options\n" + "\n"
                + "    --license       print License information\n\n")
    }

    private fun long_help(gfp: LameGlobalFlags, fp: PrintStream,
                          ProgramName: String, lessmode: Int) {
        // print long syntax help
        lame_version_print(fp)
        fp.printf(
                "usage: %s [options] <infile> [outfile]\n"
                        + "\n"
                        + "    <infile> and/or <outfile> can be \"-\", which means stdin/stdout.\n"
                        + "\n" + "RECOMMENDED:\n"
                        + "    lame -V2 input.wav output.mp3\n" + "\n",
                ProgramName)
        fp.printf("OPTIONS:\n"
                + "  Input options:\n"
                + "    --scale <arg>   scale input (multiply PCM data) by <arg>\n"
                + "    --scale-l <arg> scale channel 0 (left) input (multiply PCM data) by <arg>\n"
                + "    --scale-r <arg> scale channel 1 (right) input (multiply PCM data) by <arg>\n"
                + "    --mp1input      input file is a MPEG Layer I   file\n"
                + "    --mp2input      input file is a MPEG Layer II  file\n"
                + "    --mp3input      input file is a MPEG Layer III file\n"
                + "    --nogap <file1> <file2> <...>\n"
                + "                    gapless encoding for a set of contiguous files\n"
                + "    --nogapout <dir>\n"
                + "                    output dir for gapless encoding (must precede --nogap)\n"
                + "    --nogaptags     allow the use of VBR tags in gapless encoding\n")
        fp.printf("\n"
                + "  Input options for RAW PCM:\n"
                + "    -r              input is raw pcm\n"
                + "    -x              force byte-swapping of input\n"
                + "    -s sfreq        sampling frequency of input file (kHz) - default 44.1 kHz\n"
                + "    --bitwidth w    input bit width is w (default 16)\n"
                + "    --signed        input is signed (default)\n"
                + "    --unsigned      input is unsigned\n"
                + "    --little-endian input is little-endian (default)\n"
                + "    --big-endian    input is big-endian\n")

        fp.printf("  Operational options:\n"
                + "    -a              downmix from stereo to mono file for mono encoding\n"
                + "    -m <mode>       (j)oint, (s)imple, (f)orce, (d)dual-mono, (m)ono\n"
                + "                    default is (j) or (s) depending on bitrate\n"
                + "                    joint  = joins the best possible of MS and LR stereo\n"
                + "                    simple = force LR stereo on all frames\n"
                + "                    force  = force MS stereo on all frames.\n"
                + "    --preset type   type must be \"medium\", \"standard\", \"extreme\", \"insane\",\n"
                + "                    or a value for an average desired bitrate and depending\n"
                + "                    on the value specified, appropriate quality settings will\n"
                + "                    be used.\n"
                + "                    \"--preset help\" gives more info on these\n"
                + "    --comp  <arg>   choose bitrate to achive a compression ratio of <arg>\n")
        fp.printf("    --replaygain-fast   compute RG fast but slightly inaccurately (default)\n"
                + "    --replaygain-accurate   compute RG more accurately and find the peak sample\n"
                + "    --noreplaygain  disable ReplayGain analysis\n"
                + "    --clipdetect    enable --replaygain-accurate and print a message whether\n"
                + "                    clipping occurs and how far the waveform is from full scale\n")
        fp.printf("    --freeformat    produce a free format bitstream\n"
                + "    --decode        input=mp3 file, output=wav\n"
                + "    -t              disable writing wav header when using --decode\n")

        fp.printf("  Verbosity:\n"
                + "    --disptime <arg>print progress report every arg seconds\n"
                + "    -S              don't print progress report, VBR histograms\n"
                + "    --nohist        disable VBR histogram display\n"
                + "    --silent        don't print anything on screen\n"
                + "    --quiet         don't print anything on screen\n"
                + "    --brief         print more useful information\n"
                + "    --verbose       print a lot of useful information\n"
                + "\n")
        fp.printf("  Noise shaping & psycho acoustic algorithms:\n"
                + "    -q <arg>        <arg> = 0...9.  Default  -q 5 \n"
                + "                    -q 0:  Highest quality, very slow \n"
                + "                    -q 9:  Poor quality, but fast \n"
                + "    -h              Same as -q 2.   Recommended.\n"
                + "    -f              Same as -q 7.   Fast, ok quality\n")

        fp.printf("  CBR (constant bitrate, the default) options:\n"
                + "    -b <bitrate>    set the bitrate in kbps, default 128 kbps\n"
                + "    --cbr           enforce use of constant bitrate\n"
                + "\n"
                + "  ABR options:\n"
                + "    --abr <bitrate> specify average bitrate desired (instead of quality)\n"
                + "\n")
        fp.printf(
                "  VBR options:\n"
                        + "    -V n            quality setting for VBR.  default n=%d\n"
                        + "                    0=high quality,bigger files. 9=smaller files\n"
                        + "    -v              the same as -V 4\n"
                        + "    --vbr-old       use old variable bitrate (VBR) routine\n"
                        + "    --vbr-new       use new variable bitrate (VBR) routine (default)\n",
                gfp.VBR_q)
        fp.printf("    -b <bitrate>    specify minimum allowed bitrate, default  32 kbps\n"
                + "    -B <bitrate>    specify maximum allowed bitrate, default 320 kbps\n"
                + "    -F              strictly enforce the -b option, for use with players that\n"
                + "                    do not support low bitrate mp3\n"
                + "    -t              disable writing LAME Tag\n"
                + "    -T              enable and force writing LAME Tag\n")

        fp.printf("  ATH related:\n"
                + "    --noath         turns ATH down to a flat noise floor\n"
                + "    --athshort      ignore GPSYCHO for short blocks, use ATH only\n"
                + "    --athonly       ignore GPSYCHO completely, use ATH only\n"
                + "    --athtype n     selects between different ATH types [0-4]\n"
                + "    --athlower x    lowers ATH by x dB\n")
        fp.printf("    --athaa-type n  ATH auto adjust: 0 'no' else 'loudness based'\n"
                +
                /**
                 * OBSOLETE
                 * "    --athaa-loudapprox n   n=1 total energy or n=2 equal loudness curve\n"
                 */
                /**
                 * OBSOLETE
                 * "    --athaa-loudapprox n   n=1 total energy or n=2 equal loudness curve\n"
                 */
                "    --athaa-sensitivity x  activation offset in -/+ dB for ATH auto-adjustment\n"
                + "\n")

        fp.printf("  PSY related:\n"
                + "    --short         use short blocks when appropriate\n"
                + "    --noshort       do not use short blocks\n"
                + "    --allshort      use only short blocks\n")
        fp.printf("    --temporal-masking x   x=0 disables, x=1 enables temporal masking effect\n"
                + "    --nssafejoint   M/S switching criterion\n"
                + "    --nsmsfix <arg> M/S switching tuning [effective 0-3.5]\n"
                + "    --interch x     adjust inter-channel masking ratio\n"
                + "    --ns-bass x     adjust masking for sfbs  0 -  6 (long)  0 -  5 (short)\n"
                + "    --ns-alto x     adjust masking for sfbs  7 - 13 (long)  6 - 10 (short)\n"
                + "    --ns-treble x   adjust masking for sfbs 14 - 21 (long) 11 - 12 (short)\n")
        fp.printf("    --ns-sfb21 x    change ns-treble by x dB for sfb21\n"
                + "    --shortthreshold x,y  short block switching threshold,\n"
                + "                          x for L/R/M channel, y for S channel\n"
                + "  Noise Shaping related:\n"
                + "    --substep n     use pseudo substep noise shaping method types 0-2\n")

        fp.printf("  experimental switches:\n"
                + "    -X n[,m]        selects between different noise measurements\n"
                + "                    n for long block, m for short. if m is omitted, m = n\n"
                + "    -Y              lets LAME ignore noise in sfb21, like in CBR\n"
                + "    -Z [n]          currently no effects\n")

        fp.printf("  MP3 header/stream options:\n"
                + "    -e <emp>        de-emphasis n/5/c  (obsolete)\n"
                + "    -c              mark as copyright\n"
                + "    -o              mark as non-original\n"
                + "    -p              error protection.  adds 16 bit checksum to every frame\n"
                + "                    (the checksum is computed correctly)\n"
                + "    --nores         disable the bit reservoir\n"
                + "    --strictly-enforce-ISO   comply as much as possible to ISO MPEG spec\n"
                + "\n")
        fp.printf("  Filter options:\n"
                + "  --lowpass <freq>        frequency(kHz), lowpass filter cutoff above freq\n"
                + "  --lowpass-width <freq>  frequency(kHz) - default 15%% of lowpass freq\n"
                + "  --highpass <freq>       frequency(kHz), highpass filter cutoff below freq\n"
                + "  --highpass-width <freq> frequency(kHz) - default 15%% of highpass freq\n")
        fp.printf("  --resample <sfreq>  sampling frequency of output file(kHz)- default=automatic\n")

        fp.printf("  ID3 tag options:\n"
                + "    --tt <title>    audio/song title (max 30 chars for version 1 tag)\n"
                + "    --ta <artist>   audio/song artist (max 30 chars for version 1 tag)\n"
                + "    --tl <album>    audio/song album (max 30 chars for version 1 tag)\n"
                + "    --ty <year>     audio/song year of issue (1 to 9999)\n"
                + "    --tc <comment>  user-defined text (max 30 chars for v1 tag, 28 for v1.1)\n"
                + "    --tn <track[/total]>   audio/song track number and (optionally) the total\n"
                + "                           number of tracks on the original recording. (track\n"
                + "                           and total each 1 to 255. just the track number\n"
                + "                           creates v1.1 tag, providing a total forces v2.0).\n"
                + "    --tg <genre>    audio/song genre (name or number in list)\n"
                + "    --ti <file>     audio/song albumArt (jpeg/png/gif file, 128KB max, v2.3)\n"
                + "    --tv <id=value> user-defined frame specified by id and value (v2.3 tag)\n")
        fp.printf("    --add-id3v2     force addition of version 2 tag\n"
                + "    --id3v1-only    add only a version 1 tag\n"
                + "    --id3v2-only    add only a version 2 tag\n"
                + "    --space-id3v1   pad version 1 tag with spaces instead of nulls\n"
                + "    --pad-id3v2     same as '--pad-id3v2-size 128'\n"
                + "    --pad-id3v2-size <value> adds version 2 tag, pad with extra <value> bytes\n"
                + "    --genre-list    print alphabetically sorted ID3 genre list and exit\n"
                + "    --ignore-tag-errors  ignore errors in values passed for tags\n"
                + "\n")
        fp.printf("    Note: A version 2 tag will NOT be added unless one of the input fields\n"
                + "    won't fit in a version 1 tag (e.g. the title string is longer than 30\n"
                + "    characters), or the '--add-id3v2' or '--id3v2-only' options are used,\n"
                + "    or output is redirected to stdout.\n"
                + "\nMisc:\n    --license       print License information\n\n")

        display_bitrates(fp)
    }

    fun display_bitrates(fp: PrintStream) {
        display_bitrate(fp, "1", 1, 1)
        display_bitrate(fp, "2", 2, 0)
        display_bitrate(fp, "2.5", 4, 0)
        fp.println()
    }

    private fun display_bitrate(fp: PrintStream, version: String,
                                d: Int, indx: Int) {
        var nBitrates = 14
        if (d == 4)
            nBitrates = 8

        fp.printf(
                "\nMPEG-%-3s layer III sample frequencies (kHz):  %2d  %2d  %g\n" + "bitrates (kbps):", version, 32 / d, 48 / d, 44.1 / d)
        for (i in 1..nBitrates)
            fp.printf(" %2d", Tables.bitrate_table[indx][i])
        fp.println()
    }

    /**
     * PURPOSE: Writes presetting info to #stdout#
     */
    private fun presets_longinfo_dm(msgfp: PrintStream) {
        msgfp.printf("\n"
                + "The --preset switches are aliases over LAME settings.\n"
                + "\n" + "\n")
        msgfp.printf("To activate these presets:\n" + "\n"
                + "   For VBR modes (generally highest quality):\n" + "\n")
        msgfp.printf("     \"--preset medium\" This preset should provide near transparency\n"
                + "                             to most people on most music.\n"
                + "\n"
                + "     \"--preset standard\" This preset should generally be transparent\n"
                + "                             to most people on most music and is already\n"
                + "                             quite high in quality.\n"
                + "\n")
        msgfp.printf("     \"--preset extreme\" If you have extremely good hearing and similar\n"
                + "                             equipment, this preset will generally provide\n"
                + "                             slightly higher quality than the \"standard\"\n"
                + "                             mode.\n" + "\n")
        msgfp.printf("   For CBR 320kbps (highest quality possible from the --preset switches):\n"
                + "\n"
                + "     \"--preset insane\"  This preset will usually be overkill for most\n"
                + "                             people and most situations, but if you must\n"
                + "                             have the absolute highest quality with no\n"
                + "                             regard to filesize, this is the way to go.\n"
                + "\n")
        msgfp.printf("   For ABR modes (high quality per given bitrate but not as high as VBR):\n"
                + "\n"
                + "     \"--preset <kbps>\"  Using this preset will usually give you good\n"
                + "                             quality at a specified bitrate. Depending on the\n"
                + "                             bitrate entered, this preset will determine the\n")
        msgfp.printf("                             optimal settings for that particular situation.\n"
                + "                             While this approach works, it is not nearly as\n"
                + "                             flexible as VBR, and usually will not attain the\n"
                + "                             same level of quality as VBR at higher bitrates.\n"
                + "\n")
        msgfp.printf("The following options are also available for the corresponding profiles:\n"
                + "\n"
                + "   <fast>        standard\n"
                + "   <fast>        extreme\n"
                + "                 insane\n"
                + "   <cbr> (ABR Mode) - The ABR Mode is implied. To use it,\n"
                + "                      simply specify a bitrate. For example:\n"
                + "                      \"--preset 185\" activates this\n"
                + "                      preset and uses 185 as an average kbps.\n"
                + "\n")
        msgfp.printf("   \"fast\" - Enables the fast VBR mode for a particular profile.\n" + "\n")
        msgfp.printf("   \"cbr\"  - If you use the ABR mode (read above) with a significant\n"
                + "            bitrate such as 80, 96, 112, 128, 160, 192, 224, 256, 320,\n"
                + "            you can use the \"cbr\" option to force CBR mode encoding\n"
                + "            instead of the standard abr mode. ABR does provide higher\n"
                + "            quality but CBR may be useful in situations such as when\n"
                + "            streaming an mp3 over the internet may be important.\n"
                + "\n")
        msgfp.printf("    For example:\n" + "\n"
                + "    \"--preset fast standard <input file> <output file>\"\n"
                + " or \"--preset cbr 192 <input file> <output file>\"\n"
                + " or \"--preset 172 <input file> <output file>\"\n"
                + " or \"--preset extreme <input file> <output file>\"\n"
                + "\n" + "\n")
        msgfp.printf("A few aliases are also available for ABR mode:\n"
                + "phone => 16kbps/mono        phon+/lw/mw-eu/sw => 24kbps/mono\n"
                + "mw-us => 40kbps/mono        voice => 56kbps/mono\n"
                + "fm/radio/tape => 112kbps    hifi => 160kbps\n"
                + "cd => 192kbps               studio => 256kbps\n")
    }

    private fun presets_set(gfp: LameGlobalFlags, fast: Int,
                            cbr: Int, preset_name: String, ProgramName: String): Int {
        var preset_name = preset_name
        var mono = 0

        if (preset_name == "help" && fast < 1 && cbr < 1) {
            lame_version_print(System.out)
            presets_longinfo_dm(System.out)
            return -1
        }

        /* aliases for compatibility with old presets */

        if (preset_name == "phone") {
            preset_name = "16"
            mono = 1
        }
        if (preset_name == "phon+" || preset_name == "lw"
                || preset_name == "mw-eu" || preset_name == "sw") {
            preset_name = "24"
            mono = 1
        }
        if (preset_name == "mw-us") {
            preset_name = "40"
            mono = 1
        }
        if (preset_name == "voice") {
            preset_name = "56"
            mono = 1
        }
        if (preset_name == "fm") {
            preset_name = "112"
        }
        if (preset_name == "radio" || preset_name == "tape") {
            preset_name = "112"
        }
        if (preset_name == "hifi") {
            preset_name = "160"
        }
        if (preset_name == "cd") {
            preset_name = "192"
        }
        if (preset_name == "studio") {
            preset_name = "256"
        }

        if (preset_name == "medium") {
            pre.lame_set_VBR_q(gfp, 4)
            if (fast > 0) {
                gfp.VBR = VbrMode.vbr_mtrh
            } else {
                gfp.VBR = VbrMode.vbr_rh
            }
            return 0
        }

        if (preset_name == "standard") {
            pre.lame_set_VBR_q(gfp, 2)
            if (fast > 0) {
                gfp.VBR = VbrMode.vbr_mtrh
            } else {
                gfp.VBR = VbrMode.vbr_rh
            }
            return 0
        } else if (preset_name == "extreme") {
            pre.lame_set_VBR_q(gfp, 0)
            if (fast > 0) {
                gfp.VBR = VbrMode.vbr_mtrh
            } else {
                gfp.VBR = VbrMode.vbr_rh
            }
            return 0
        } else if (preset_name == "insane" && fast < 1) {

            gfp.preset = Lame.INSANE
            pre.apply_preset(gfp, Lame.INSANE, 1)

            return 0
        }

        /* Generic ABR Preset */
        if (Integer.valueOf(preset_name) > 0 && fast < 1) {
            if (Integer.valueOf(preset_name) >= 8 && Integer.valueOf(preset_name) <= 320) {
                gfp.preset = Integer.valueOf(preset_name)
                pre.apply_preset(gfp, Integer.valueOf(preset_name), 1)

                if (cbr == 1)
                    gfp.VBR = VbrMode.vbr_off

                if (mono == 1) {
                    gfp.mode = MPEGMode.MONO
                }

                return 0

            } else {
                lame_version_print(System.err)
                System.err
                        .printf("Error: The bitrate specified is out of the valid range for this preset\n"
                                + "\n"
                                + "When using this mode you must enter a value between \"32\" and \"320\"\n"
                                + "\n"
                                + "For further information try: \"%s --preset help\"\n",
                                ProgramName)
                return -1
            }
        }

        lame_version_print(System.err)
        System.err
                .printf("Error: You did not enter a valid profile and/or options with --preset\n"
                        + "\n"
                        + "Available profiles are:\n"
                        + "\n"
                        + "   <fast>        medium\n"
                        + "   <fast>        standard\n"
                        + "   <fast>        extreme\n"
                        + "                 insane\n"
                        + "          <cbr> (ABR Mode) - The ABR Mode is implied. To use it,\n"
                        + "                             simply specify a bitrate. For example:\n"
                        + "                             \"--preset 185\" activates this\n"
                        + "                             preset and uses 185 as an average kbps.\n"
                        + "\n")
        System.err
                .printf("    Some examples:\n"
                        + "\n"
                        + " or \"%s --preset fast standard <input file> <output file>\"\n"
                        + " or \"%s --preset cbr 192 <input file> <output file>\"\n"
                        + " or \"%s --preset 172 <input file> <output file>\"\n"
                        + " or \"%s --preset extreme <input file> <output file>\"\n"
                        + "\n"
                        + "For further information try: \"%s --preset help\"\n",
                        ProgramName, ProgramName, ProgramName, ProgramName,
                        ProgramName)
        return -1
    }

    /**
     * LAME is a simple frontend which just uses the file extension to determine
     * the file type. Trying to analyze the file contents is well beyond the
     * scope of LAME and should not be added.
     */
    private fun filename_to_type(FileName: String): sound_file_format {
        var FileName = FileName
        val len = FileName.length

        if (len < 4)
            return sound_file_format.sf_unknown

        FileName = FileName.substring(len - 4)
        if (FileName.equals(".mpg", ignoreCase = true))
            return sound_file_format.sf_mp123
        if (FileName.equals(".mp1", ignoreCase = true))
            return sound_file_format.sf_mp123
        if (FileName.equals(".mp2", ignoreCase = true))
            return sound_file_format.sf_mp123
        if (FileName.equals(".mp3", ignoreCase = true))
            return sound_file_format.sf_mp123
        if (FileName.equals(".wav", ignoreCase = true))
            return sound_file_format.sf_wave
        if (FileName.equals(".aif", ignoreCase = true))
            return sound_file_format.sf_aiff
        if (FileName.equals(".raw", ignoreCase = true))
            return sound_file_format.sf_raw
        return if (FileName.equals(".ogg", ignoreCase = true)) sound_file_format.sf_ogg else GetAudio.sound_file_format.sf_unknown
    }

    private fun resample_rate(freq: Double): Int {
        var freq = freq
        if (freq >= 1e3)
            freq *= 1e-3

        when (freq.toInt()) {
            8 -> return 8000
            11 -> return 11025
            12 -> return 12000
            16 -> return 16000
            22 -> return 22050
            24 -> return 24000
            32 -> return 32000
            44 -> return 44100
            48 -> return 48000
            else -> {
                System.err.printf("Illegal resample frequency: %.3f kHz\n", freq)
                return 0
            }
        }
    }

    private fun set_id3_albumart(gfp: LameGlobalFlags,
                                 file_name: String?): Int {
        var ret = -1
        var fpi: RandomAccessFile? = null

        if (file_name == null) {
            return 0
        }
        try {
            fpi = RandomAccessFile(file_name, "r")
            try {
                val size = (fpi.length() and Int.MAX_VALUE.toLong()).toInt()
                val albumart = ByteArray(size)
                fpi.readFully(albumart)
                ret = if (id3.id3tag_set_albumart(gfp, albumart, size)) 0 else 4
            } catch (e: IOException) {
                ret = 3
            } finally {
                try {
                    fpi.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
        } catch (e1: FileNotFoundException) {
            ret = 1
        }

        when (ret) {
            1 -> System.err.printf("Could not find: '%s'.\n", file_name)
            2 -> System.err
                    .printf("Insufficient memory for reading the albumart.\n")
            3 -> System.err.printf("Read error: '%s'.\n", file_name)
            4 -> System.err
                    .printf("Unsupported image: '%s'.\nSpecify JPEG/PNG/GIF image (128KB maximum)\n",
                            file_name)
            else -> {
            }
        }
        return ret
    }

    private enum class ID3TAG_MODE {
        ID3TAG_MODE_DEFAULT, ID3TAG_MODE_V1_ONLY, ID3TAG_MODE_V2_ONLY
    }

    class NoGap {
        var num_nogap: Int = 0
    }

    fun parse_args(gfp: LameGlobalFlags,
                   argv: ArrayList<String>, inPath: StringBuilder,
                   outPath: StringBuilder, nogap_inPath: Array<String?>,
                   ng: NoGap?): Int {
        /* set to 1 if we parse an input file name */
        var input_file = 0
        var autoconvert = 0
        var `val`: Double
        var nogap = 0
        /* set to 1 to use VBR tags in NOGAP mode */
        var nogap_tags = 0
        val ProgramName = "lame"
        var count_nogap = 0
        /* is RG explicitly disabled by the user */
        var noreplaygain = 0
        var id3tag_mode = ID3TAG_MODE.ID3TAG_MODE_DEFAULT

        inPath.setLength(0)
        outPath.setLength(0)
        /* turn on display options. user settings may turn them off below */
        silent = 0
        ignore_tag_errors = false
        brhist = true
        mp3_delay = 0
        mp3_delay_set = false
        print_clipping_info = false
        disable_wav_header = false
        id3.id3tag_init(gfp)

        /* process args */
        var i = 0
        while (i < argv.size) {
            var c: Char
            var token: String
            var tokenPos = 0
            var arg: String
            var nextArg: String
            var argUsed: Int

            token = argv[i]
            if (token[tokenPos++] == '-') {
                argUsed = 0
                nextArg = if (i + 1 < argv.size) argv[i + 1] else ""

                if (token.length - tokenPos == 0) {
                    /* The user wants to use stdin and/or stdout. */
                    input_file = 1
                    if (inPath.length == 0) {
                        inPath.setLength(0)
                        inPath.append(argv[i])
                    } else if (outPath.length == 0) {
                        outPath.setLength(0)
                        outPath.append(argv[i])
                    }
                }
                if (token[tokenPos] == '-') { /* GNU style */
                    tokenPos++

                    if (token.substring(tokenPos).equals("resample", ignoreCase = true)) {
                        argUsed = 1
                        gfp.out_samplerate = resample_rate(java.lang.Double
                                .parseDouble(nextArg))

                    } else if (token.substring(tokenPos).equals(
                                    "vbr-old", ignoreCase = true)) {
                        gfp.VBR = VbrMode.vbr_rh

                    } else if (token.substring(tokenPos).equals(
                                    "vbr-new", ignoreCase = true)) {
                        gfp.VBR = VbrMode.vbr_mtrh

                    } else if (token.substring(tokenPos).equals(
                                    "vbr-mtrh", ignoreCase = true)) {
                        gfp.VBR = VbrMode.vbr_mtrh

                    } else if (token.substring(tokenPos)
                                    .equals("cbr", ignoreCase = true)) {
                        gfp.VBR = VbrMode.vbr_off

                    } else if (token.substring(tokenPos)
                                    .equals("abr", ignoreCase = true)) {
                        argUsed = 1
                        gfp.VBR = VbrMode.vbr_abr
                        gfp.VBR_mean_bitrate_kbps = Integer.valueOf(nextArg)
                        /*
						 * values larger than 8000 are bps (like Fraunhofer), so
						 * it's strange to get 320000 bps MP3 when specifying
						 * 8000 bps MP3
						 */
                        if (gfp.VBR_mean_bitrate_kbps >= 8000)
                            gfp.VBR_mean_bitrate_kbps = (gfp.VBR_mean_bitrate_kbps + 500) / 1000

                        gfp.VBR_mean_bitrate_kbps = Math.min(
                                gfp.VBR_mean_bitrate_kbps, 320)
                        gfp.VBR_mean_bitrate_kbps = Math.max(
                                gfp.VBR_mean_bitrate_kbps, 8)

                    } else if (token.substring(tokenPos).equals(
                                    "r3mix", ignoreCase = true)) {
                        gfp.preset = Lame.R3MIX
                        pre.apply_preset(gfp, Lame.R3MIX, 1)

                    } else if (token.substring(tokenPos).equals(
                                    "bitwidth", ignoreCase = true)) {
                        argUsed = 1
                        in_bitwidth = Integer.valueOf(nextArg)

                    } else if (token.substring(tokenPos).equals(
                                    "signed", ignoreCase = true)) {
                        in_signed = true

                    } else if (token.substring(tokenPos).equals(
                                    "unsigned", ignoreCase = true)) {
                        in_signed = false

                    } else if (token.substring(tokenPos).equals(
                                    "little-endian", ignoreCase = true)) {
                        in_endian = ByteOrder.LITTLE_ENDIAN

                    } else if (token.substring(tokenPos).equals(
                                    "big-endian", ignoreCase = true)) {
                        in_endian = ByteOrder.BIG_ENDIAN

                    } else if (token.substring(tokenPos).equals(
                                    "mp1input", ignoreCase = true)) {
                        input_format = GetAudio.sound_file_format.sf_mp1

                    } else if (token.substring(tokenPos).equals(
                                    "mp2input", ignoreCase = true)) {
                        input_format = GetAudio.sound_file_format.sf_mp2

                    } else if (token.substring(tokenPos).equals(
                                    "mp3input", ignoreCase = true)) {
                        input_format = GetAudio.sound_file_format.sf_mp3

                    } else if (token.substring(tokenPos).equals(
                                    "ogginput", ignoreCase = true)) {
                        System.err
                                .printf("sorry, vorbis support in LAME is deprecated.\n")
                        return -1

                    } else if (token.substring(tokenPos).equals(
                                    "phone", ignoreCase = true)) {
                        if (presets_set(gfp, 0, 0, token, ProgramName) < 0)
                            return -1
                        System.err
                                .printf("Warning: --phone is deprecated, use --preset phone instead!")

                    } else if (token.substring(tokenPos).equals(
                                    "voice", ignoreCase = true)) {
                        if (presets_set(gfp, 0, 0, token, ProgramName) < 0)
                            return -1
                        System.err
                                .printf("Warning: --voice is deprecated, use --preset voice instead!")

                    } else if (INTERNAL_OPTS && token.substring(tokenPos).equals(
                                    "noshort", ignoreCase = true)) {
                        gfp.short_blocks = ShortBlock.short_block_dispensed

                    } else if (INTERNAL_OPTS && token.substring(tokenPos).equals(
                                    "short", ignoreCase = true)) {
                        gfp.short_blocks = ShortBlock.short_block_allowed

                    } else if (INTERNAL_OPTS && token.substring(tokenPos).equals(
                                    "allshort", ignoreCase = true)) {
                        gfp.short_blocks = ShortBlock.short_block_forced

                    } else if (token.substring(tokenPos).equals(
                                    "decode", ignoreCase = true)) {
                        gfp.decode_only = true

                    } else if (token.substring(tokenPos).equals(
                                    "decode-mp3delay", ignoreCase = true)) {
                        mp3_delay = Integer.valueOf(nextArg)
                        mp3_delay_set = true
                        argUsed = 1

                    } else if (token.substring(tokenPos).equals(
                                    "nores", ignoreCase = true)) {
                        gfp.disable_reservoir = true

                    } else if (token.substring(tokenPos).equals(
                                    "strictly-enforce-ISO", ignoreCase = true)) {
                        gfp.strict_ISO = true

                    } else if (token.substring(tokenPos).equals(
                                    "scale", ignoreCase = true)) {
                        argUsed = 1
                        gfp.scale = java.lang.Double.parseDouble(nextArg).toFloat()

                    } else if (token.substring(tokenPos).equals(
                                    "scale-l", ignoreCase = true)) {
                        argUsed = 1
                        gfp.scale_left = java.lang.Double.parseDouble(nextArg).toFloat()

                    } else if (token.substring(tokenPos).equals(
                                    "scale-r", ignoreCase = true)) {
                        argUsed = 1
                        gfp.scale_right = java.lang.Double.parseDouble(nextArg).toFloat()

                    } else if (token.substring(tokenPos).equals(
                                    "freeformat", ignoreCase = true)) {
                        gfp.free_format = true

                    } else if (token.substring(tokenPos).equals(
                                    "replaygain-fast", ignoreCase = true)) {
                        gfp.findReplayGain = true

                    } else if (token.substring(tokenPos).equals(
                                    "replaygain-accurate", ignoreCase = true)) {
                        gfp.decode_on_the_fly = true
                        gfp.findReplayGain = true

                    } else if (token.substring(tokenPos).equals(
                                    "noreplaygain", ignoreCase = true)) {
                        noreplaygain = 1
                        gfp.findReplayGain = false

                    } else if (token.substring(tokenPos).equals(
                                    "clipdetect", ignoreCase = true)) {
                        print_clipping_info = true
                        gfp.decode_on_the_fly = true

                    } else if (token.substring(tokenPos).equals(
                                    "nohist", ignoreCase = true)) {
                        brhist = false

                        /* options for ID3 tag */
                    } else if (token.substring(tokenPos).equals("tt", ignoreCase = true)) {
                        argUsed = 1
                        id3_tag(gfp, 't', TextEncoding.TENC_RAW, nextArg)

                    } else if (token.substring(tokenPos).equals("ta", ignoreCase = true)) {
                        argUsed = 1
                        id3_tag(gfp, 'a', TextEncoding.TENC_RAW, nextArg)

                    } else if (token.substring(tokenPos).equals("tl", ignoreCase = true)) {
                        argUsed = 1
                        id3_tag(gfp, 'l', TextEncoding.TENC_RAW, nextArg)

                    } else if (token.substring(tokenPos).equals("ty", ignoreCase = true)) {
                        argUsed = 1
                        id3_tag(gfp, 'y', TextEncoding.TENC_RAW, nextArg)

                    } else if (token.substring(tokenPos).equals("tc", ignoreCase = true)) {
                        argUsed = 1
                        id3_tag(gfp, 'c', TextEncoding.TENC_RAW, nextArg)

                    } else if (token.substring(tokenPos).equals("tn", ignoreCase = true)) {
                        val ret = id3_tag(gfp, 'n', TextEncoding.TENC_RAW,
                                nextArg)
                        argUsed = 1
                        if (ret) {
                            if (!ignore_tag_errors) {
                                if (id3tag_mode == ID3TAG_MODE.ID3TAG_MODE_V1_ONLY) {
                                    System.err
                                            .printf("The track number has to be between 1 and 255 for ID3v1.\n")
                                    return -1
                                } else if (id3tag_mode == ID3TAG_MODE.ID3TAG_MODE_V2_ONLY) {
                                    /*
									 * track will be stored as-is in ID3v2 case,
									 * so no problem here
									 */
                                } else {
                                    if (silent < 10) {
                                        System.err
                                                .printf("The track number has to be between 1 and 255 for ID3v1, ignored for ID3v1.\n")
                                    }
                                }
                            }
                        }

                    } else if (token.substring(tokenPos).equals("tg", ignoreCase = true)) {
                        id3_tag(gfp, 'g', TextEncoding.TENC_RAW, nextArg)
                        argUsed = 1

                    } else if (token.substring(tokenPos).equals("tv", ignoreCase = true)) {
                        argUsed = 1
                        if (id3_tag(gfp, 'v', TextEncoding.TENC_RAW, nextArg)) {
                            if (silent < 10) {
                                System.err.printf(
                                        "Invalid field value: '%s'. Ignored\n",
                                        nextArg)
                            }
                        }

                    } else if (token.substring(tokenPos).equals("ti", ignoreCase = true)) {
                        argUsed = 1
                        if (set_id3_albumart(gfp, nextArg) != 0) {
                            if (!ignore_tag_errors) {
                                return -1
                            }
                        }

                    } else if (token.substring(tokenPos).equals(
                                    "ignore-tag-errors", ignoreCase = true)) {
                        ignore_tag_errors = true

                    } else if (token.substring(tokenPos).equals(
                                    "add-id3v2", ignoreCase = true)) {
                        id3.id3tag_add_v2(gfp)

                    } else if (token.substring(tokenPos).equals(
                                    "id3v1-only", ignoreCase = true)) {
                        id3.id3tag_v1_only(gfp)
                        id3tag_mode = ID3TAG_MODE.ID3TAG_MODE_V1_ONLY

                    } else if (token.substring(tokenPos).equals(
                                    "id3v2-only", ignoreCase = true)) {
                        id3.id3tag_v2_only(gfp)
                        id3tag_mode = ID3TAG_MODE.ID3TAG_MODE_V2_ONLY

                    } else if (token.substring(tokenPos).equals(
                                    "space-id3v1", ignoreCase = true)) {
                        id3.id3tag_space_v1(gfp)

                    } else if (token.substring(tokenPos).equals(
                                    "pad-id3v2", ignoreCase = true)) {
                        id3.id3tag_pad_v2(gfp)

                    } else if (token.substring(tokenPos).equals(
                                    "pad-id3v2-size", ignoreCase = true)) {
                        var n = Integer.valueOf(nextArg)
                        n = if (n <= 128000) n else 128000
                        n = if (n >= 0) n else 0
                        id3.id3tag_set_pad(gfp, n)
                        argUsed = 1

                    } else if (token.substring(tokenPos).equals(
                                    "genre-list", ignoreCase = true)) {
                        id3.id3tag_genre_list(object : GenreListHandler {
                            override fun genre_list_handler(num: Int, name: String) {
                                System.out.printf("%3d %s\n", num, name)
                            }
                        })
                        return -2

                        // XXX Unsupported: some experimental switches for
                        // setting ID3 tags

                    } else if (token.substring(tokenPos).equals(
                                    "lowpass", ignoreCase = true)) {
                        `val` = java.lang.Double.parseDouble(nextArg)
                        argUsed = 1
                        if (`val` < 0) {
                            gfp.lowpassfreq = -1
                        } else {
                            /* useful are 0.001 kHz...50 kHz, 50 Hz...50000 Hz */
                            if (`val` < 0.001 || `val` > 50000.0) {
                                System.err
                                        .printf("Must specify lowpass with --lowpass freq, freq >= 0.001 kHz\n")
                                return -1
                            }
                            gfp.lowpassfreq = (`val` * if (`val` < 50.0) 1e3 else 1e0 + 0.5).toInt()
                        }

                    } else if (token.substring(tokenPos).equals(
                                    "lowpass-width", ignoreCase = true)) {
                        `val` = java.lang.Double.parseDouble(nextArg)
                        argUsed = 1
                        /* useful are 0.001 kHz...16 kHz, 16 Hz...50000 Hz */
                        if (`val` < 0.001 || `val` > 50000.0) {
                            System.err
                                    .printf("Must specify lowpass width with --lowpass-width freq, freq >= 0.001 kHz\n")
                            return -1
                        }
                        gfp.lowpassfreq = (`val` * if (`val` < 16.0) 1e3 else 1e0 + 0.5).toInt()

                    } else if (token.substring(tokenPos).equals(
                                    "highpass", ignoreCase = true)) {
                        `val` = java.lang.Double.parseDouble(nextArg)
                        argUsed = 1
                        if (`val` < 0.0) {
                            gfp.highpassfreq = -1
                        } else {
                            /* useful are 0.001 kHz...16 kHz, 16 Hz...50000 Hz */
                            if (`val` < 0.001 || `val` > 50000.0) {
                                System.err
                                        .printf("Must specify highpass with --highpass freq, freq >= 0.001 kHz\n")
                                return -1
                            }
                            gfp.highpassfreq = (`val` * if (`val` < 16.0) 1e3 else 1e0 + 0.5).toInt()
                        }

                    } else if (token.substring(tokenPos).equals(
                                    "highpass-width", ignoreCase = true)) {
                        `val` = java.lang.Double.parseDouble(nextArg)
                        argUsed = 1
                        /* useful are 0.001 kHz...16 kHz, 16 Hz...50000 Hz */
                        if (`val` < 0.001 || `val` > 50000.0) {
                            System.err
                                    .printf("Must specify highpass width with --highpass-width freq, freq >= 0.001 kHz\n")
                            return -1
                        }
                        gfp.highpasswidth = `val`.toInt()

                    } else if (token.substring(tokenPos).equals(
                                    "comp", ignoreCase = true)) {
                        argUsed = 1
                        `val` = java.lang.Double.parseDouble(nextArg)
                        if (`val` < 1.0) {
                            System.err
                                    .printf("Must specify compression ratio >= 1.0\n")
                            return -1
                        }
                        gfp.compression_ratio = `val`.toFloat()

                    } else if (token.substring(tokenPos).equals(
                                    "notemp", ignoreCase = true)) {
                        gfp.useTemporal = false

                    } else if (token.substring(tokenPos).equals(
                                    "interch", ignoreCase = true)) {
                        argUsed = 1
                        gfp.interChRatio = java.lang.Double.parseDouble(nextArg).toFloat()

                    } else if (token.substring(tokenPos).equals(
                                    "temporal-masking", ignoreCase = true)) {
                        argUsed = 1
                        gfp.useTemporal = Integer.valueOf(nextArg) != 0

                    } else if (token.substring(tokenPos).equals(
                                    "nssafejoint", ignoreCase = true)) {
                        gfp.exp_nspsytune = gfp.exp_nspsytune or 2

                    } else if (token.substring(tokenPos).equals(
                                    "nsmsfix", ignoreCase = true)) {
                        argUsed = 1
                        gfp.msfix = java.lang.Double.parseDouble(nextArg).toFloat()

                    } else if (token.substring(tokenPos).equals(
                                    "ns-bass", ignoreCase = true)) {
                        argUsed = 1
                        run {
                            val d: Double
                            var k: Int
                            d = java.lang.Double.parseDouble(nextArg)
                            k = (d * 4).toInt()
                            if (k < -32)
                                k = -32
                            if (k > 31)
                                k = 31
                            if (k < 0)
                                k += 64
                            gfp.exp_nspsytune = gfp.exp_nspsytune or (k shl 2)
                        }

                    } else if (token.substring(tokenPos).equals(
                                    "ns-alto", ignoreCase = true)) {
                        argUsed = 1
                        run {
                            val d: Double
                            var k: Int
                            d = java.lang.Double.parseDouble(nextArg)
                            k = (d * 4).toInt()
                            if (k < -32)
                                k = -32
                            if (k > 31)
                                k = 31
                            if (k < 0)
                                k += 64
                            gfp.exp_nspsytune = gfp.exp_nspsytune or (k shl 8)
                        }

                    } else if (token.substring(tokenPos).equals(
                                    "ns-treble", ignoreCase = true)) {
                        argUsed = 1
                        run {
                            val d: Double
                            var k: Int
                            d = java.lang.Double.parseDouble(nextArg)
                            k = (d * 4).toInt()
                            if (k < -32)
                                k = -32
                            if (k > 31)
                                k = 31
                            if (k < 0)
                                k += 64
                            gfp.exp_nspsytune = gfp.exp_nspsytune or (k shl 14)
                        }

                    } else if (token.substring(tokenPos).equals(
                                    "ns-sfb21", ignoreCase = true)) {
                        /*
						 * to be compatible with Naoki's original code, ns-sfb21
						 * specifies how to change ns-treble for sfb21
						 */
                        argUsed = 1
                        run {
                            val d: Double
                            var k: Int
                            d = java.lang.Double.parseDouble(nextArg)
                            k = (d * 4).toInt()
                            if (k < -32)
                                k = -32
                            if (k > 31)
                                k = 31
                            if (k < 0)
                                k += 64
                            gfp.exp_nspsytune = gfp.exp_nspsytune or (k shl 20)
                        }

                    } else if (token.substring(tokenPos).equals(
                                    "nspsytune2", ignoreCase = true)) {

                    } else if (token.substring(tokenPos).equals(
                                    "quiet", ignoreCase = true) || token.substring(tokenPos).equals(
                                    "silent", ignoreCase = true)) {
                        silent = 10 /* on a scale from 1 to 10 be very silent */

                    } else if (token.substring(tokenPos).equals(
                                    "brief", ignoreCase = true)) {
                        silent = -5 /* print few info on screen */

                    } else if (token.substring(tokenPos).equals(
                                    "verbose", ignoreCase = true)) {
                        silent = -10 /* print a lot on screen */

                    } else if (token.substring(tokenPos).equals(
                                    "version", ignoreCase = true) || token.substring(tokenPos).equals(
                                    "license", ignoreCase = true)) {
                        print_license(System.out)
                        return -2

                    } else if (token.substring(tokenPos).equals(
                                    "help", ignoreCase = true) || token.substring(tokenPos).equals(
                                    "usage", ignoreCase = true)) {
                        short_help(gfp, System.out, ProgramName)
                        return -2

                    } else if (token.substring(tokenPos).equals(
                                    "longhelp", ignoreCase = true)) {
                        long_help(gfp, System.out, ProgramName, 0 /* lessmode=NO */)
                        return -2

                    } else if (token.substring(tokenPos).equals("?", ignoreCase = true)) {
                        long_help(gfp, System.out, ProgramName, 1 /* lessmode=YES */)
                        return -2

                    } else if (token.substring(tokenPos).equals(
                                    "preset", ignoreCase = true) || token.substring(tokenPos).equals(
                                    "alt-preset", ignoreCase = true)) {
                        argUsed = 1
                        run {
                            var fast = 0
                            var cbr = 0

                            while (nextArg == "fast" || nextArg == "cbr") {

                                if (nextArg == "fast" && fast < 1)
                                    fast = 1
                                if (nextArg == "cbr" && cbr < 1)
                                    cbr = 1

                                argUsed++
                                nextArg = if (i + argUsed < argv.size)
                                    argv[i + argUsed]
                                else
                                    ""
                            }

                            if (presets_set(gfp, fast, cbr, nextArg,
                                            ProgramName) < 0)
                                return -1
                        }

                    } else if (token.substring(tokenPos).equals(
                                    "disptime", ignoreCase = true)) {
                        argUsed = 1
                        update_interval = java.lang.Double.parseDouble(nextArg).toFloat()

                    } else if (token.substring(tokenPos).equals(
                                    "nogaptags", ignoreCase = true)) {
                        nogap_tags = 1

                    } else if (token.substring(tokenPos).equals(
                                    "nogapout", ignoreCase = true)) {
                        outPath.setLength(0)
                        outPath.append(nextArg)
                        argUsed = 1

                    } else if (token.substring(tokenPos).equals(
                                    "nogap", ignoreCase = true)) {
                        nogap = 1

                    } else if (INTERNAL_OPTS && token.substring(tokenPos).equals(
                                    "tune", ignoreCase = true)) { /* without helptext */
                        argUsed = 1
                        run {
                            gfp.tune_value_a = java.lang.Double
                                    .parseDouble(nextArg).toFloat()
                            gfp.tune = true
                        }

                    } else {
                        val internal_flags = gfp.internal_flags!!
                        if (INTERNAL_OPTS && token.substring(tokenPos).equals(
                                        "shortthreshold", ignoreCase = true)) {
                            run {
                                val x: Float
                                val y: Float
                                val sc = Scanner(nextArg)
                                x = sc.nextFloat()
                                if (!sc.hasNext()) {
                                    y = x
                                } else {
                                    sc.nextByte()
                                    y = sc.nextFloat()
                                }
                                argUsed = 1
                                internal_flags.nsPsy.attackthre = x
                                internal_flags.nsPsy.attackthre_s = y
                            }

                        } else if (INTERNAL_OPTS && token.substring(tokenPos).equals(
                                        "maskingadjust", ignoreCase = true)) { /* without helptext */
                            argUsed = 1
                            gfp.maskingadjust = java.lang.Double.parseDouble(nextArg).toFloat()

                        } else if (INTERNAL_OPTS && token.substring(tokenPos).equals(
                                        "maskingadjustshort", ignoreCase = true)) { /* without helptext */
                            argUsed = 1
                            gfp.maskingadjust_short = java.lang.Double
                                    .parseDouble(nextArg).toFloat()

                        } else if (INTERNAL_OPTS && token.substring(tokenPos).equals(
                                        "athcurve", ignoreCase = true)) { /* without helptext */
                            argUsed = 1
                            gfp.ATHcurve = java.lang.Double.parseDouble(nextArg).toFloat()

                        } else if (INTERNAL_OPTS && token.substring(tokenPos).equals(
                                        "no-preset-tune", ignoreCase = true)) { /* without helptext */

                        } else if (INTERNAL_OPTS && token.substring(tokenPos).equals(
                                        "substep", ignoreCase = true)) {
                            argUsed = 1
                            internal_flags.substep_shaping = Integer
                                    .valueOf(nextArg)

                        } else if (INTERNAL_OPTS && token.substring(tokenPos).equals(
                                        "sbgain", ignoreCase = true)) { /* without helptext */
                            argUsed = 1
                            internal_flags.subblock_gain = Integer
                                    .valueOf(nextArg)

                        } else if (INTERNAL_OPTS && token.substring(tokenPos).equals(
                                        "sfscale", ignoreCase = true)) { /* without helptext */
                            internal_flags.noise_shaping = 2

                        } else if (INTERNAL_OPTS && token.substring(tokenPos).equals(
                                        "noath", ignoreCase = true)) {
                            gfp.noATH = true

                        } else if (INTERNAL_OPTS && token.substring(tokenPos).equals(
                                        "athonly", ignoreCase = true)) {
                            gfp.ATHonly = true

                        } else if (INTERNAL_OPTS && token.substring(tokenPos).equals(
                                        "athshort", ignoreCase = true)) {
                            gfp.ATHshort = true

                        } else if (INTERNAL_OPTS && token.substring(tokenPos).equals(
                                        "athlower", ignoreCase = true)) {
                            argUsed = 1
                            gfp.ATHlower = -java.lang.Double.parseDouble(nextArg).toFloat() / 10.0f

                        } else if (INTERNAL_OPTS && token.substring(tokenPos).equals(
                                        "athtype", ignoreCase = true)) {
                            argUsed = 1
                            gfp.ATHtype = Integer.valueOf(nextArg)

                        } else if (INTERNAL_OPTS && token.substring(tokenPos).equals(
                                        "athaa-type", ignoreCase = true)) {
                            /*
                             * switch for developing, no DOCU
                             */
                            argUsed = 1
                            /*
                             * once was 1:Gaby, 2:Robert, 3:Jon, else:off
                             */
                            gfp.athaa_type = Integer.valueOf(nextArg)
                            /*
                             * now: 0:off else:Jon
                             */

                        } else if (token.substring(tokenPos).equals(
                                        "athaa-sensitivity", ignoreCase = true)) {
                            argUsed = 1
                            gfp.athaa_sensitivity = java.lang.Double
                                    .parseDouble(nextArg).toFloat()

                        } else {
                            run {
                                System.err.printf("%s: unrecognized option --%s\n",
                                        ProgramName, token)
                                return -1
                            }
                        }
                    }
                    i += argUsed

                } else {
                    while (tokenPos < token.length) {
                        c = token[tokenPos++]

                        arg = if (tokenPos < token.length) token else nextArg
                        when (c) {
                            'm' -> {
                                argUsed = 1

                                when (arg[0]) {
                                    's' -> gfp.mode = MPEGMode.STEREO
                                    'd' -> gfp.mode = MPEGMode.DUAL_CHANNEL
                                    'f' -> {
                                        gfp.force_ms = true
                                        gfp.mode = MPEGMode.JOINT_STEREO
                                    }
                                    /* FALLTHROUGH */
                                    'j' -> gfp.mode = MPEGMode.JOINT_STEREO
                                    'm' -> gfp.mode = MPEGMode.MONO
                                    'a' -> gfp.mode = MPEGMode.JOINT_STEREO
                                    else -> {
                                        System.err
                                                .printf("%s: -m mode must be s/d/j/f/m not %s\n",
                                                        ProgramName, arg)
                                        return -1
                                    }
                                }
                            }

                            'V' -> {
                                argUsed = 1
                                /* to change VBR default look in lame.h */
                                if (gfp.VBR == VbrMode.vbr_off) {
                                    gfp.VBR_q = VbrMode.vbr_default.ordinal
                                    gfp.VBR_q_frac = 0f
                                }
                                gfp.VBR_q = java.lang.Double.parseDouble(arg).toFloat().toInt()
                                gfp.VBR_q_frac = java.lang.Double.parseDouble(arg).toFloat() - gfp.VBR_q
                            }
                            'v' ->
                                /* to change VBR default look in lame.h */
                                if (gfp.VBR == VbrMode.vbr_off)
                                    gfp.VBR = VbrMode.vbr_mtrh

                            'q' -> {
                                argUsed = 1
                                run {
                                    var tmp_quality = Integer.valueOf(arg)

                                    /*
								 * XXX should we move this into
								 * lame_set_quality()?
								 */
                                    if (tmp_quality < 0)
                                        tmp_quality = 0
                                    if (tmp_quality > 9)
                                        tmp_quality = 9

                                    gfp.quality = tmp_quality
                                }
                            }
                            'f' -> gfp.quality = 7
                            'h' -> gfp.quality = 2

                            's' -> {
                                argUsed = 1
                                `val` = java.lang.Double.parseDouble(arg)
                                gfp.in_samplerate = (`val` * if (`val` <= 192) 1e3 else 1e0 + 0.5).toInt()
                            }
                            'b' -> {
                                argUsed = 1
                                gfp.brate = Integer.valueOf(arg)

                                if (gfp.brate > 320) {
                                    gfp.disable_reservoir = true
                                }
                                gfp.VBR_min_bitrate_kbps = gfp.brate
                            }
                            'B' -> {
                                argUsed = 1
                                gfp.VBR_max_bitrate_kbps = Integer.valueOf(arg)
                            }
                            'F' -> gfp.VBR_hard_min = 1
                            't' /* dont write VBR tag */ -> {
                                gfp.bWriteVbrTag = false
                                disable_wav_header = true
                            }
                            'T' /* do write VBR tag */ -> {
                                gfp.bWriteVbrTag = true
                                nogap_tags = 1
                                disable_wav_header = false
                            }
                            'r' /* force raw pcm input file */ -> input_format = sound_file_format.sf_raw
                            'x' /* force byte swapping */ -> swapbytes = true
                            'p' ->
                                /*
							 * (jo) error_protection: add crc16 information to
							 * stream
							 */
                                gfp.error_protection = true
                            'a' -> {
                                /*
							 * autoconvert input file from stereo to mono - for
							 * mono mp3 encoding
							 */
                                autoconvert = 1
                                gfp.mode = MPEGMode.MONO
                            }
                            'S' -> silent = 10
                            'X' ->
                                /*
							 * experimental switch -X: the differnt types of
							 * quant compare are tough to communicate to
							 * endusers, so they shouldn't bother to toy around
							 * with them
							 */ {
                                val x: Int
                                val y: Int
                                val sc = Scanner(arg)
                                x = sc.nextInt()
                                if (!sc.hasNext()) {
                                    y = x
                                } else {
                                    sc.nextByte()
                                    y = sc.nextInt()
                                }
                                argUsed = 1
                                if (INTERNAL_OPTS) {
                                    gfp.quant_comp = x
                                    gfp.quant_comp_short = y
                                }
                            }
                            'Y' -> gfp.experimentalY = true
                            'Z' ->
                                /*
							 * experimental switch -Z: this switch is obsolete
							 */ {
                                var n = 1
                                val sc = Scanner(arg)
                                n = sc.nextInt()
                                if (INTERNAL_OPTS) {
                                    gfp.experimentalZ = n
                                }
                            }
                            'e' -> {
                                argUsed = 1

                                when (arg[0]) {
                                    'n' -> gfp.emphasis = 0
                                    '5' -> gfp.emphasis = 1
                                    'c' -> gfp.emphasis = 3
                                    else -> {
                                        System.err.printf(
                                                "%s: -e emp must be n/5/c not %s\n",
                                                ProgramName, arg)
                                        return -1
                                    }
                                }
                            }
                            'c' -> gfp.copyright = 1
                            'o' -> gfp.original = 0

                            '?' -> {
                                long_help(gfp, System.out, ProgramName, 0 /*
																	 * LESSMODE=NO
																	 */)
                                return -1
                            }

                            else -> {
                                System.err.printf("%s: unrecognized option -%c\n",
                                        ProgramName, c)
                                return -1
                            }
                        }
                        if (argUsed != 0) {
                            if (arg === token)
                                token = "" /* no more from token */
                            else
                                ++i /* skip arg we used */
                            arg = ""
                            argUsed = 0
                        }
                    }
                }
            } else {
                if (nogap != 0) {
                    if (ng != null && count_nogap < ng.num_nogap) {
                        nogap_inPath[count_nogap++] = argv[i]
                        input_file = 1
                    } else {
                        /* sorry, calling program did not allocate enough space */
                        System.err
                                .printf("Error: 'nogap option'.  Calling program does not allow nogap option, or\n" + "you have exceeded maximum number of input files for the nogap option\n")
                        ng!!.num_nogap = -1
                        return -1
                    }
                } else {
                    /* normal options: inputfile [outputfile] */
                    if (inPath.length == 0) {
                        inPath.setLength(0)
                        inPath.append(argv[i])
                        input_file = 1
                    } else {
                        if (outPath.length == 0) {
                            outPath.setLength(0)
                            outPath.append(argv[i])
                        } else {
                            System.err.printf("%s: excess arg %s\n",
                                    ProgramName, argv[i])
                            return -1
                        }
                    }
                }
            }
            i++
        } /* loop over args */

        if (0 == input_file) {
            usage(System.out, ProgramName)
            return -1
        }

        if (inPath.toString()[0] == '-')
            silent = if (silent <= 1) 1 else silent

        if (outPath.length == 0 && count_nogap == 0) {
            outPath.setLength(0)
            outPath.append(inPath.substring(0, inPath.length - 4))
            if (gfp.decode_only) {
                outPath.append(".mp3.wav")
            } else {
                outPath.append(".mp3")
            }
        }

        /* RG is enabled by default */
        if (0 == noreplaygain)
            gfp.findReplayGain = true

        /* disable VBR tags with nogap unless the VBR tags are forced */
        if (nogap != 0 && gfp.bWriteVbrTag && nogap_tags == 0) {
            println("Note: Disabling VBR Xing/Info tag since it interferes with --nogap\n")
            gfp.bWriteVbrTag = false
        }

        /* some file options not allowed with stdout */
        if (outPath.toString()[0] == '-') {
            gfp.bWriteVbrTag = false /* turn off VBR tag */
        }

        /* if user did not explicitly specify input is mp3, check file name */
        if (input_format === sound_file_format.sf_unknown)
            input_format = filename_to_type(inPath.toString())

        if (input_format === sound_file_format.sf_ogg) {
            System.err.printf("sorry, vorbis support in LAME is deprecated.\n")
            return -1
        }
        /* default guess for number of channels */
        if (autoconvert != 0)
            gfp.num_channels = 2
        else if (MPEGMode.MONO == gfp.mode)
            gfp.num_channels = 1
        else
            gfp.num_channels = 2

        if (gfp.free_format) {
            if (gfp.brate < 8 || gfp.brate > 640) {
                System.err
                        .printf("For free format, specify a bitrate between 8 and 640 kbps\n")
                System.err.printf("with the -b <bitrate> option\n")
                return -1
            }
        }
        if (ng != null)
            ng.num_nogap = count_nogap
        return 0
    }

    companion object {

        private val INTERNAL_OPTS = false
    }

}
