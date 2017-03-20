/*
 *  Quirky Nimbus
 *
 *  Author: todd@wackford.net
 *  Date: 2014-02-22
 *
 *****************************************************************
 *     Setup Namespace, capabilities, attributes and commands
 *****************************************************************
 * Namespace:			"wackford"
 *
 * Capabilities:		"polling"
 *						"refresh"
 *
 * Custom Attributes:	"dial"
 *						"info"
 *
 * Custom Commands:		"none"
 *
 *****************************************************************
 *                       Changes
 *****************************************************************
 *
 *  Change 1:	2014-03-10
 *				Documented Header
 *
 *****************************************************************
 *                       Code
 *****************************************************************
 */
 // for the UI
metadata {
	// Automatically generated. Make future change here.
	definition (name: "Quirky Nimbus", namespace: "panealy", author: "todd@wackford.net", oauth: true) {
		capability "Refresh"
		capability "Polling"

		attribute "dial", "string"
		attribute "info", "string"
        
        command "dial"
	}


	tiles {
		standardTile("dial", "device.dial", width: 2, height: 2){
        	state "dial", label: '${currentValue}', icon:"st.custom.quirky.quirky-device"
        }      
        valueTile("info", "device.info", inactiveLabel: false, decoration: "flat") {
			state "info", label:'Dial is displaying ${currentValue}', unit:""
		}
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat") {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
	}
	main(["dial"])
    details(["dial","info","refresh" ])
}

preferences {
	input title: "", description: "Quirky Nimbus Preferences", displayDuringSetup: true, type: "paragraph", element: "paragraph"
    
    input title: "", description: "Logging", type: "paragraph", element: "paragraph"
    input name: "isLogLevelTrace", type: "bool", title: "Show trace log level ?\n", defaultValue: "false", displayDuringSetup: true
    input name: "isLogLevelDebug", type: "bool", title: "Show debug log level ?\n", defaultValue: "true", displayDuringSetup: true
}

// parse events into attributes
def parse(description) {
	log.debug "parse() - $description"
    
	if ( description == "updated" )
    	return

	def results = []

	if (description?.name && description?.value)
	{
		results << sendEvent(name: "${description?.name}", value: "${description?.value}")
	}
}

def uninstalled() {
	log.debug "Executing 'uninstall' in child"
    parent.uninstallChildDevice(this)
}

def poll() {
	log.debug "Nimbus executing 'pollNimbus'"
	parent.pollNimbus(this)
}

def refresh() {
	log.debug "Nimbus executing 'refresh'"
	parent.pollNimbus(this)
}