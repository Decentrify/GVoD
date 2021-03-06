/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * KompicsToolbox is free software; you can redistribute it and/or
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
package se.sics.nstream.test;

import java.util.Arrays;
import se.sics.ktoolbox.util.test.EqualComparator;
import se.sics.nstream.storage.durable.events.DStorageWrite;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class DStorageWriteReqEC implements EqualComparator<DStorageWrite.Request> {
    @Override
    public boolean isEqual(DStorageWrite.Request o1, DStorageWrite.Request o2) {
        if(o1.pos != o2.pos) {
            return false;
        }
        return Arrays.equals(o1.value, o2.value);
    }
}
