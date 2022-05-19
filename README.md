# Comp2207_Coursework
The CourseWork of Comp2207
it is a distrubute store system
## How to use it
The zip includes a bash script (validate_submission.sh) and a Java policy file (my_policy.policy). The script will (1) unzip your submission, (2) check all required files are included, (3) compile all the Java sources, (4) run Controller and Dstores.
The script is meant to be run in a UNIX terminal. Make sure both the script and the Java policy file are unzipped in the same directory. You may need to make the script executable using the following command
chmod +x validate_submission.sh
To run the script, type the following
./validate_submission.sh <yourname.zip> <wait_time>
The wait_time parameter defines how many secods to wait before the script terminates Controller and Dstore processes. This parameter is optional; if you don't specify it, the default value 5 will be used. You can also specify a negative value to indicate that the script must not terminate Controller and Dstore processes; this can be useful to let you use your client to test your software in a situation where Controller and Dstores are launched in the same way they will be launched for the assessment. Note that, in this case you should terminate the processes manually.
## How to use the provided client
compilation
make sure client.jar and ClientMain.java are in the current diretory
from terminal, type: javac -cp client.jar ClientMain.java
execution
on Linux - from terminal, type: java -cp client.jar:. ClientMain 12345 1000
on Windows - from terminal, type: java -cp client.jar;. ClientMain 12345 1000