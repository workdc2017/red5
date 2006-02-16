package org.red5.server.context;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.red5.server.SharedObjectPersistence;
import org.red5.server.net.rtmp.Connection;
import org.red5.server.net.rtmp.message.Ping;
import org.red5.server.net.rtmp.status.StatusObject;
import org.red5.server.net.rtmp.status.StatusObjectService;
import org.red5.server.stream.IStreamSource;
import org.red5.server.stream.Stream;
import org.red5.server.stream.StreamManager;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class BaseApplication 
	implements ApplicationContextAware, BeanPostProcessor {

	//private StatusObjectService statusObjectService = null;
	private ApplicationContext appCtx = null;
	private HashSet clients = new HashSet();
	private StreamManager streamManager = null;
	private SharedObjectPersistence soPersistence = null;
	private HashSet listeners = new HashSet();
	
	protected static Log log =
        LogFactory.getLog(BaseApplication.class.getName());
	
	public void setApplicationContext(ApplicationContext appCtx){
		this.appCtx = appCtx;
	}
	
	public void setStreamManager(StreamManager streamManager){
		this.streamManager = streamManager;
	}
	
	public void setSharedObjectPersistence(SharedObjectPersistence soPersistence) {
		this.soPersistence = soPersistence;
	}
	
	
	/*
	public void setStatusObjectService(StatusObjectService statusObjectService){
		this.statusObjectService = this.statusObjectService;
	}
	*/
	
	private StatusObject getStatus(String statusCode){
		// TODO: fix this, getting the status service out of the thread scope is a hack
		final StatusObjectService statusObjectService = Scope.getStatusObjectService();
		return statusObjectService.getStatusObject(statusCode);
	}
	
	public final void initialize(){
		log.debug("Calling onAppStart");
		onAppStart();
	}
	
	public final StatusObject connect(List params){
		final Client client = Scope.getClient();
		log.debug("Calling onConnect");
		if(onConnect(client, params)){
			clients.add(client);
			Connection conn = (Connection) client;
			Ping ping = new Ping();
			ping.setValue1((short)0);
			ping.setValue2(0);
			conn.ping(ping);
			return getStatus(StatusObjectService.NC_CONNECT_SUCCESS);
		} else {
			return getStatus(StatusObjectService.NC_CONNECT_REJECTED);
		}
	}
	
	public final void disconnect(){
		final Client client = Scope.getClient();
		clients.remove(client);
		if (this.soPersistence != null) {
			// Unregister client from shared objects
			Iterator it = this.soPersistence.getSharedObjects();
			while (it.hasNext()) {
				PersistentSharedObject so = (PersistentSharedObject) it.next();
				so.unregisterClient(client);
			}
		}
		log.info("Calling onDisconnect");
		onDisconnect(client);
	}
	
	// -----------------------------------------------------------------------------
	
	public int createStream(){
		// i think this is to say if the user is allowed to create a stream
		// if it returns 0 the play call will not come through
		// any number higher than 0 seems to do the same thing
		return 1; 
	}
	
	public void play(String name){
		 play(name, -1); // not sure what the number does
	}
	
	public void play(String name, int number){
		final Stream stream = Scope.getStream();
		stream.setName(name);
		log.debug("play: "+name);
		log.debug("stream: "+stream);
		log.debug("number:"+number);
		if(streamManager.isPublishedStream(name)){
			streamManager.connectToPublishedStream(stream);
			stream.start();
		} else {
			final IStreamSource source = streamManager.lookupStreamSource(name);
			log.debug(source);
			stream.setSource(source);
			
			//Integer.MAX_VALUE;
			//stream.setNSId();
			stream.start();
		}
		//streamManager.play(stream, name);
		//return getStatus(StatusObjectService.NS_PLAY_START);
	}
	
	public StatusObject publish(String name, String mode){
		final Stream stream = Scope.getStream();
		stream.setName(name);
		streamManager.publishStream(stream);
		stream.publish();
		// register the name with the stream manager	
		log.debug("publish: "+name);
		log.debug("stream: "+stream);
		log.debug("mode:"+mode);
		return getStatus(StatusObjectService.NS_PUBLISH_START);
	}
	
	
	public void pause(boolean pause, int time){
		log.info("Pause called: "+pause+" true:"+time);
		final Stream stream = Scope.getStream();
		if(pause) stream.pause();
		else stream.resume();
	}
	
	public void deleteStream(){
		// unpublish ?
	}
	
	public void closeStream(){
		final Stream stream = Scope.getStream();
		stream.stop();
	}
	// publishStream ?
	
	// -----------------------------------------------------------------------------
	
	public void onAppStart(){
		
	}
	
	public boolean onConnect(Client conn, List params){
		// always ok, override
		return true;
	}
	
	public void onDisconnect(Client conn){
		Iterator it = listeners.iterator();
		while(it.hasNext()){
			AppLifecycleAware el = (AppLifecycleAware) it.next();
			el.onDisconnect(conn);
		}
	}

	public Object postProcessBeforeInitialization(Object bean, String name) throws BeansException {
		if(bean instanceof AppLifecycleAware){
			listeners.add(bean);
		}
		return bean;
	}
	
	public Object postProcessAfterInitialization(Object bean, String name) throws BeansException {
		// not needed
		return bean;
	}
	
	// -----------------------------------------------------------------------------
	
	public PersistentSharedObject getSharedObject(String name) {
		if (this.soPersistence == null) {
			// XXX: maybe we should thow an exception here as a non-persistent SO doesn't make any sense...
			return new PersistentSharedObject(name, null);
		}
		
		PersistentSharedObject result = this.soPersistence.loadSharedObject(name);
		if (result == null) {
			// Create new shared object with given name
			log.info("Creating new shared object " + name);
			result = new PersistentSharedObject(name, this.soPersistence);
		}
		
		return result;
	}
	
}
