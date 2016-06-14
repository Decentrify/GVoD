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
package se.sics.gvod.stream.congestion;


/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public interface PLedbatState {

    public void setSendingTime(long time);

    public long getSendingTime();
    
    public void setReceivedTime(long time);

    public long getReceivedTime();

    public void setStatus(Status status);

    public Status getStatus();

    public static class Impl implements PLedbatState {

        private Long sendingTime;
        private Long receivedTime;
        private Status status;

        Impl(Long sendingTime) {
            this.sendingTime = sendingTime;
            status = null;
            receivedTime = null;
        }

        public Impl() {
            this(null);
        }

        @Override
        public void setSendingTime(long time) {
            this.sendingTime = time;
        }

        @Override
        public long getSendingTime() {
            return sendingTime;
        }
        
        @Override
        public void setReceivedTime(long time) {
            this.receivedTime = time;
        }

        @Override
        public long getReceivedTime() {
            return receivedTime;
        }

        @Override
        public void setStatus(Status status) {
            this.status = status;
        }

        @Override
        public Status getStatus() {
            return status;
        }
    }

    public static enum Status {
        SPEED_UP, SLOW_DOWN, MAINTAIN
    }
}