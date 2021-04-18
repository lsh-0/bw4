#!/bin/bash
set -e
lein cljfmt fix
lein eastwood
