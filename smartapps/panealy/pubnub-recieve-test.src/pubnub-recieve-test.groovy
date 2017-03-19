/**
 *  pubnub recieve test
 *
 *  Copyright 2017 Peter Nealy
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
    name: "pubnub recieve test",
    namespace: "panealy",
    author: "Peter Nealy",
    description: "Tests recieving data from pubnub listener hosted in AWS Lambda",
    category: "SmartThings Labs",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    oauth: true)


preferences {
        section("Channel") {
        	input "namespace", "string", title: "enter pubnub namespace", required: true, defaultValue: "b4da8ed9739192268ba5667b4d10545b09e45d83|light_bulb-1702387|user-331666"
        }
}

mappings {
	path("/u") { action: [GET: "updateDevice"] }
    /*
	path("/m") { action: [GET: "processMacro"] }
	path("/h") { action: [GET: "processSmartHome"] }
	path("/l") { action: [GET: "processList"] }
	path("/b") { action: [GET: "processBegin"] }
	path("/u") { action: [GET: "getURLs"] }
    path("/f") { action: [GET: "processFollowup"] }
	path("/setup") { action: [GET: "setupData"] }
    path("/flash") { action: [GET: "flash"] }
    path("/cheat") { action: [GET: "cheat"] }
    */
}

/*
                else if (intentName == "DeviceOperation") {
                    var Operator = event.request.intent.slots.Operator.value;
                    var Device = event.request.intent.slots.Device.value;
                    var Num = event.request.intent.slots.Num.value;
                    var Param = event.request.intent.slots.Param.value;
                    url += 'd?Device=' + Device + '&Operator=' + Operator + '&Num=' + Num + '&Param=' + Param; 
                    process = true;
                    cardName = "SmartThings Devices";
                }
 */
def updateDevice() {  
	def dev = params.Device.toLowerCase() 	//Label of device
//	def op = params.Operator				//Operation to perform
//    def numVal = params.Num     			//Number for dimmer/PIN type settings
//    def param = params.Param				//Other parameter (color)
    //if (dev =~ /message|queue/) msgQueueReply(op) else processDeviceAction(dev, op, numVal, param, false)
    log.debug "${dev} ${params}"
    return ["access":"OK"]
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
	// TODO: subscribe to attributes, devices, locations, etc.
    OAuthToken()
    
    log.debug "AT: ${state.accessToken}"
    log.debug "STappID: ${app.id}"
    log.debug "url=https://graph.api.smartthings.com:443/api/smartapps/installations/${app.id}"
}

def OAuthToken(){
	try {
        createAccessToken()
		log.debug "Creating new Access Token"
	} catch (e) { log.error "Access Token not defined. OAuth may not be enabled. Go to the SmartApp IDE settings to enable OAuth." }
}

// TODO: implement event handlers