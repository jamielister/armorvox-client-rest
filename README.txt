The "Armorvox-Client-Rest" project is open software designed as an exemplar and starting-point for partners and customers using Auraya's ArmorVox speaker recognition cloud service.

See https://cloud.armorvox.com/  for details.

It is a command-line tool that calls each of the APIs of the ArmorVox server.

Identities (IDs) and audio are expected to be organised as follows:

ID - can be numeric (or alphanumeric when using the WebProxy)
TYPE - indicates voice print type, numeric 1 to 8, 10 or 11.
id_list.txt is a file listing the IDs to enrol or verify. Each ID on a separate line with optional audio file locations following each ID
Audio file is a WAV file (PCM/MULAW/ALAW) contained in the ID folder, and is named ID-EV-TYPE-N.wav, where EV is 1 for enrol or 2 for verify. N is ordinal within EV.

For example, for TYPE = 7

path/to/id_list.txt
path/to/ID_A/ID_A-1-7-1.wav
path/to/ID_A/ID_A-1-7-2.wav
path/to/ID_A/ID_A-1-7-3.wav
path/to/ID_A/ID_A-2-7-1.wav
path/to/ID_B/ID_B-1-7-1.wav
path/to/ID_B/ID_B-1-7-2.wav
path/to/ID_B/ID_B-1-7-3.wav
path/to/ID_B/ID_B-2-7-1.wav

(Note that IDs are usually numeric).


To install and run this exemplar, you'll need to have:
* Maven
* Java 8

>mvn package

Once built, the executable jar file is in the target folder.

To get a list of all the options, just run the executable without any:
>java -jar armorvox-client.jar 

For a typical enrolment, from the generated 'target' folder, run:
>java -jar armorvox-client.jar -l ../example_data/enrol.txt -s https://cloud.armorvox.com/eval_server/vxml/v1 -a ae

For verification
>java -jar armorvox-client.jar -l ../example_data/verify.txt -s https://cloud.armorvox.com/eval_server/vxml/v1 -a av

