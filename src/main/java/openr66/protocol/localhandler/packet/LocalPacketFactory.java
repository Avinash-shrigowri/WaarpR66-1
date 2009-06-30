/**
 * Copyright 2009, Frederic Bregier, and individual contributors by the @author
 * tags. See the COPYRIGHT.txt in the distribution for a full listing of
 * individual contributors. This is free software; you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3.0 of the License,
 * or (at your option) any later version. This software is distributed in the
 * hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU Lesser General Public License for more details. You should have
 * received a copy of the GNU Lesser General Public License along with this
 * software; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF site:
 * http://www.fsf.org.
 */
package openr66.protocol.localhandler.packet;

import openr66.protocol.exception.OpenR66ProtocolPacketException;
import openr66.protocol.exception.OpenR66ProtocolShutdownException;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Factory to create Packet according to type from a buffer
 * 
 * @author Frederic Bregier
 */
public class LocalPacketFactory {
    public static final byte TESTPACKET = 0;
    public static final byte ERRORPACKET = 1;
    public static final byte SHUTDOWNPACKET = 2;
    public static final byte REQUESTPACKET = 3;
    public static final byte SENDPACKET = 4;
    public static final byte RECVPACKET = 5;
    public static final byte STATUSPACKET = 6;
    public static final byte CANCELPACKET = 7;
    public static final byte CONFIGSENDPACKET = 8;
    public static final byte CONFIGRECVPACKET = 9;

    /**
     * This method create a Packet from the ChannelBuffer.
     * 
     * @param headerLength
     *            length of the header from the current position of the buffer
     * @param middleLength
     * @param endLength
     * @param buf
     * @return the newly created Packet
     * @throws OpenR66ProtocolPacketException
     * @throws OpenR66ProtocolShutdownException
     */
    public static AbstractLocalPacket createPacketFromChannelBuffer(
            int headerLength, int middleLength, int endLength, ChannelBuffer buf)
            throws OpenR66ProtocolPacketException,
            OpenR66ProtocolShutdownException {
        final byte packetType = buf.readByte();
        switch (packetType) {
        case TESTPACKET: {
            return TestPacket.createFromBuffer(headerLength, middleLength, endLength, buf);
        }
        case ERRORPACKET: {
            return ErrorPacket.createFromBuffer(headerLength, middleLength, endLength, buf);
        }
        case SHUTDOWNPACKET: {
            ShutdownPacket packet = ShutdownPacket.createFromBuffer(headerLength, middleLength, endLength, buf);
            if (packet.isShutdownValid()) {
                throw new OpenR66ProtocolShutdownException("Shutdown Type received");                
            }
            throw new OpenR66ProtocolPacketException("Invalid Shutdown command");
        }
        default:
            throw new OpenR66ProtocolPacketException(
                    "Unvalid Packet Type received: " + packetType);
        }
    }
}