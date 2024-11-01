# Using Your Mobile Phone as the Mouse to Control Your Server
In situations where a mouse is not available, you can use your smartphone to control the mouse cursor on a computer or laptop.

## Beetle
![Server_and_client](https://github.com/user-attachments/assets/cd7b1799-9913-4f11-9d11-a8cce81767d3)


* Server(PC): Mouse + Communication

	- Communication: The server receives real-time position data from the smartphone client via Bluetooth.

	- Mouse: The server processes the smartphone's position data and converts it into cursor positions on the monitor.

* Client(smartphone): AR core+ Communication

	- AR Core: The smartphone generates real-time position data.

	- Communication: The smartphone sends real-time position data to the server (PC) via Bluetooth.
 

## Beetle Server
* Role: The server converts the received t_x and t_y values into mouse cursor coordinates to move the cursor.
  
![smartphone_to_mousecursor](https://github.com/user-attachments/assets/1a7ca632-149a-4e8e-9b75-ed8799c016ee)


Change in the smartphone's x and y coordinates = Change in Mouse x and y coordinates ÷ 65000.

65000 is an example; it can be adjusted according to the desired sensitivity of the user.

## Beetle Android App
![BeetleApp](https://github.com/user-attachments/assets/54df2543-126c-4f65-8fda-996cd7bef4be)


* Connect Button: Select the server device to connect to.
* Start Button: Begin transmitting the smartphone's coordinate values (t_x, t_y).
* Stop Button: Stop transmitting the smartphone's coordinate values (t_x, t_y).

## Note
The BeetleServer file should be run in Eclipse, while the BeetleClient file should be executed in Android Studio.
