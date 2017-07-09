/*
 * Copyright 2016, 2017 Peter Doornbosch
 *
 * This file is part of JMeter-WebSocket-Samplers, a JMeter add-on for load-testing WebSocket applications.
 *
 * JMeter-WebSocket-Samplers is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * JMeter-WebSocket-Samplers is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.luminis.jmeter.wssampler;

import eu.luminis.websocket.Frame;
import eu.luminis.websocket.WebSocketClient;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.log.Logger;

import java.io.IOException;
import java.net.SocketTimeoutException;

/**
 * Base class for frame filters.
 * This class (and thus it's descendants) are not thread-safe: they don't have to, because JMeter accesses filters only
 * from one thread.
 */
public abstract class FrameFilter extends ConfigTestElement {

    private FrameFilter next;

    public FrameFilter() {
        super();
    }

    public Frame receiveFrame(WebSocketClient wsClient, int readTimeout, SampleResult result) throws IOException {
        prepareFilter();

        Frame receivedFrame;
        int socketTimeout = readTimeout;
        boolean matchesFilter;
        do {
            long start = System.currentTimeMillis();
            receivedFrame = next != null? next.receiveFrame(wsClient, socketTimeout, result): wsClient.receiveFrame(socketTimeout);
            long timeSpent = System.currentTimeMillis() - start;

            matchesFilter = matchesFilter(receivedFrame);
            if (matchesFilter) {
                getLogger().debug("Filter discards " + receivedFrame);
                performReplyAction(wsClient, receivedFrame);

                SampleResult subResult = new SampleResult();
                subResult.setSampleLabel("Discarded " + receivedFrame.getTypeAsString() + " frame (by filter '" + getName() + "')");
                subResult.setSuccessful(true);
                subResult.setResponseMessage("Received " + receivedFrame);
                result.addRawSubResult(subResult);
            }

            if (timeSpent < socketTimeout)
                socketTimeout -= timeSpent;
            else {
                // Time spent waiting for a valid frame (one that passed the filter) is now equal to original read timeout, so do not wait any longer.
                throw new SocketTimeoutException("Read timed out");
            }
        }
        while (matchesFilter);

        return receivedFrame;
    }

    protected void prepareFilter() {}

    abstract protected boolean matchesFilter(Frame receivedFrame);

    protected void performReplyAction(WebSocketClient wsClient, Frame receivedFrame) throws IOException {}

    @Override
    public boolean expectsModification() {
        return false;
    }

    abstract protected Logger getLogger();

    @Override
    public String toString() {
        return "Frame Filter '" + getName() + "'";
    }

    public void setNext(FrameFilter nextFilter) {
        if (nextFilter == this)
            getLogger().debug("Ignoring additional filter '" + nextFilter + "'; already present in chain.");
        else if (next == null)
            this.next = nextFilter;
        else
            next.setNext(nextFilter);
    }

    public String getChainAsString() {
        return toString() + (next != null? " -> " + next.getChainAsString(): "");
    }
}
