/*
 *  Lutron Pico Remote
 *
 *  Author: panealy@gmail.com
 *  Date: 2017-02-19
 *
 *****************************************************************
 *     Setup Namespace, capabilities, attributes and commands
 *****************************************************************
 * Namespace: "panealy"
 *
 * Capabilities:		
 *  "switch"
 *  "polling"
 *  "refresh"
 *
 * Custom Attributes:	
 *  "none"
 *
 * Custom Commands:
 *  "none"
 *
 *****************************************************************
 *                       Changes
 *****************************************************************
 *
 *  2017-02-25    Initial Creation
 *
 *****************************************************************
 *                       Code
 *****************************************************************
 */
 // for the UI
metadata {
	// Automatically generated. Make future change here.
	definition (name: "Lutron Pico Remote", namespace: "panealy", author: "panealy@gmail.com") {
		capability "Refresh"
		capability "Polling"
		capability "Switch"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles {
		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
			state "off", label: '${name}', action: "switch.on", icon: "st.switches.light.off", backgroundColor: "#ffffff"
            state "on", label: '${name}', action: "switch.off", icon: "st.switches.light.on", backgroundColor: "#79b821"	
		}
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat") {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
	}
	main(["switch"])
    details(["switch", "refresh" ])
}


// parse events into attributes
def parse(description) {
	log.debug "parse() - $description"
	def results = []
	
    if ( description == "updated" ) on initial install we are returned just a string
    	return
        
	if (description?.name && description?.value)
	{
		results << sendEvent(name: "${description?.name}", value: "${description?.value}")
	}
}


// handle commands
def on() {
	log.debug "Executing 'on'"
    log.debug this
	parent.on(this)
}

def off() {
	log.debug "Executing 'off'"
	parent.off(this)
}

def uninstalled() {
	log.debug "Executing 'uninstall' in child"
    parent.uninstallChildDevice(this)
}

def poll() {
	log.debug "Executing 'poll'"
	parent.pollRemote(this)
}

def refresh() {
	log.debug "Executing 'refresh'"
	parent.pollRemote(this)
}
