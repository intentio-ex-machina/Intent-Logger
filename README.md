Intent Logger
=============

Intent Logger is a user firewall (UFW) designed to be ran on an IEM-enabled device to log intents
to a file for analysis. Files are saved to external storage in the AICS folder.

Implementation Status
---------------------

Done
----

* Timestamp
* Receiver Component
* UserID
* Request Code
* Start Flags
* Required Permission
* Service Flags
* Service Action
* Intent Action
* Intent Data
* Intent Flags
* Intent Type

In Progress
-----------

* Caller Component (package only, activity & service only)

Missing
-------

* Millisecond Offset
* Caller UID
* Caller PID
* Receiver UID
* Receiver PID
* Options
* Broadcast Flags
* Intent Category
* Intent ClipData
* Intent Extras


License
-------

Copyright 2016 Carter Yagemann <carter.yagemann@gmail.com>.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.