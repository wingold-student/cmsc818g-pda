# De-Stress Me PDA Aspect
## Stress Management PDA aspect for CMSC818G

## Requirements
* Java SDK 18
* Maven 1.8
* Python 3
  * sklearn
  * pandas
  * numpy

To quickly install the Python requirements you can create your own virtual environment and do `pip install -r ./requirements.txt` in the root directory.

Run with `mvn compile exec:exec` in the root directory.

To stop the program, simply hit any key in the terminal that is executing the program.

To view the pages go to "localhost:8080" which will serve a static HTML page. Or "localhost:8080/hello" for just a string message.

To integrate with the front-end, see [this github repo](https://github.com/HyemiSong/PDA-App/)
