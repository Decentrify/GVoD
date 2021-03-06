/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * GVoD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.sics.gvod.mngr.util;

import java.io.File;
import java.io.FileFilter;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class VideoFilter implements FileFilter {

    @Override
    public boolean accept(File file) {
        if (!file.isFile()) {
            return false;
        }
        for (AcceptedVideos extension : AcceptedVideos.values()) {
            if (file.getName().endsWith(extension.extension)) {
                return true;
            }
        }
        return false;
    }

    static enum AcceptedVideos {

        MP4(".mp4"), MKV(".mkv");

        private String extension;

        private AcceptedVideos(String extension) {
            this.extension = extension;
        }
    }
}
