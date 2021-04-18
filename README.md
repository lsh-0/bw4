# Boardwalk (4)

Boardwalk is a tool for consuming, publishing and navigating structured data.

## model

`core.clj` handles application state and the logic of handling 'messages'.

There are two types of messages, a regular 'message' and a 'request'. 

When a 'message' is sent it doesn't expect a response, it is simply broadcast out to anyone listening.

When a 'request' is sent it has a means for recipients to reply to it.

## License

Copyright © 2020 - now [LSH-0](https://github.com/lsh-0)

Distributed under the GNU Affero General Public Licence, version 3 [with additional permissions](LICENCE.txt#L665)
