package org.red5.server.stream;

import java.io.File;
import java.io.IOException;

import org.red5.io.IStreamableFile;
import org.red5.io.IStreamableFileService;
import org.red5.io.ITagWriter;
import org.red5.server.api.IConnection;
import org.red5.server.api.IScope;
import org.red5.server.api.Red5;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IOnDemandStream;
import org.red5.server.api.stream.IStream;
import org.red5.server.api.stream.IStreamCapableConnection;
import org.red5.server.api.stream.IStreamService;
import org.red5.server.api.stream.ISubscriberStream;
import org.red5.server.messaging.InMemoryPullPullPipe;
import org.red5.server.messaging.InMemoryPushPushPipe;
import org.red5.server.messaging.PipeUtils;
import org.red5.server.net.rtmp.RTMPHandler;
import org.red5.server.stream.consumer.ConnectionConsumer;
import org.red5.server.stream.filter.StreamBandwidthController;
import org.red5.server.stream.provider.FileProvider;
import org.springframework.core.io.Resource;

public class StreamService extends StreamManager
		implements IStreamService {

	private int getCurrentStreamId() {
		// TODO: this must come from the current connection!
		return RTMPHandler.getStreamId()-1;
	}
	
	public int createStream() {
		IConnection conn = Red5.getConnectionLocal();
		if (!(conn instanceof IStreamCapableConnection))
			return -1;
		
		// Flash starts with 1
		return ((IStreamCapableConnection) conn).reserveStreamId() + 1;
	}

	public void deleteStream(int number) {
		IConnection conn = Red5.getConnectionLocal();
		if (!(conn instanceof IStreamCapableConnection))
			return;
		
		deleteStream((IStreamCapableConnection) conn, number);
	}
	
	public void deleteStream(IStreamCapableConnection conn, int number) {
		IStream stream = conn.getStreamById(number);
		if (stream == null) {
			// XXX: invalid request, we must return an error to the client here...
		}
		conn.deleteStreamById(number);
		log.debug("Delete stream: "+stream+" number: "+number);
		deleteStream(((IConnection) conn).getScope(), stream);
	}
	
	public void play(String name) {
		play(name, new Double(-2000.0), -1, false);

	}

	public void play(String name, Double type) {
		play(name, type, -1, false);

	}

	public void play(String name, Double type, int length) {
		play(name, type, length, false);
	}

	public void play(String name, Double type, int length,
			boolean flushPlaylist) {
		IConnection conn = Red5.getConnectionLocal();
		if (!(conn instanceof IStreamCapableConnection))
			return;

		IScope scope = conn.getScope();
		int streamId = getCurrentStreamId();
		
		// it seems as if the number is sent multiplied by 1000
		int num = (int)(type.doubleValue() / 1000.0);
		if (num < -2)
			num = -2;

		boolean isPublishedStream = hasBroadcastStream(scope, name);
		boolean isFileStream = hasOnDemandStream(scope, name);
		
		// decision: 0 for Live, 1 for File, 2 for Wait, 3 for N/A
		int decision = 3;
		
		switch (num) {
		case -2:
			if (isPublishedStream) {
				decision = 0;
			} else if (isFileStream) {
				decision = 1;
			} else {
				decision = 2;
			}
			break;
			
		case -1:
			if (isPublishedStream) {
				decision = 0;
			} else {
				// TODO: Wait for stream to be created until timeout, otherwise continue
				// with next item in playlist (see Macromedia documentation)
				// NOTE: For now we create a temporary stream
				decision = 2;
			}
			break;
			
		default:
			if (isFileStream) {
				decision = 1;
			} else {
				// TODO: Wait for it, then continue with next item in playlist (?)
			}
			break;
		}
		
		ISubscriberStream subscriber;
		IOnDemandStream onDemand;
		switch (decision) {
		case 0:
			subscriber = ((IStreamCapableConnection) conn).newSubscriberStream(name, streamId);
			subscriber.start(0, length);
			break;
			
		case 1:
			onDemand = ((IStreamCapableConnection) conn).newOnDemandStream(name, streamId);
			// TODO: initial seeking?
			onDemand.play(length);
			break;
			
		case 2:
			subscriber = ((IStreamCapableConnection) conn).newSubscriberStream(name, streamId);
			subscriber.start(0, length);
			break;
		}
	}

	public void publish(String name) {
		publish(name, Stream.MODE_LIVE);
	}
	
	public void publish(String name, String mode) {
		IConnection conn = Red5.getConnectionLocal();
		if (!(conn instanceof IStreamCapableConnection))
			return;
		
		IBroadcastStream stream = ((IStreamCapableConnection) conn).newBroadcastStream(name, getCurrentStreamId());
		if (mode.equals(Stream.MODE_LIVE))
			// Nothing more to do
			return;
		
		// Now the mode is either MODE_RECORD or MODE_APPEND
		IScope scope = conn.getScope();
		try {				
			Resource res = scope.getResource(getStreamFilename(name, ".flv"));
			if (mode.equals(Stream.MODE_RECORD) && res.exists()) 
				res.getFile().delete();
			
			if (!res.exists())
				res = scope.getResource(getStreamDirectory()).createRelative(name + ".flv");
			
			if (!res.exists())
				res.getFile().createNewFile();
			
			File file = res.getFile();
			IStreamableFileService service = getFileFactory(scope).getService(file);
			IStreamableFile flv = service.getStreamableFile(file);
			ITagWriter writer = null; 
			if (mode.equals(Stream.MODE_RECORD)) 
				writer = flv.getWriter();
			else if (mode.equals(Stream.MODE_APPEND))
				writer = flv.getAppendWriter();
			stream.subscribe(new FileStreamSink(scope, writer));
		} catch (IOException e) {
			log.error("Error recording stream: " + stream, e);
		}
	}

	public void publish(boolean dontStop) {
		if (dontStop)
			return;
		
		IConnection conn = Red5.getConnectionLocal();
		if (!(conn instanceof IStreamCapableConnection))
			return;
		
		IStream stream = ((IStreamCapableConnection) conn).getStreamById(getCurrentStreamId()+1);
		stream.close();
	}
	
	public void closeStream() {
		log.debug("Closing stream");
		IConnection conn = Red5.getConnectionLocal();
		if (!(conn instanceof IStreamCapableConnection))
			return;
		
		IStream stream = ((IStreamCapableConnection) conn).getStreamById(getCurrentStreamId()+1);
		stream.close();
	}
	
	public void seek(int position) {
		IConnection conn = Red5.getConnectionLocal();
		if (!(conn instanceof IStreamCapableConnection))
			return;
		
		IStream stream = ((IStreamCapableConnection) conn).getStreamById(getCurrentStreamId()+1);
		if (stream instanceof IOnDemandStream)
			((IOnDemandStream) stream).seek(position);
	}
	
	public void pause(boolean resumePlayback, int position) {
		IConnection conn = Red5.getConnectionLocal();
		if (!(conn instanceof IStreamCapableConnection))
			return;
		
		IStream stream = ((IStreamCapableConnection) conn).getStreamById(getCurrentStreamId()+1);
		if (stream instanceof IOnDemandStream) {
			IOnDemandStream ods = (IOnDemandStream) stream;
			if (resumePlayback) {
				ods.seek(position);
				ods.resume();
			} else
				ods.pause();
		}
	}
}
