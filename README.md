# UniFi NVR for Hubitat

This integration will allow you to add your UniFi NVR controlled surveillance cameras to your Hubitat system.

Currently they only function as motion detectors but hopefully I'll be able to get the "snapshot" and "snapshot on motion" functions working soon

You'll need to following from your UniFi NVR:

	- Username (I suggest creating an account on the NVR just for this integration)
	- Password
	- API Key for that user 
	- IP Address of the NVR
	- NVR HTTP Port (Usually 7080)


1. Insert the file "unifi_camera_driver.groovy" into the "Drivers Code" area on your Hubitat.

2. Insert the file "unifi_app.groovy" into the "Apps Code" area on your Hubitat.

3. In the "Apps" section of your Hubitat choose "Load New App" and select "UniFi NVR".

4. Fill out the boxes in the App setup and click "Done".

5. Wait for your cameras to show up in "Devices".  (It can take a minute or 2)


You can now use your UniFi cameras as motion triggers in Rule Machine and Simple Lighting.

If you run into any issues, have any suggestions or would like to help out with the "snapshot" and "snapshot on motion" features PM me on the Hubitat forums.


