// ** AUTO-UI IMPORT STATEMENTS **
import org.red5.utils.Connector;
// ** END AUTO-UI IMPORT STATEMENTS **
import com.neoarchaic.ui.Tooltip;
import org.red5.net.Stream;
import org.red5.utils.Delegate;
import com.gskinner.events.GDispatcher;

class org.red5.samples.livestream.broadcaster.Main extends MovieClip {
// Constants:
	public static var CLASS_REF = org.red5.samples.livestream.broadcaster.Main;
	public static var LINKAGE_ID:String = "org.red5.samples.livestream.broadcaster.Main";
// Public Properties:
	public var addEventListener:Function;
	public var removeEventListener:Function;
// Private Properties:
	private var dispatchEvent:Function;
	private var stream:Stream;
	private var cam:Camera;
	private var mic:Microphone;
	private var controller:MovieClip;
// UI Elements:

// ** AUTO-UI ELEMENTS **
	private var connector:Connector;
	private var public_video:MovieClip;
// ** END AUTO-UI ELEMENTS **

// Initialization:
	private function Main() {GDispatcher.initialize(this);}
	private function onLoad():Void { configUI(); }

// Public Methods:
	public function registerController(p_controller:MovieClip):Void
	{
		controller = p_controller;
	}
// Semi-Private Methods:
// Private Methods:
	private function configUI():Void 
	{
		// setup the tooltip defaults
		Tooltip.options = {size:10, font:"_sans", corner:0};
		
		// setup cam
		cam = Camera.get();
		cam.setMode(160, 110, 8);
		cam.setQuality(0,80);
		
		// setup mic
		mic = Microphone.get();
		
		// get notified of connection changes
		connector.addEventListener("onSetID", this);
		connector.addEventListener("connectionChange", this);
		connector.addEventListener("newStream", Delegate.create(controller, controller.newStream));
		
		// set the uri
		Connector.red5URI = "rtmp://fancycode.com/fitcDemo";
		
		var live:Boolean = _level0._url.indexOf("http://") > -1 ? true : false;
		/*
		* if(live) Connector.red5URI = "rtmp://67.78.20.202/fitcDemo";
		*/
		if(live) Connector.red5URI = "rtmp://fancycode.com/fitcDemo";
		if(!live) Connector.red5URI = "rtmp://192.168.1.2/fitcDemo";
		
		
		// initialize the connector
		connector.configUI();	
	}
	
	private function status(evtObj:Object):Void
	{
		// deal with the status messages here
	}
	
	private function error(evtObj:Object):Void
	{
		// deal with the errors here
	}
	
	private function onSetID(evtObj:Object):Void
	{
		// setup stream
		stream = new Stream(connector.connection);
		// add stream status events listeners here
		stream.addEventListener("status", Delegate.create(this, status));
		stream.addEventListener("error", Delegate.create(this, error));
		// attach camera
		stream.attachVideo(cam);
		// add audio
		stream.attachAudio(mic);
		stream.publish("videoStream_" + evtObj.id, "live");
		// show it on screen
		public_video.video.attachVideo(cam);
		
		controller.setID(evtObj.id, connector.connection);
		//dispatchEvent({type:"onSetID", id:evtObj.id})
	}	
	
	private function connectionChange(evtObj:Object):Void
	{
		if(evtObj.connected) 
		{
			dispatchEvent({type:"connected", connection:connector.connection});
		}else
		{
			public_video.video.attachVideo(null);
			public_video.video.clear();
			dispatchEvent({type:"disconnected", connection:connector.connection});
		}
	}

}