# Boardwalk (4)

Boardwalk is a tool for consuming, publishing and navigating structured data.

## model

Data exists in a transient state in the UI until committed to the database whereupon it gets wrapped in a map and gains 
an `id` if wasn't already a map and didn't already have an `id`.

The UI allows input of arbitrary data but also provides *services* to slurp data from other sources, such as Github, or transform existing data, or provide basic utilities, like storing data or scheduling tasks to be run or metadata about other services.

Services are simple functions that listen to specific *topics* and accept *messages* addressed to those topics. 

A message is just some metadata wrapping arbitrary data that makes sense to the service receiving it. It can be anything.

When a message is sent it typically doesn't expect a response, it is simply broadcast on the given *topic* to any service listening.

A *request* is a type of message that returns a `future` that can be derefed (and will block) until results from the service call are available.

Multiple services can listen to a single topic. For example, a topic called "news" may exist and specialised per-site services may listen to requests sent to the "news" topic and return results.

## License

Copyright © 2020 - now [LSH-0](https://github.com/lsh-0)

Distributed under the GNU Affero General Public Licence, version 3 [with additional permissions](LICENCE.txt#L665)
