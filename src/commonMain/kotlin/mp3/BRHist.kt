/*
 *	Bitrate histogram source file
 *
 *	Copyright (c) 2000 Mark Taylor
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	 See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

/* $Id: BRHist.java,v 1.3 2011/05/24 22:02:42 kenchis Exp $ */

package mp3

// XXX VBR histogram console output is unsupported in java.
// Console features like cursor-up etc. are missing!
class BRHist {

    fun brhist_init(gf: LameGlobalFlags, bitrate_kbps_min: Int,
                             bitrate_kbps_max: Int): Int {
        return 0
    }

    fun brhist_jump_back() {

    }

    fun brhist_disp(gf: LameGlobalFlags) {}

}
