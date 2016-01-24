  
/**
 *  Smart Light
 *
 *  Copyright 2015 Vikash Varma
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
 *  Use Cases:
 *    1. Security - Turn on lights when its dark. Turn off when there is light.
 *    2. Sensor - Turn on / off based on sensors
 *    3. SecurityOptimized - if I am home, turn on/off based on sensor. if i am away, turn on when its drak and turn off when there is light.
 *
 */
 
 /*
during sunset motion was on, however light did not turn on since motion event did not fire. Fix: created schedule job to call motion handler at set time
 */
definition(
    name: "Light Automation",
    namespace: "vvarma",
    author: "Vikash Varma",
    description: "Light and switch automation using home mode, motion, contact, and lock sensors.",
    category: "Safety & Security",
    iconUrl: "http://ecx.images-amazon.com/images/I/31zw0IKIkbL.jpg",
    iconX2Url: "http://ecx.images-amazon.com/images/I/31zw0IKIkbL._AA160_.jpg"
)

preferences {
	   page(name: "mainPage", nextPage:"pageTwo", title: "Control lights and switches",  uninstall: true)
       page(name: "pageTwo", nextPage:"pageThree", title: "Settings",  uninstall: true)
       page(name: "pageThree", title: "Name and Mode settings",  uninstall: true, install: true) {
            section([mobileOnly:true]) {
                label title: "Assign a name", required: false
                mode title: "Set for specific mode(s)", required: false
            } 
       }
}
def pageTwo() {
	dynamicPage(name: "pageTwo") {
		if (drakPref =="Light Sensor" ) {
			section("Which Light Sensor"){
				input "lightSensor", "capability.illuminanceMeasurement", required: false
        	}
        	section ("Light sensor settings") {
        		input "lightLevel", "number",  title:"Turn on when illuminance is less than (lux)" , required: false
            }
        } else {
            section ("Sunrise offset (optional)...") {
                input "sunriseOffsetValue", "number", title: "Offset Minutes", required: false, description:"0"
                input "sunriseOffsetDir", "enum", title: "Before or After", required: false, options: ["Before","After"]
            }
            section ("Sunset offset (optional)...") {
                input "sunsetOffsetValue", "number", title: "Offset Minutes", required: false, description:"0"
                input "sunsetOffsetDir", "enum", title: "Before or After", required: false, options: ["Before","After"]
            }
        }
    }
}

def mainPage() {
	dynamicPage (name: "mainPage" ) {
        section("Which lights..."){
            input "lights", "capability.switch", multiple: true
        }
        section ("Preferences") {
        	input ("drakPref", "enum", title:"Use light sensor or sunset/sunrise", metadata: [values: ["Light Sensor", "Sunset/Sunrise"]],multiple:false,submitOnChange:true)
        	input ("pref", "enum", title: "Dawn to dusk light or Control using sensors", metadata: [values: ["Dawn to Dusk", "Sensor" ]], multiple:false,submitOnChange: true) 
            input "pushNotify", "boolean" , title:"Send notification", default: true
		}
        if ( pref == "Sensor" || pref == "SecurityOptimized" ) {
        	section ("Sensors to control lights and switches..") {
                    input "motionSensors", "capability.motionSensor", title:"Motion sensors?", multiple: true, required: false
                    input "doorSensors", "capability.contactSensor", title: "Door sensors", multiple:true, required: false
                    input "lock1", "capability.lock", title:"Which Lock?", required: false
                    input "delayMinutes", "number", title: "Turn off after (Minutes)?", description:"5"
            }
        }       
        /*
        if (pref == "SecurityOptimized" ) {
        	section ("Away modes..") {
        		input "onModes", "mode", title: "Away Modes", required: true, multiple: true
            }
        } */ 
       
    }
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	unschedule()
	initialize()
}

def initialize() {
    subscribe(location, "position", locationPositionChange)       
    switch (pref) {
        case "Sensor":
            subscribe(motionSensors, "motion", motionHandler)
            subscribe(doorSensors, "contact.open", sensorOpenHandler)
            subscribe(doorSensors, "contact.closed", sensorCloseHandler)
            subscribe(lock1, "lock.unlocked", sensorOpenHandler)
            subscribe(lock1, "lock.locked", sensorCloseHandler)
            break
        case "Security" :
            if (lightSensor) {
                subscribe(lightSensor, "illuminance", illuminanceHandler, [filterEvents: false])
            } 
        break        
    }
    state.motionActive = false
    state.lastStatus = "ini"
    state.reason = "initialize"
    if (isDark() && checkMotion()) {
        trunLightsOn()  
    } else {
        trunLightsOff()
    }
    astroCheck()
    schedule ("0 0 3 * * ?" , astroCheck) //once a day at 3 AM 
}

def locationPositionChange(evt) {
	log.trace "locationChange()"
	astroCheck()
}

def checkMotion() {
	state.motionActive = false       
	for(sensor in motionSensors) {
            if( sensor.currentValue('motion') == "active") {
                state.motionActive = true
            }
    } 
    return state.motionActive
}
def motionHandler(evt) {
	if (isDark()) {	
    	checkMotion()
        if (state.motionActive) {
                sensorOpenHandler(evt)
        } else {
                sensorCloseHandler(evt) 
        }
    }
}

def sensorOpenHandler(evt) {
	 if ( isDark() ) {
     	state.reason = "$evt.displayName Motion=$state.motionActive $evt.name=$evt.value; isDark=$state.isDark"	
    	unschedule
		trunLightsOn()
     }
}

def sensorCloseHandler(evt) {
	state.reason = "$evt.displayName Motion=$state.motionActive $evt.name=$evt.value; isDark=$state.isDark"
    log.info "sensorCloseHandler: scheduling trunLightsOff since $state.reason"
    runIn(delayMinutes*60, trunLightsOff)
}

def sendMsg(msg) {
	log.info msg
    if ( pushNotify) {
   		sendNotificationEvent("$app.label $msg");
    }
   	//sendEvent(name:"Light", value:"trunLightsOn", descriptionText: msg, displayed:true)
}

def trunLightsOn() {
	def msg
	if (state.lastStatus != "on") {
		msg = "trunLightsOn: Turning $lights on. Event=$state.reason"
        sendMsg(msg)
 		lights.on()	
        lights.setLevel(100)
        state.lastStatus = "on"
	} else {
    	log.info "trunLightsOn: $lights already on. Event=$state.reason"
     	//sendMsg(msg)
    }
}

def trunLightsOff() {
	def msg
	if (state.lastStatus != "off") {	
    	if (state.motionActive == false  ) {
			state.lastStatus = "off"
        	lights.off()
        	msg = "trunLightsOff: Turning $lights off. Event=$state.reason"
        	sendMsg(msg)
   		} 
    } else {
        log. info "trunLightsOff: $lights already off. Event=$state.reason"
        //sendMsg(msg,false)
   }
}

/*
* TODO: Rewrite
*/

def illuminanceHandler(evt) {
	log.debug "$evt.name: $evt.value, lastStatus: $state.lastStatus, motionStopTime: $state.motionStopTime"
	if (state.lastStatus != "off" ) {
		lights.off()
		state.lastStatus = "off"
	}
	else if (state.motionStopTime) {
		if (lastStatus != "off") {
			def elapsed = now() - state.motionStopTime
			if (elapsed >= (delayMinutes ?: 0) * 60000L) {
				turnLightsOff
			}
		}
	}
	else if (lastStatus != "on" && evt.value < 30){
		lights.on()
		state.lastStatus = "on"
	}
}


def astroCheck() {
	def s = getSunriseAndSunset(sunriseOffset: sunriseOffset, sunsetOffset: sunsetOffset)
	state.riseTime = s.sunrise.time
	state.setTime = s.sunset.time
	log.debug "rise: ${new Date(state.riseTime)}($state.riseTime), set: ${new Date(state.setTime)}($state.setTime)"
    state.reason = "astroCheck"
    if (pref == "Security") {
    	unschedule
        schedule(state.setTime,trunLightsOn);
    } else {
    	 schedule(state.setTime,motionHandler);
    }
    schedule(state.riseTime,trunLightsOff);
}

private isDark() {
	if (lightSensor) {
		state.isDark = lightSensor.currentIlluminance < lightLevel
	}
	else {
		def t = now()
		state.isDark = t < state.riseTime || t > state.setTime
	}    
   // log.debug "isDark: $result now: rise: ${new Date(t)} | ${new Date(state.riseTime)} | set: ${new Date(state.setTime)}"
   // log.debug "isDark: $result"
}


private getSunriseOffset() {
	sunriseOffsetValue ? (sunriseOffsetDir == "Before" ? "-$sunriseOffsetValue" : sunriseOffsetValue) : null
}

private getSunsetOffset() {
	sunsetOffsetValue ? (sunsetOffsetDir == "Before" ? "-$sunsetOffsetValue" : sunsetOffsetValue) : null
}

/*
def modeHandler(evt) {
    state.homeMode = evt.value 
    state.reason = "home mode changed to $state.homeMode"
    unschedule
  
    if (onModes && onModes.contains (state.homeMode) ) {    	
        schedule(state.riseTime,trunLightsOff);
		schedule(state.setTime,trunLightsOn);
       // sendPush("secheduling $lights off at $state.riseTime and on at $state.setTime" );
       if (enabled()) {
        	trunLightsOn()  
       } else {
       		trunLightsOff()  
       }
    } else {
        trunLightsOff()
    }
}


private isAway() {
	if (onModes.contains (state.homeMode) ) {
    	return true
    } else {
    	return false
    }
}

 */