/*
 * RED5 Open Source Flash Server - http://code.google.com/p/red5/
 * 
 * Copyright 2006-2013 by respective authors (see below). All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.red5.server.net.rtmpt;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.mina.core.buffer.IoBuffer;
import org.red5.server.net.rtmp.IRTMPHandler;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.codec.RTMP;
import org.red5.server.net.rtmp.codec.RTMPProtocolDecoder;
import org.red5.server.net.rtmp.codec.RTMPProtocolEncoder;
import org.red5.server.net.rtmp.message.Packet;
import org.red5.server.net.rtmpt.codec.RTMPTProtocolDecoder;
import org.red5.server.net.rtmpt.codec.RTMPTProtocolEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base RTMPT client / session.
 * 
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public abstract class BaseRTMPTConnection extends RTMPConnection {

	private static final Logger log = LoggerFactory.getLogger(BaseRTMPTConnection.class);

	/**
	 * Protocol decoder
	 */
	private RTMPTProtocolDecoder decoder;

	/**
	 * Protocol encoder
	 */
	private RTMPTProtocolEncoder encoder;

	/**
	 * Closing flag
	 */
	private volatile boolean closing;

	/**
	 * Number of read bytes
	 */
	private AtomicLong readBytes = new AtomicLong(0);

	/**
	 * Number of written bytes
	 */
	private AtomicLong writtenBytes = new AtomicLong(0);

	/**
	 * Byte buffer
	 */
	private volatile IoBuffer buffer;

	/**
	 * RTMP events handler
	 */
	protected IRTMPHandler handler;

	/**
	 * List of pending incoming messages
	 */
	protected volatile LinkedBlockingQueue<Object> pendingInMessages = new LinkedBlockingQueue<Object>();

	/**
	 * List of pending outgoing messages
	 */
	protected volatile LinkedBlockingQueue<PendingData> pendingOutMessages = new LinkedBlockingQueue<PendingData>();

	/**
	 * Maximum incoming messages to process at a time per client
	 */
	protected int maxInMessagesPerProcess = 16;

	/**
	 * Maximum amount of time in milliseconds to wait before allowing an offer to fail
	 */
	protected long maxQueueOfferTime = 500L;

	/**
	 * Maximum offer attempts before failing on incoming or outgoing queues
	 */
	protected int maxQueueOfferAttempts = 4;

	public BaseRTMPTConnection(String type) {
		super(type);
		this.buffer = IoBuffer.allocate(0).setAutoExpand(true);
	}

	/**
	 * Return any pending messages up to a given size.
	 *
	 * @param targetSize the size the resulting buffer should have
	 * @return a buffer containing the data to send or null if no messages are pending
	 */
	abstract public IoBuffer getPendingMessages(int targetSize);

	/** {@inheritDoc} */
	@Override
	public void close() {
		log.debug("close - state: {}", state.getState());
		// defer actual closing so we can send back pending messages to the client
		closing = true;
	}

	/**
	 * Getter for property 'closing'.
	 *
	 * @return Value for property 'closing'.
	 */
	public boolean isClosing() {
		return closing;
	}

	/**
	 * Real close
	 */
	public void realClose() {
		log.debug("realClose - isClosing: {}", isClosing());
		if (isClosing()) {
			state.setState(RTMP.STATE_DISCONNECTED);
			log.trace("Clearing pending messages (in: {} and out: {})", pendingInMessages.size(), pendingOutMessages.size());
			pendingInMessages.clear();
			pendingOutMessages.clear();
			// clean up buffer
			if (buffer != null) {
				buffer.free();
				buffer = null;
			}
			super.close();
		}
	}

	/** {@inheritDoc} */
	@Override
	public long getReadBytes() {
		return readBytes.get();
	}

	/** {@inheritDoc} */
	@Override
	public long getWrittenBytes() {
		return writtenBytes.get();
	}

	/** {@inheritDoc} */
	@Override
	public long getPendingMessages() {
		log.debug("Checking pending queue size. Session id: {} closing: {} state: {}", sessionId, closing, state);
		if (state.getState() == RTMP.STATE_DISCONNECTED) {
			log.debug("Connection is disconnected");
			pendingOutMessages.clear();
		}
		return pendingOutMessages.size();
	}

	/**
	 * Decode data sent by the client.
	 *
	 * @param data the data to decode
	 * @return a list of decoded objects
	 */
	public List<?> decode(IoBuffer data) {
		log.debug("decode");
		if (closing || state.getState() == RTMP.STATE_DISCONNECTED) {
			// connection is being closed, don't decode any new packets
			return Collections.EMPTY_LIST;
		}
		long read = readBytes.addAndGet(data.limit());
		log.trace("Current bytes read at decode: {}", read);
		buffer.put(data);
		buffer.flip();
		return decoder.decodeBuffer(state, buffer);
	}

	/**
	 * Receive a collection of RTMP messages.
	 * 
	 * @param messages
	 */
	public void read(final List<?> messages) {
		log.debug("read - messages: {}", messages.size());
		for (Object message : messages) {
			if (message instanceof Packet) {
				read((Packet) message);
			} else {
				read((IoBuffer) message);
			}
		}
	}

	/**
	 * Receive RTMP IoBuffer from the connection.
	 *
	 * @param ioBuffer the I/O buffer to receive
	 */
	public void read(final IoBuffer ioBuffer) {
		log.debug("read - ioBuffer: {}", ioBuffer);
		if (closing || state.getState() == RTMP.STATE_DISCONNECTED) {
			// connection is being closed, don't receive any new packets
			log.debug("No read completed due to connection disconnecting");
		} else {
			// add to pending
			log.debug("Adding incoming message ioBuffer");
			try {
				int attempt = 0;
				while (!pendingInMessages.offer(ioBuffer, maxQueueOfferTime, TimeUnit.MILLISECONDS)) {
					log.trace("IoBuffer was not added to in queue");
					attempt++;
					if (attempt >= maxQueueOfferAttempts) {
						break;
					}
				}
			} catch (InterruptedException ex) {
				log.warn("Offering io buffer to in queue failed", ex);
			}
		}
	}

	/**
	 * Receive RTMP packet from the connection.
	 *
	 * @param packet the packet to receive
	 */
	public void read(final Packet packet) {
		log.debug("read - packet: {}", packet);
		if (closing || state.getState() == RTMP.STATE_DISCONNECTED) {
			// connection is being closed, don't receive any new packets
			log.debug("No read completed due to connection disconnecting");
		} else {
			// add to pending
			log.debug("Adding incoming message packet");
			try {
				int attempt = 0;
				while (!pendingInMessages.offer(packet, maxQueueOfferTime, TimeUnit.MILLISECONDS)) {
					log.trace("Packet was not added to in queue");
					attempt++;
					if (attempt >= maxQueueOfferAttempts) {
						break;
					}
				}
			} catch (InterruptedException ex) {
				log.warn("Offering packet to in queue failed", ex);
			}
		}
	}

	/**
	 * Send RTMP packet down the connection.
	 *
	 * @param packet the packet to send
	 */
	@Override
	public void write(final Packet packet) {
		log.debug("write - packet: {}", packet);
		//log.trace("state: {}", state);
		if (closing || state.getState() == RTMP.STATE_DISCONNECTED) {
			// connection is being closed, don't send any new packets
			log.debug("No write completed due to connection disconnecting");
		} else {
			IoBuffer data = null;
			try {
				data = encoder.encodePacket(state, packet);
				if (data != null) {
					// add to pending
					log.debug("Adding outgoing message packet");
					PendingData pendingData = new PendingData(data, packet);
					try {
						int attempt = 0;
						while (!pendingOutMessages.offer(pendingData, maxQueueOfferTime, TimeUnit.MILLISECONDS)) {
							log.trace("Packet was not added to out queue");
							attempt++;
							if (attempt >= maxQueueOfferAttempts) {
								break;
							}
						}
					} catch (InterruptedException ex) {
						log.warn("Offering packet to out queue failed", ex);
					}
				} else {
					log.warn("Response buffer was null after encoding");
				}
			} catch (Exception e) {
				log.error("Could not encode message {}", packet, e);
			}
		}
	}

	/**
	 * Send raw data down the connection.
	 *
	 * @param packet the buffer containing the raw data
	 */
	@Override
	public void writeRaw(IoBuffer packet) {
		log.debug("write - io buffer: {}", packet);
		PendingData pendingData = new PendingData(packet);
		try {
			int attempt = 0;
			while (!pendingOutMessages.offer(pendingData, maxQueueOfferTime, TimeUnit.MILLISECONDS)) {
				log.trace("Packet was not added to out queue");
				attempt++;
				if (attempt >= maxQueueOfferAttempts) {
					break;
				}
			}
		} catch (InterruptedException ex) {
			log.warn("Offering io buffer to out queue failed", ex);
		}
	}

	protected IoBuffer foldPendingMessages(int targetSize) {
		log.debug("foldPendingMessages - target size: {}", targetSize);
		IoBuffer result = null;
		if (!pendingOutMessages.isEmpty()) {
			int available = pendingOutMessages.size();
			// create list to hold outgoing data
			LinkedList<PendingData> sendList = new LinkedList<PendingData>();
			pendingOutMessages.drainTo(sendList, Math.min(164, available));
			result = IoBuffer.allocate(targetSize).setAutoExpand(true);
			for (PendingData pendingMessage : sendList) {
				result.put(pendingMessage.getBuffer());
				Packet packet = pendingMessage.getPacket();
				if (packet != null) {
					try {
						handler.messageSent(this, packet);
						// mark packet as being written
						writingMessage(packet);
					} catch (Exception e) {
						log.error("Could not notify stream subsystem about sent message", e);
					}
				} else {
					log.trace("Pending message did not have a packet");
				}
			}
			sendList.clear();
			result.flip();
			// send byte length
			int sendSize = result.limit();
			log.debug("Send size: {}", sendSize);
			writtenBytes.addAndGet(sendSize);
		}
		return result;
	}

	public void setHandler(IRTMPHandler handler) {
		this.handler = handler;
	}

	public void setDecoder(RTMPProtocolDecoder decoder) {
		this.decoder = (RTMPTProtocolDecoder) decoder;
	}

	public void setEncoder(RTMPProtocolEncoder encoder) {
		this.encoder = (RTMPTProtocolEncoder) encoder;
		this.encoder.setConnection(this);
	}

	/**
	 * @param maxInMessagesPerProcess the maxInMessagesPerProcess to set
	 */
	public void setMaxInMessagesPerProcess(int maxInMessagesPerProcess) {
		this.maxInMessagesPerProcess = maxInMessagesPerProcess;
	}

	/**
	 * @param maxQueueOfferTime the maxQueueOfferTime to set
	 */
	public void setMaxQueueOfferTime(long maxQueueOfferTime) {
		this.maxQueueOfferTime = maxQueueOfferTime;
	}

	/**
	 * @param maxQueueOfferAttempts the maxQueueOfferAttempts to set
	 */
	public void setMaxQueueOfferAttempts(int maxQueueOfferAttempts) {
		this.maxQueueOfferAttempts = maxQueueOfferAttempts;
	}

	/**
	 * Holder for data destined for a requester that is not ready to be sent.
	 */
	private static class PendingData {

		// simple packet
		private final Packet packet;

		// encoded packet data
		private final byte[] byteBuffer;

		private PendingData(IoBuffer buffer, Packet packet) {
			int size = buffer.limit();
			this.byteBuffer = new byte[size];
			buffer.get(byteBuffer);
			this.packet = packet;
			if (log.isTraceEnabled()) {
				log.trace("Buffer: {}", Arrays.toString(ArrayUtils.subarray(byteBuffer, 0, 32)));
			}
		}

		private PendingData(IoBuffer buffer) {
			int size = buffer.limit();
			this.byteBuffer = new byte[size];
			buffer.get(byteBuffer);
			this.packet = null;
			if (log.isTraceEnabled()) {
				log.trace("Buffer: {}", Arrays.toString(ArrayUtils.subarray(byteBuffer, 0, 32)));
			}
		}

		public byte[] getBuffer() {
			if (log.isTraceEnabled()) {
				log.trace("Get buffer: {}", Arrays.toString(ArrayUtils.subarray(byteBuffer, 0, 32)));
			}
			return byteBuffer;
		}

		public Packet getPacket() {
			return packet;
		}

		@SuppressWarnings("unused")
		public int getBufferSize() {
			if (byteBuffer != null) {
				return byteBuffer.length;
			}
			return 0;
		}

	}

}
