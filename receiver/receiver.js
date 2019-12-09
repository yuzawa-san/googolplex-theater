const GoogolplexTheater = {
	init: (listener) => {
		const sdkScript = document.createElement("SCRIPT");
		sdkScript.type = "text/javascript";
		sdkScript.src = "//www.gstatic.com/cast/sdk/libs/caf_receiver/v3/cast_receiver_framework.js";
		sdkScript.onload = () => {
			const context = cast.framework.CastReceiverContext.getInstance();
			const options = new cast.framework.CastReceiverOptions();
			options.disableIdleTimeout = true;
			const NAMESPACE_CUSTOM = "urn:x-cast:com.jyuzawa.googolplex-theater.device";
			const sendMessage = (message) => {
				context.sendCustomMessage(NAMESPACE_CUSTOM, undefined, message);
			};
			context.addCustomMessageListener(NAMESPACE_CUSTOM, customEvent => {
				console.log("MESSAGE", customEvent);
				if (customEvent.type === "message") {
					listener(customEvent.data.device, sendMessage);
				}
			});
			context.addEventListener(cast.framework.system.EventType.SENDER_DISCONNECTED, ev => window.close());
			context.start(options);
		}
		document.body.appendChild(sdkScript);
	}
};