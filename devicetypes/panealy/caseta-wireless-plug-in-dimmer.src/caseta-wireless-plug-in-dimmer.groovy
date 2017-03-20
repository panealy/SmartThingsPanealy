/*
 *  Caseta Wireless Plug-in Dimmer
 *
 *  Author: panealy@gmail.com
 *  Date: 2017-02-25
 *
 *****************************************************************
 *     Setup Namespace, capabilities, attributes and commands
 *****************************************************************
 * Namespace: "panealy"
 *
 * Capabilities:		
 *  "switchLevel"
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
 *  2017-02-19    Initial Creation
 *
 *****************************************************************
 *                       Code
 *****************************************************************
 */
 // for the UI
metadata {
	// Automatically generated. Make future change here.
	definition (name: "Caseta Wireless Plug-in Dimmer", namespace: "panealy", author: "panealy@gmail.com") {
		capability "Refresh"
		capability "Polling"
        capability "Switch"
		capability "Switch Level"
        command "levelUp"
        command "levelDown"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles {
        standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true, canChangeBackground: true) {
			state "off", label: '${name}', action: "switch.on", icon: "st.switches.light.off", backgroundColor: "#ffffff", nextState: "turning_on"
            state "on", label: '${name}', action: "switch.off", icon: "st.switches.light.on", backgroundColor: "#79b821", nextState: "turning_off"
            state "turning_on", label: '${name}', icon: 'st.switches.switch.on', backgroundColor:"#79b821"
            state "turning_off", label: '${name}', icon: 'st.switches.switch.off', backgroundColor:"#ffffff"
		}
        controlTile("level_slider_control", "device.level", "slider", height: 1, width: 2, inactiveLabel: false) {
                state 'level', action: 'switch level.setLevel'
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat") {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        valueTile('level_value', 'device.level', inactiveLabel: true, height: 1, width: 1, decoration: 'flat') {
            state 'levelValue', label: '${currentValue}%', unit:"", backgroundColor: "#53a7c0"
        }
        standardTile('level_up', 'device.level', inactiveLabel: false, decoration: 'flat', canChangeIcon: false) {
            state 'default', action: 'levelUp', icon: 'st.illuminance.illuminance.bright'
        }
        standardTile('level_down', 'device.level', inactiveLabel: false, decoration: 'flat', canChangeIcon: false) {
            state 'default', action: 'levelDown', icon: 'st.illuminance.illuminance.light'
        }
	}
	main(["switch"])
    details(["switch", "level_up", "level_down", "level_slider_control", "level_value", "refresh" ])
}


// parse events into attributes
def parse(description) {
	log.debug "parse() - $description"
	def results = []
	
    if ( description == "updated" ) //on initial install we are returned just a string
    	return
      
    /*  
	if (description?.name && description?.value)
	{
		results << sendEvent(name: "${description?.name}", value: "${description?.value}")
	}
    */
}


// handle commands
def on() {
	parent.lightBulbOn(this)
}

def off() {
	parent.lightBulbOff(this)
}

def levelUp(){
    int nextLevel = device.currentValue("level") + 10.0
    
    if( nextLevel > 100){
    	nextLevel = 100
    }
    log.debug "Setting dimmer level up to: ${nextLevel}"
    setLevel(nextLevel)
}

def levelDown(){
    int nextLevel = device.currentValue("level") - 10.0
    
    if (nextLevel < 5){
    	nextLevel = 0
    }
    
    if (nextLevel == 0){
    	off()
    }
    else
    {
    	log.debug "Setting dimmer level down to: ${nextLevel}"
        setLevel(nextLevel)
    }
}

def setLevel(value) {
		float val = value / 100.0
        log.debug "Setting the level to decimal: ${val}"
        parent.lightBulbLevel(this, val)
}

def setLevel(value, duration) {
        log.debug "Firing the multi arg version"
//            def dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
//                zwave.switchMultilevelV2.switchMultilevelSet(value: value, dimmingDuration: dimmingDuration).format()
}

def uninstalled() {
	log.debug "Executing 'uninstall' in child"
    parent.uninstallChildDevice(this)
}

def poll() {
	log.debug "Executing 'poll'"
	parent.pollLightBulb(this)
}

def refresh() {
	log.debug "Executing 'refresh'"
	parent.pollLightBulb(this)
}