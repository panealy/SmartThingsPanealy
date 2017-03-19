/**
     *  Garage (or any) Outlet On/Off
     *
     *  Copyright 2016 Peter Nealy
     *
     *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
     *  in compliance with the License. You may obtain a copy of the License at:
     *
     *      http://www.apache.org/licenses/LICENSE-2.0
     *
     *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
     *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
     *  for the specific language governing permissions and limitations under the License.
     *
     */
    definition(
        name: "Garage Fan On/Off",
        namespace: "panealy",
        author: "Peter Nealy",
        description: "Turn garage devices on upon garage entry, and off after a period of a few hours. Primarily for garage fans.",
        category: "My Apps",
        iconUrl: "http://cdn.device-icons.smartthings.com/Appliances/appliances11-icn.png",
        iconX2Url: "http://cdn.device-icons.smartthings.com/Appliances/appliances11-icn@2x.png",
        iconX3Url: "http://cdn.device-icons.smartthings.com/Appliances/appliances11-icn@3x.png")
    
    preferences {
    	section("When these sensors are activated...") {
    		input "multiSensor", "capability.contactSensor", title: "Which?", required: true, multiple: true
    	}
        
        section("After waiting this many minutes (default 1 minute...") {
        	input "minutesToWait", "number", title: "How many?", required: false, defaultValue: 1
        }
        
        section("Then turn on these outlets...") {
        	input "outlets", "capability.outlet", title: "Which?", required: false, multiple: false
        }
        
        section("And turn on these switches...") {
        	input "switches", "capability.switch", title: "Which?", required: true, multiple: false
        }
        
        section("And then turn it off this many minutes (default 120 minutes...") {
        	input "minutesUntilOff", "number", title: "after the outlet turns off", required: false, defaultValue: 120
        }
        
        section("Disable runtime if switch or outlet is manually manipulated?") {
        	input "disableOnInteraction", "bool", required: false, defaultValue: false
        }
    }
    
    def installed() {
    	log.debug "Installed with settings: ${settings}"
    
    	initialize()
    }
    
    def updated() {
    	log.debug "Updated with settings: ${settings}"
    
    	unsubscribe()
    	initialize()
    }

def initialize() {
    state.deviceState = [:]
    /*multiSensor.each { device ->
    	log.debug device
        if (device.contact == "contact.open") log.debug "sensor ${device} indicates OPEN"
        if (device.contact == "contact.closed") log.debug "sensor ${device} indicates CLOSED"
 		subscribe(device, "contact.open", deviceOnHandler)
		subscribe(device, "contact.closed", deviceOffHandler)
	}*/
	initSensors()
    initOutlets()
    initSwitches()
}

def initSensors() {
	log.debug "Initializing sensors ${multiSensor}"
    multiSensor.each { device ->
    	def attr = device.currentState("contact")
    	log.debug "Subscribing device ${device}, currentState ${attr.value}"
        //state.deviceState[device].lastState = attr.value
 		subscribe(device, "contact.open", deviceOnHandler)
		subscribe(device, "contact.closed", deviceOffHandler)
	}
}

def initOutlets() {
    outlets.each { dni ->
    	def attr = dni.currentState("outlet")
        log.debug "initializing outlet ${dni} (currentState: ${attr.value})"
		state.deviceState[dni] = false
    }
}

def initSwitches() {
    switches.each { dni ->
    	def attr = dni.currentState("switch")
        log.debug "initializing switch ${dni} (currentState: ${attr.value})"
		state.deviceState[dni] = false
    }
}

def deviceOnHandler(evt) {
	log.debug "deviceOnHandler ${evt} | minutesToWait=${minutesToWait}"
    if(minutesToWait > 0) runIn(minutesToWait*60, devicesOn) else devicesOn()
}

def deviceOffHandler(evt) {
	log.debug "deviceOffHandler ${evt} | minutesUntilOff=${minutesUntilOff}"
    if(minutesUntilOff > 0) runIn(minutesUntilOff*60, devicesOff) else devicesOff()
}

def devicesOff() {
    outlets.each { dni ->
        log.debug "turning off outlet ${dni}"
		dni.off()
		state.deviceState[dni] = false
    }
    switches.each { dni ->
        log.debug "turning off switch ${dni}"
		dni.off()
		state.deviceState[dni] = false
    }
}

def devicesOn() {
    outlets.each { dni ->
        log.debug "turning on outlet ${dni}"
		dni.on()
		state.deviceState[dni] = true
    }
    switches.each { dni ->
        log.debug "turning on switch ${dni}"
		dni.on()
		state.deviceState[dni] = true
    }
}