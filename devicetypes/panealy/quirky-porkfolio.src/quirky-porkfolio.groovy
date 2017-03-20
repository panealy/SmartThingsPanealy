/* Quirky Porkfolio
 *
 *  Author: todd@wackford.net
 *  Date: 2014-02-22
 *
 *****************************************************************
 *     Setup Namespace, acpabilities, attributes and commands
 *****************************************************************
 * Namespace:			"wackford"
 *
 * Capabilities:		"acceleration"
 *						"battery"
 *						"polling"
 *						"refresh"
 *
 * Custom Attributes:	"balance"
 *						"goal"
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
 *  Change 2:	2014-09-30
 *				Added child device uninstall call to parent
 *
 *****************************************************************
 *                       Code
 *****************************************************************
 */
metadata {
	// Automatically generated. Make future change here.
	definition (name: "Quirky Porkfolio", namespace: "panealy", author: "todd@wackford.net", oauth: true) {
		capability "Refresh"
		capability "Polling"
        capability "Acceleration Sensor"
        capability "Configuration"

		attribute "nose_color", "string"
		attribute "balance", "string"
		attribute "goal", "string"
	}

	tiles {
    	standardTile("acceleration", "device.accelerationSensor", width: 2, height: 2, canChangeIcon: true) {
        	state "inactive", label:'pig secure', icon:"st.quirky.porkfolio.quirky-porkfolio-side"//, backgroundColor:"#44b621"
			state "active", label:'pig alarm', icon:"st.quirky.porkfolio.quirky-porkfolio-dead", backgroundColor:"#FF1919"	
		}
        standardTile("balance", "device.balance", inactiveLabel: false, canChangeIcon: true) {
			state "balance", label:'${currentValue}', unit:"", icon:"st.quirky.porkfolio.quirky-porkfolio-facing"
		}
        standardTile("goal", "device.goal", inactiveLabel: false, decoration: "flat", canChangeIcon: true) {
			state "goal", label:'${currentValue} goal', unit:"", icon:"st.quirky.porkfolio.quirky-porkfolio-success"
		}
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat") {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        standardTile("configure", "device.power", inactiveLabel: false, decoration: "flat") {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}
	}
	main(["acceleration", "balance"])
    details(["acceleration", "balance", "goal", "refresh", "configure" ])
}

preferences {
	input title: "", description: "Quirky Porkfolio Preferences", displayDuringSetup: true, type: "paragraph", element: "paragraph"
    
    input name: "nose_color", type: "enum", title: "Porkfolio nose color:", description: "Color", required: true, options:["Green","Red","Yellow","Purple","Blue"]
    
    input title: "", description: "Logging", type: "paragraph", element: "paragraph"
    input name: "isLogLevelTrace", type: "bool", title: "Show trace log level ?\n", defaultValue: "false", displayDuringSetup: true
    input name: "isLogLevelDebug", type: "bool", title: "Show debug log level ?\n", defaultValue: "true", displayDuringSetup: true
}

// parse events into attributes
def parse(description) {
	log.debug "parse() - $description"
	def results = []
    
    if ( description == "updated" ) // on initial install we are returned just a string
    	return

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
	log.debug "Executing 'poll'"
	parent.getPiggyBankUpdate(this)
}

def refresh() {
	log.debug "Executing 'refresh'"
	parent.getPiggyBankUpdate(this)
}

 /**
 *  configure - Configures the parameters of the device
 *
 *  Required for the "Configuration" capability
 */
def configure() {
	log.debug "configure() ${nose_color}"
    
    // TODO more robust in future - only set color now
    //  - Unfortunately while this works as far as sending the command and updating
    //    desired_state on the server, it isn't clear it actually successfully updates the color. Why, isn't clear.
    log.debug "Set Color to ${nose_color}"
    try {
    	parent.configurePorkfolio(this, "${nose_color}")
    } catch (e) {
    	log.error("caught exception", e)
    }
}