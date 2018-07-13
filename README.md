# virtual-har-server

Virtual Har Server *was* an HTTP proxy that serves responses from a HAR file. 
It was one of multiple engines that could be used by the [har-replay][har-replay]
library to serve responses. Now it is the only engine that can be used by that
library, and as such it is included as a module in that parent project. The
source code has been removed from the master branch here to avoid confusion.
Future development of the `virtual-har-server` code will happen within **har-replay**.
The legacy codebase is available under the `legacy` tag.

[har-replay]: https://github.com/mike10004/har-replay
